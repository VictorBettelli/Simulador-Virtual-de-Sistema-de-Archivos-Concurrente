package view;

import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import model.FileSystemNode;
import model.Disk;
import model.Process;
import model.Scheduler;
import model.OperacionesArchivo;
import model.LockManager;
import model.ProcesoHilo;
import model.RWLock;
import model.Terminable;
import model.LogListener;
import util.LinkedList;
import util.Node;
import config.UserSession;
import java.io.File;
import java.io.IOException;
import test.TestCase;
import test.TestCaseLoader;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import model.JournalEntry;
import persistence.FileSystemPersistence;
import model.JournalManager;

public class FileSystemGUI extends JFrame implements Terminable, LogListener {
    // ===== PALETA DE COLORES =====
    private final Color COLOR_BACKGROUND = new Color(235,240,250);
    private final Color COLOR_PANEL = new Color(245,248,255);
    private final Color COLOR_HEADER = new Color(210,220,240);
    private final Color COLOR_SELECTION = new Color(180,205,255);

    private JTree fileTree;
    private DefaultTreeModel treeModel;
    private JTextArea infoArea;
    private FileSystemNode root;
    private Disk disk;
    private JPanel diskPanel;
    private JournalManager journalManager;
    
    // Atributos para planificación
    private Scheduler scheduler;
    private OperacionesArchivo operaciones;
    private LockManager lockManager;
    private LinkedList<ProcesoHilo> procesosActivos;
    private JComboBox<String> politicaCombo;
    private JLabel cabezaLabel;
    private JTextArea logArea;
    private int desplazamientoTotal;
    private JLabel desplazamientoLabel;
    private JSpinner cabezaSpinner;
    private JButton btnSetCabeza;
    
    // Atributos para modo usuario
    private UserSession userSession;           
    private JComboBox<String> userModeCombo;   
    private JComboBox<String> userSelectorCombo;
    private JLabel currentModeLabel;
    
    // Para mostrar locks
    private DefaultListModel<String> locksModel;
    private JList<String> locksList;
    
    // Timer para actualización automática
    private Timer updateTimer;
    
    // Controles de modo manual
    private JCheckBox chkModoManual;
    private JButton btnPaso;
    
    // --- Colas de procesos por estado ---
    private DefaultListModel<Process> listosModel;
    private JList<Process> listosList;
    private DefaultListModel<Process> cpuModel;
    private JList<Process> cpuList;
    private DefaultListModel<Process> bloqueadosModel;
    private JList<Process> bloqueadosList;
    
    // Solicitudes pendientes (cola de disco)
    private DefaultListModel<String> solicitudesModel;
    private JList<String> solicitudesList;
    
    // --- Tabla de asignación de archivos ---
    private JTable tablaAsignacion;
    private DefaultTableModel tablaModel;
    
    // --- Tabla de journaling ---
    private DefaultTableModel journalModel;
    private JTable journalTable;
    
    public FileSystemGUI() {
        userSession = new UserSession();      
        disk = new Disk();
        desplazamientoTotal = 0;
        journalManager = new JournalManager();
        initComponents();
        initFileSystem();
        verificarJournalPendiente();
        
        // Crear operaciones y scheduler después de tener root
        operaciones = new OperacionesArchivo(disk, root,journalManager);
        scheduler = new Scheduler(disk, operaciones, this);
        scheduler.start(); // Inicia el hilo del scheduler
        
        lockManager = new LockManager();
        procesosActivos = new LinkedList<>();
        
        refreshTree();
        updateDiskView();
        actualizarVistaProcesos();
        actualizarTablaAsignacion(); // Mostrar archivos iniciales
        actualizarPermisosInterfaz();
        actualizarVistaJournal();
        
        // Timer para actualizar la GUI cada 500 ms
        updateTimer = new Timer(500, e -> actualizarVistaProcesos());
        updateTimer.start();
    }

    private void initComponents() {
        setTitle("Simulador de Sistema de Archivos");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ===== PANEL SUPERIOR - SELECTOR DE MODO =====
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createEtchedBorder());

        topPanel.add(new JLabel("Modo:"));
        userModeCombo = new JComboBox<>(new String[]{"Administrador", "Usuario"});
        userModeCombo.addActionListener(e -> cambiarModo());
        topPanel.add(userModeCombo);

        topPanel.add(new JLabel("  Usuario:"));
        userSelectorCombo = new JComboBox<>();
        for (String user : UserSession.USERS) {
            userSelectorCombo.addItem(user);
        }
        userSelectorCombo.addActionListener(e -> cambiarUsuario());
        userSelectorCombo.setEnabled(false);
        topPanel.add(userSelectorCombo);

        currentModeLabel = new JLabel("  " + userSession.getModeDisplay());
        topPanel.add(currentModeLabel);

        add(topPanel, BorderLayout.NORTH);

        // Panel izquierdo con el árbol
        JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.setBorder(BorderFactory.createTitledBorder("Estructura de Archivos"));
        treePanel.setPreferredSize(new Dimension(300, 500));

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Raíz (/)");
        treeModel = new DefaultTreeModel(rootNode);
        fileTree = new JTree(treeModel);
        fileTree.addTreeSelectionListener(e -> showFileInfo());

        // Renderizador para diferenciar directorios y archivos
        fileTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                if (value instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                    Object userObj = node.getUserObject();
                    if (userObj instanceof FileSystemNode) {
                        FileSystemNode fsNode = (FileSystemNode) userObj;
                        if (fsNode.isDirectory()) {
                            setText("📁 " + fsNode.getName());
                        } else {
                            setText("📄 " + fsNode.getName());
                        }
                    }
                }
                return this;
            }
        });

        JScrollPane treeScroll = new JScrollPane(fileTree);
        treePanel.add(treeScroll, BorderLayout.CENTER);

        // ===== PANEL DERECHO MEJORADO =====
        // --- Tabla de asignación (estilo moderno) ---
        tablaModel = new DefaultTableModel(new String[]{"Nombre", "Bloques", "Primer Bloque", "Color"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaAsignacion = new JTable(tablaModel);
        tablaAsignacion.setRowHeight(22);
        tablaAsignacion.setFillsViewportHeight(true);
        tablaAsignacion.setShowGrid(false);
        tablaAsignacion.setIntercellSpacing(new Dimension(0, 0));
        tablaAsignacion.setSelectionBackground(COLOR_SELECTION);
        tablaAsignacion.setSelectionForeground(Color.BLACK);

        tablaAsignacion.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tablaAsignacion.getTableHeader().setBackground(COLOR_HEADER);

        // Renderizador para mostrar el color real en la columna "Color"
        tablaAsignacion.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {

                JLabel label = new JLabel();
                label.setOpaque(true);

                String nombre = (String) tablaModel.getValueAt(row, 0);
                FileSystemNode nodo = buscarArchivo(root, nombre);

                if (nodo != null && nodo.getColor() != null) {
                    label.setBackground(nodo.getColor());
                } else {
                    label.setBackground(Color.WHITE);
                }

                label.setBorder(BorderFactory.createLineBorder(Color.BLACK));

                return label;
            }
        });

        JScrollPane scrollTabla = new JScrollPane(tablaAsignacion);
        scrollTabla.setBorder(BorderFactory.createTitledBorder("Tabla de asignación"));

        // --- Panel de información del elemento (con scroll) ---
        infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JScrollPane scrollInfo = new JScrollPane(infoArea);

        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBackground(COLOR_PANEL);
        infoPanel.setBorder(BorderFactory.createTitledBorder("Información del elemento"));
        infoPanel.add(scrollInfo, BorderLayout.CENTER);

        // --- Panel del disco (matriz) ---
        diskPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawDisk(g);
            }
        };
        diskPanel.setBackground(new Color(250, 250, 255));
        diskPanel.setPreferredSize(new Dimension(560, 560)); // <-- LÍNEA AÑADIDA
        JScrollPane scrollDisco = new JScrollPane(diskPanel);
        scrollDisco.setBorder(BorderFactory.createTitledBorder("Disco (bloques)"));

        // --- Tabla de journaling (estilo moderno) ---
        journalModel = new DefaultTableModel(new String[]{"Operación", "Archivo", "Bloque", "Estado"}, 0);
        journalTable = new JTable(journalModel);
        journalTable.setRowHeight(22);
        journalTable.setShowGrid(false);
        journalTable.setIntercellSpacing(new Dimension(0, 0));
        journalTable.setSelectionBackground(COLOR_SELECTION);
        journalTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        journalTable.getTableHeader().setBackground(COLOR_HEADER);
        journalTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        // ===== AJUSTAR ANCHO DE COLUMNAS =====
        journalTable.getColumnModel().getColumn(0).setPreferredWidth(80);  // Operación
        journalTable.getColumnModel().getColumn(1).setPreferredWidth(200); // Archivo (más ancho)
        journalTable.getColumnModel().getColumn(2).setPreferredWidth(60);  // Bloque
        journalTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Estado
        JScrollPane scrollJournal = new JScrollPane(journalTable);
        scrollJournal.setBorder(BorderFactory.createTitledBorder("Journaling"));

        // ===== LAYOUT 2x2 =====
        // Fila superior: tabla de asignación + info
        JSplitPane topSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                scrollTabla,
                infoPanel
        );
        topSplit.setResizeWeight(0.6); // 60% para la tabla

        // Fila inferior: disco + journal
        JSplitPane bottomSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                scrollDisco,
                scrollJournal
        );
        bottomSplit.setResizeWeight(0.75); // 75% para el disco

        // Panel derecho completo
        JSplitPane rightPanel = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                topSplit,
                bottomSplit
        );
        rightPanel.setResizeWeight(0.35); // 35% para la parte superior

        // Split principal con el árbol
        JSplitPane mainSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                treePanel,
                rightPanel
        );
        mainSplit.setDividerLocation(280);
        add(mainSplit, BorderLayout.CENTER);

        // Panel de control (acciones)
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Acciones"));

        JButton btnCreateDir = new JButton("Crear Directorio");
        JButton btnCreateFile = new JButton("Crear Archivo");
        JButton btnUpdate = new JButton("Modificar Nombre");
        JButton btnDelete = new JButton("Eliminar");
        JButton btnRefresh = new JButton("Refrescar");
        JButton btnSave = new JButton("💾 Guardar");      
        JButton btnLoad = new JButton("📂 Cargar"); 
        JButton btnLoadTestCase = new JButton("📋 Cargar Caso Prueba");
        JButton btnSimularFallo = new JButton("💥 Simular Fallo");
        JButton btnLimpiarDisco = new JButton("🧹 Limpiar Disco");
        JButton btnLimpiarJournal = new JButton("📄 Limpiar Journal");
        JButton btnVerJournal = new JButton("📂 Ver Journal.txt");
        
        btnCreateDir.addActionListener(e -> solicitarCrearDirectorio());
        btnCreateFile.addActionListener(e -> solicitarCrearArchivo());
        btnUpdate.addActionListener(e -> solicitarModificarNombre());
        btnDelete.addActionListener(e -> solicitarEliminar());
        btnRefresh.addActionListener(e -> refreshTree());
        btnSave.addActionListener(e -> guardarEstado());   
        btnLoad.addActionListener(e -> cargarEstado());
        btnLoadTestCase.addActionListener(e -> cargarCasoPrueba());
        btnSimularFallo.addActionListener(e -> simularFallo());
        btnLimpiarDisco.addActionListener(e -> limpiarDiscoCompleto());
        btnLimpiarJournal.addActionListener(e -> limpiarJournal());
        btnVerJournal.addActionListener(e -> abrirArchivoJournal());

        controlPanel.add(btnCreateDir);
        controlPanel.add(btnCreateFile);
        controlPanel.add(btnUpdate);
        controlPanel.add(btnDelete);
        controlPanel.add(btnRefresh);
        controlPanel.add(btnSave);   
        controlPanel.add(btnLoad);
        controlPanel.add(btnLoadTestCase);
        controlPanel.add(btnSimularFallo);
        controlPanel.add(btnLimpiarDisco);
        controlPanel.add(btnLimpiarJournal);
        controlPanel.add(btnVerJournal);

        // ===== PANEL DE PLANIFICACIÓN Y COLAS =====
        JPanel schedulerPanel = new JPanel(new BorderLayout());
        schedulerPanel.setBorder(BorderFactory.createTitledBorder("Planificación de Disco"));

        // Panel superior del scheduler
        JPanel topSchedulerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topSchedulerPanel.add(new JLabel("Política:"));
        politicaCombo = new JComboBox<>(new String[]{"FIFO", "SSTF", "SCAN", "C-SCAN"});
        politicaCombo.addActionListener(e -> {
            int politica = politicaCombo.getSelectedIndex();
            scheduler.setPolitica(politica);
            actualizarVistaProcesos();
            System.out.println("Política cambiada manualmente a: " + politica);
        });
        topSchedulerPanel.add(politicaCombo);

        cabezaLabel = new JLabel("Cabezal: " + (scheduler != null ? scheduler.getCabezaActual() : 0));
        topSchedulerPanel.add(cabezaLabel);

        topSchedulerPanel.add(new JLabel("  Pos. inicial:"));
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(0, 0, Disk.SIZE - 1, 1);
        cabezaSpinner = new JSpinner(spinnerModel);
        cabezaSpinner.setPreferredSize(new Dimension(60, 25));
        topSchedulerPanel.add(cabezaSpinner);

        btnSetCabeza = new JButton("Establecer");
        btnSetCabeza.addActionListener(e -> {
            int nuevaPos = (int) cabezaSpinner.getValue();
            if (scheduler != null) {
                scheduler.setCabezaActual(nuevaPos);
                cabezaLabel.setText("Cabezal: " + scheduler.getCabezaActual());
                desplazamientoTotal = 0;
                actualizarDesplazamiento();
                actualizarVistaProcesos();
            }
        });
        topSchedulerPanel.add(btnSetCabeza);

        desplazamientoLabel = new JLabel("  Desplazamiento total: 0");
        topSchedulerPanel.add(desplazamientoLabel);

        // Controles de modo manual
        chkModoManual = new JCheckBox("Modo manual (paso a paso)");
        chkModoManual.addActionListener(e -> {
            if (scheduler != null) {
                scheduler.setModoManual(chkModoManual.isSelected());
                btnPaso.setEnabled(chkModoManual.isSelected());
            }
        });
        topSchedulerPanel.add(chkModoManual);

        btnPaso = new JButton("Ejecutar siguiente");
        btnPaso.setEnabled(false);
        btnPaso.addActionListener(e -> {
            if (scheduler != null) {
                scheduler.permitirSiguiente();
            }
        });
        topSchedulerPanel.add(btnPaso);

        // Panel de solicitudes pendientes (cola de disco)
        solicitudesModel = new DefaultListModel<>();
        solicitudesList = new JList<>(solicitudesModel);
        JScrollPane scrollSolicitudes = new JScrollPane(solicitudesList);
        scrollSolicitudes.setBorder(BorderFactory.createTitledBorder("Solicitudes pendientes (cola de disco)"));
        scrollSolicitudes.setPreferredSize(new Dimension(400, 80));

        // --- Colas de procesos por estado ---
        listosModel = new DefaultListModel<>();
        listosList = new JList<>(listosModel);
        listosList.setCellRenderer(crearProcesoCellRenderer());
        JScrollPane scrollListos = new JScrollPane(listosList);
        scrollListos.setBorder(BorderFactory.createTitledBorder("LISTOS"));

        cpuModel = new DefaultListModel<>();
        cpuList = new JList<>(cpuModel);
        cpuList.setCellRenderer(crearProcesoCellRenderer());
        JScrollPane scrollCPU = new JScrollPane(cpuList);
        scrollCPU.setBorder(BorderFactory.createTitledBorder("EN CPU"));

        bloqueadosModel = new DefaultListModel<>();
        bloqueadosList = new JList<>(bloqueadosModel);
        bloqueadosList.setCellRenderer(crearProcesoCellRenderer());
        JScrollPane scrollBloqueados = new JScrollPane(bloqueadosList);
        scrollBloqueados.setBorder(BorderFactory.createTitledBorder("BLOQUEADOS"));

        // Panel que agrupa las tres colas
        JPanel colasPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        colasPanel.add(scrollListos);
        colasPanel.add(scrollCPU);
        colasPanel.add(scrollBloqueados);
        colasPanel.setPreferredSize(new Dimension(400, 120));

        // Área de log
        logArea = new JTextArea(8, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scrollLog = new JScrollPane(logArea);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Log de movimientos"));

        // Panel de locks (opcional)
        locksModel = new DefaultListModel<>();
        locksList = new JList<>(locksModel);
        JScrollPane scrollLocks = new JScrollPane(locksList);
        scrollLocks.setBorder(BorderFactory.createTitledBorder("Locks activos"));
        scrollLocks.setPreferredSize(new Dimension(400, 80));

        // Panel central con BoxLayout vertical
        JPanel centerSchedulerPanel = new JPanel();
        centerSchedulerPanel.setLayout(new BoxLayout(centerSchedulerPanel, BoxLayout.Y_AXIS));
        centerSchedulerPanel.add(scrollSolicitudes);
        centerSchedulerPanel.add(colasPanel);
        centerSchedulerPanel.add(scrollLog);
        centerSchedulerPanel.add(scrollLocks);

        schedulerPanel.add(topSchedulerPanel, BorderLayout.NORTH);
        schedulerPanel.add(centerSchedulerPanel, BorderLayout.CENTER);

        // Panel sur completo
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(controlPanel, BorderLayout.NORTH);
        southPanel.add(schedulerPanel, BorderLayout.CENTER);

        add(southPanel, BorderLayout.SOUTH);

        setSize(1500, 900);
        setLocationRelativeTo(null);
    }

    // Renderizador común para listas de procesos
    private DefaultListCellRenderer crearProcesoCellRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                panel.setOpaque(true);
                if (value instanceof Process) {
                    Process p = (Process) value;
                    FileSystemNode archivo = p.getArchivo();

                    // Círculo de color
                    JLabel colorLabel = new JLabel();
                    colorLabel.setOpaque(true);
                    colorLabel.setPreferredSize(new Dimension(12, 12));
                    if (archivo != null && !archivo.isDirectory() && archivo.getColor() != null) {
                        colorLabel.setBackground(archivo.getColor());
                    } else {
                        colorLabel.setBackground(Color.GRAY);
                    }
                    colorLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
                    panel.add(colorLabel);

                    // Texto sin el estado
                    String tipo = (archivo != null && archivo.isDirectory()) ? "DIR" : "FILE";
                    String nombre = (archivo != null) ? archivo.getName() : p.getNombreArchivo();
                    String texto = "P" + p.getId() + " [" + p.getOperacion() + " " + tipo + "] " + nombre;
                    if (p.getOperacion().equals("UPDATE") && p.getNuevoNombre() != null) {
                        texto += " → " + p.getNuevoNombre();
                    }
                    JLabel textLabel = new JLabel(texto);
                    panel.add(textLabel);

                    if (isSelected) {
                        panel.setBackground(list.getSelectionBackground());
                        textLabel.setForeground(list.getSelectionForeground());
                    } else {
                        panel.setBackground(list.getBackground());
                        textLabel.setForeground(list.getForeground());
                    }
                    return panel;
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        };
    }

    // ===== MÉTODOS PARA CAMBIO DE MODO =====
    private void cambiarModo() {
        boolean isAdmin = userModeCombo.getSelectedIndex() == 0;
        userSelectorCombo.setEnabled(!isAdmin);
        
        if (isAdmin) {
            userSession.setUser("admin", true);
        } else {
            String selectedUser = (String) userSelectorCombo.getSelectedItem();
            userSession.setUser(selectedUser, false);
        }
        
        currentModeLabel.setText("  " + userSession.getModeDisplay());
        actualizarPermisosInterfaz();
        refreshTree();
        
        System.out.println("Modo cambiado a: " + userSession.getModeDisplay());
    }
    
    private void cambiarUsuario() {
        if (userModeCombo.getSelectedIndex() == 1) {
            String selectedUser = (String) userSelectorCombo.getSelectedItem();
            userSession.setUser(selectedUser, false);
            currentModeLabel.setText("  " + userSession.getModeDisplay());
            actualizarPermisosInterfaz();
            refreshTree();
            
            System.out.println("Usuario cambiado a: " + selectedUser);
        }
    }
    
    private void actualizarPermisosInterfaz() {
        boolean isAdmin = userSession.isAdmin();

        if (getContentPane().getComponentCount() > 2) {
            Component south = getContentPane().getComponent(2);
            if (south instanceof JPanel) {
                Component[] components = ((JPanel) south).getComponents();
                for (Component comp : components) {
                    if (comp instanceof JPanel) {
                        if (((JPanel) comp).getBorder() != null && 
                            ((JPanel) comp).getBorder().toString().contains("Acciones")) {

                            Component[] actionButtons = ((JPanel) comp).getComponents();
                            for (Component btn : actionButtons) {
                                if (btn instanceof JButton) {
                                    JButton button = (JButton) btn;
                                    String text = button.getText();

                                    if (text.equals("Crear Directorio") || 
                                        text.equals("Crear Archivo") || 
                                        text.equals("Modificar Nombre") ||
                                        text.equals("Eliminar")) {
                                        button.setEnabled(isAdmin);
                                    }
                                    if (text.equals("Refrescar")) {
                                        button.setEnabled(true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (btnSetCabeza != null) btnSetCabeza.setEnabled(true);
        if (politicaCombo != null) politicaCombo.setEnabled(true);
        if (cabezaSpinner != null) cabezaSpinner.setEnabled(true);
    }

    private void guardarEstado() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "¿Guardar el estado actual del sistema?\n" +
            "Se guardarán: estructura de archivos, disco y procesos",
            "Guardar estado", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            LinkedList<Process> procesosParaGuardar = new LinkedList<>();
            synchronized (procesosActivos) {
                Node<ProcesoHilo> current = procesosActivos.getHead();
                while (current != null) {
                    procesosParaGuardar.add(current.data.getDatosProceso());
                    current = current.next;
                }
            }
            FileSystemPersistence.guardarTodo(root, disk, procesosParaGuardar);
            JOptionPane.showMessageDialog(this, 
                "Estado guardado exitosamente en /data", 
                "Éxito", JOptionPane.INFORMATION_MESSAGE);
            actualizarVistaJournal();
        }
    }
    
    private void cargarEstado() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "¿Cargar estado guardado?\n" +
            "Se perderán los cambios no guardados.",
            "Cargar estado", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            // NO llamar a reiniciarSistema() aquí porque crea un disco nuevo

            // Mejor: detener scheduler pero mantener disk y root
            if (scheduler != null) {
                scheduler.detener();
                try {
                    scheduler.join(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Limpiar estructuras actuales pero no crear nuevas
            root = new FileSystemNode(); // Esto sí, porque vamos a cargar uno nuevo
            root.setName("/");
            root.setOwner("admin");
            root.setDirectory(true);
            root.setParent(null);
            root.setChildren(new LinkedList<>());

            disk = new Disk(); // Esto también, porque cargaremos el disco del JSON

            // Cargar datos
            LinkedList<Process> procesosCargados = new LinkedList<>();
            FileSystemPersistence.cargarTodo(root, disk, procesosCargados);

            // Recrear scheduler con los nuevos disk y root
            operaciones = new OperacionesArchivo(disk, root, journalManager);
            scheduler = new Scheduler(disk, operaciones, this);
            int politicaSeleccionada = politicaCombo.getSelectedIndex();
            scheduler.setPolitica(politicaSeleccionada);
            scheduler.setModoManual(chkModoManual.isSelected());
            scheduler.start();

            procesosActivos = new LinkedList<>();
            lockManager = new LockManager();

            // Crear procesos a partir de los cargados
            Node<Process> currentProc = procesosCargados.getHead();
            while (currentProc != null) {
                Process p = currentProc.data;
                FileSystemNode archivoProc = p.getArchivo();
                RWLock lock;
                if (p.getOperacion().equals("CREATE")) {
                    lock = lockManager.getLock(p.getPadre());
                } else {
                    lock = lockManager.getLock(archivoProc);
                }
                ProcesoHilo hilo = new ProcesoHilo(p, archivoProc, lock, scheduler, disk, lockManager, this);
                synchronized (procesosActivos) {
                    procesosActivos.add(hilo);
                }
                hilo.start();
                currentProc = currentProc.next;
            }

            refreshTree();
            updateDiskView();
            actualizarVistaProcesos();
            actualizarTablaAsignacion();
            actualizarVistaJournal();

            JOptionPane.showMessageDialog(this, 
                "Estado cargado exitosamente", 
                "Éxito", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void drawDisk(Graphics g) {
        int cols = 16;
        int blockSize = 35;
        Font font = new Font("Monospaced", Font.BOLD, 12);
        g.setFont(font);
        Disk.Block[] blocks = disk.getBlocks();
        
        for (int i = 0; i < Disk.SIZE; i++) {
            int x = (i % cols) * blockSize;
            int y = (i / cols) * blockSize;
            
            if (blocks[i].isLibre()) {
                g.setColor(new Color(245, 245, 245));
            } else {
                Color c = blocks[i].getColor();
                g.setColor(c != null ? c : new Color(100, 200, 100));
            }
            g.fillRect(x, y, blockSize - 1, blockSize - 1);
            
            g.setColor(new Color(200, 200, 200));
            g.drawRect(x, y, blockSize - 1, blockSize - 1);
            
            String num = String.valueOf(i);
            int textWidth = g.getFontMetrics().stringWidth(num);
            int textX = x + (blockSize - textWidth) / 2;
            int textY = y + (blockSize + g.getFontMetrics().getAscent()) / 2 - 3;
            
            if (blocks[i].isLibre()) {
                g.setColor(Color.DARK_GRAY);
            } else {
                Color bg = blocks[i].getColor();
                if (bg != null) {
                    int luminancia = (int)(0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue());
                    g.setColor(luminancia < 128 ? Color.WHITE : Color.BLACK);
                } else {
                    g.setColor(Color.BLACK);
                }
            }
            g.drawString(num, textX, textY);
        }
    }

    private void updateDiskView() {
        diskPanel.repaint();
    }

    private void initFileSystem() {
        root = new FileSystemNode();
        root.setName("/");
        root.setOwner("admin");
        root.setDirectory(true);
        root.setParent(null);
        root.setChildren(new LinkedList<>());

        // Directorios base
        FileSystemNode home = new FileSystemNode();
        home.setName("home");
        home.setOwner("admin");
        home.setDirectory(true);
        home.setParent(root);
        home.setChildren(new LinkedList<>());
        root.getChildren().add(home);

        FileSystemNode etc = new FileSystemNode();
        etc.setName("etc");
        etc.setOwner("admin");
        etc.setDirectory(true);
        etc.setParent(root);
        etc.setChildren(new LinkedList<>());
        root.getChildren().add(etc);

        FileSystemNode usr = new FileSystemNode();
        usr.setName("usr");
        usr.setOwner("admin");
        usr.setDirectory(true);
        usr.setParent(root);
        usr.setChildren(new LinkedList<>());
        root.getChildren().add(usr);

        // Usuarios
        FileSystemNode user1 = new FileSystemNode();
        user1.setName("usuario1");
        user1.setOwner("usuario1");
        user1.setDirectory(true);
        user1.setParent(home);
        user1.setChildren(new LinkedList<>());
        home.getChildren().add(user1);

        FileSystemNode user2 = new FileSystemNode();
        user2.setName("usuario2");
        user2.setOwner("usuario2");
        user2.setDirectory(true);
        user2.setParent(home);
        user2.setChildren(new LinkedList<>());
        home.getChildren().add(user2);

        // Archivos con colores únicos
        FileSystemNode doc1 = new FileSystemNode();
        doc1.setName("documento.txt");
        doc1.setOwner("usuario1");
        doc1.setDirectory(false);
        doc1.setSizeInBlocks(5);
        Color colorDoc1 = disk.generateUniqueColor();
        int firstBlockDoc1 = disk.asignarBloques(5, colorDoc1);
        doc1.setFirstBlock(firstBlockDoc1);
        doc1.setColor(colorDoc1);
        doc1.setParent(user1);
        user1.getChildren().add(doc1);

        FileSystemNode foto = new FileSystemNode();
        foto.setName("foto.jpg");
        foto.setOwner("usuario1");
        foto.setDirectory(false);
        foto.setSizeInBlocks(8);
        Color colorFoto = disk.generateUniqueColor();
        int firstBlockFoto = disk.asignarBloques(8, colorFoto);
        foto.setFirstBlock(firstBlockFoto);
        foto.setColor(colorFoto);
        foto.setParent(user1);
        user1.getChildren().add(foto);

        FileSystemNode notas = new FileSystemNode();
        notas.setName("notas.txt");
        notas.setOwner("usuario2");
        notas.setDirectory(false);
        notas.setSizeInBlocks(3);
        Color colorNotas = disk.generateUniqueColor();
        int firstBlockNotas = disk.asignarBloques(3, colorNotas);
        notas.setFirstBlock(firstBlockNotas);
        notas.setColor(colorNotas);
        notas.setParent(user2);
        user2.getChildren().add(notas);

        FileSystemNode config = new FileSystemNode();
        config.setName("config.conf");
        config.setOwner("admin");
        config.setDirectory(false);
        config.setSizeInBlocks(2);
        Color colorConfig = disk.generateUniqueColor();
        int firstBlockConfig = disk.asignarBloques(2, colorConfig);
        config.setFirstBlock(firstBlockConfig);
        config.setColor(colorConfig);
        config.setParent(etc);
        etc.getChildren().add(config);
    }
    
    private void cargarCasoPrueba() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Seleccionar archivo JSON de caso de prueba");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Archivos JSON", "json"));

        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File archivo = fileChooser.getSelectedFile();

            // ===== NUEVO: Verificar si el disco tiene archivos =====
            if (tieneArchivos(root)) {
                int respuesta = JOptionPane.showConfirmDialog(this,
                    "⚠️ El disco actual tiene archivos.\n" +
                    "Para evitar conflictos con las posiciones del JSON,\n" +
                    "es RECOMENDABLE limpiar el disco primero.\n\n" +
                    "¿Desea limpiar el disco antes de cargar?",
                    "Confirmar carga", 
                    JOptionPane.YES_NO_CANCEL_OPTION, 
                    JOptionPane.WARNING_MESSAGE);

                if (respuesta == JOptionPane.CANCEL_OPTION) {
                    return; // Cancelar carga
                }

                if (respuesta == JOptionPane.YES_OPTION) {
                    limpiarDiscoCompleto(); // Limpia el disco
                    // Después de limpiar, continuamos con la carga
                }
                // Si dice NO, continúa con la carga (puede haber conflictos)
            }

            try {
                TestCase testCase = TestCaseLoader.cargarTestCase(archivo.getAbsolutePath());

                if (testCase != null) {
                    // ===== NUEVO: Si no se limpió, preguntar si quiere reiniciar =====
                    if (tieneArchivos(root)) {
                        int reiniciar = JOptionPane.showConfirmDialog(this,
                            "¿Desea reiniciar el sistema antes de cargar?\n" +
                            "(Esto eliminará los archivos actuales)",
                            "Reiniciar sistema", 
                            JOptionPane.YES_NO_OPTION);

                        if (reiniciar == JOptionPane.YES_OPTION) {
                            reiniciarSistema();
                        }
                    } else {
                        reiniciarSistema(); // Si está vacío, reiniciamos normalmente
                    }

                    LinkedList<Process> procesosCreados = new LinkedList<>();
                    TestCaseLoader.aplicarTestCase(testCase, root, disk, scheduler, procesosCreados);

                    // Verificar si hubo errores al asignar bloques
                    int errores = 0;
                    Node<Process> checkProc = procesosCreados.getHead();
                    while (checkProc != null) {
                        Process p = checkProc.data;
                        if (p.getArchivo() != null && p.getArchivo().getFirstBlock() == -1) {
                            errores++;
                        }
                        checkProc = checkProc.next;
                    }

                    if (errores > 0) {
                        logArea.append("⚠️ " + errores + " archivos no pudieron asignarse (bloques ocupados)\n");
                    }

                    // Establecer el número de solicitudes esperadas en el scheduler
                    scheduler.setSolicitudesEsperadas(procesosCreados.size());

                    Node<Process> currentProc = procesosCreados.getHead();
                    while (currentProc != null) {
                        Process p = currentProc.data;
                        FileSystemNode archivoProc = p.getArchivo();
                        RWLock lock;
                        if (p.getOperacion().equals("CREATE")) {
                            lock = lockManager.getLock(p.getPadre());
                        } else {
                            lock = lockManager.getLock(archivoProc);
                        }
                        ProcesoHilo hilo = new ProcesoHilo(p, archivoProc, lock, scheduler, disk, lockManager, this);
                        synchronized (procesosActivos) {
                            procesosActivos.add(hilo);
                        }
                        hilo.start();
                        currentProc = currentProc.next;
                    }

                    refreshTree();
                    updateDiskView();
                    actualizarVistaProcesos();
                    actualizarTablaAsignacion();
                    actualizarVistaJournal();

                    logArea.append("📋 Caso de prueba cargado: " + testCase.getTestId() + "\n");
                    logArea.append("   Cabezal inicial: " + testCase.getInitialHead() + "\n");
                    logArea.append("   Solicitudes: " + testCase.getRequests().size() + "\n");
                    if (errores > 0) {
                        logArea.append("   ⚠️ Errores de asignación: " + errores + "\n");
                    }

                    String mensaje = "Caso de prueba cargado exitosamente:\n" +
                                     "ID: " + testCase.getTestId() + "\n" +
                                     "Solicitudes: " + testCase.getRequests().size();

                    if (errores > 0) {
                        mensaje += "\n\n⚠️ ADVERTENCIA: " + errores + 
                                   " archivos no pudieron asignarse por bloques ocupados.\n" +
                                   "Use '🧹 Limpiar Disco' y vuelva a cargar.";
                    }

                    JOptionPane.showMessageDialog(this, 
                        mensaje,
                        errores > 0 ? "Advertencia" : "Éxito", 
                        errores > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                }

            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "Error cargando caso de prueba:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }

    // Método auxiliar que ya tienes
    private boolean tieneArchivos(FileSystemNode node) {
        if (node.getChildren() != null && node.getChildren().size() > 0) {
            return true;
        }
        return false;
    }

    private void reiniciarSistema() {
        if (scheduler != null) {
            scheduler.detener();
            try {
                scheduler.join(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Guardar journal antes de reiniciar
        if (journalManager != null) {
            journalManager.guardarJournal();
        }

        // NO crear nuevo disk y root aquí
        // Simplemente detenemos el scheduler y lo reiniciamos

        operaciones = new OperacionesArchivo(disk, root, journalManager);
        scheduler = new Scheduler(disk, operaciones, this);

        int politicaSeleccionada = politicaCombo.getSelectedIndex();
        scheduler.setPolitica(politicaSeleccionada);
        scheduler.setModoManual(chkModoManual.isSelected());
        scheduler.start();

        procesosActivos = new LinkedList<>();
        lockManager = new LockManager();

        logArea.append("🔄 Sistema reiniciado\n");
        btnPaso.setEnabled(chkModoManual.isSelected());

        actualizarTablaAsignacion();
        actualizarVistaJournal();
        actualizarVistaJournal();
        updateDiskView();
        refreshTree();
    }

    private void createDirectory(FileSystemNode parent, String name, String owner) {
        if (parent == null || !parent.isDirectory()) return;
        FileSystemNode newDir = new FileSystemNode();
        newDir.setName(name);
        newDir.setOwner(owner);
        newDir.setDirectory(true);
        newDir.setParent(parent);
        newDir.setChildren(new LinkedList<>());
        if (parent.getChildren() == null) parent.setChildren(new LinkedList<>());
        parent.getChildren().add(newDir);
    }

    private FileSystemNode findNodeByPath(String path) {
        if (path.equals("/") || path.equals("")) return root;
        String[] parts = path.split("/");
        FileSystemNode current = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (current.getChildren() != null) {
                Node<FileSystemNode> childNode = current.getChildren().getHead();
                boolean found = false;
                while (childNode != null) {
                    if (childNode.data.getName().equals(part)) {
                        current = childNode.data;
                        found = true;
                        break;
                    }
                    childNode = childNode.next;
                }
                if (!found) return null;
            } else return null;
        }
        return current;
    }

    private void buildTreeNodes(DefaultMutableTreeNode treeNode, FileSystemNode fsNode) {
        DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(fsNode.getName());
        childNode.setUserObject(fsNode);
        treeNode.add(childNode);

        if (fsNode.isDirectory() && fsNode.getChildren() != null && fsNode.getChildren().size() > 0) {
            Node<FileSystemNode> current = fsNode.getChildren().getHead();
            while (current != null) {
                buildTreeNodes(childNode, current.data);
                current = current.next;
            }
        }
    }

    private void refreshTree() {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Raíz (/)");
        rootNode.setUserObject(root);
        rootNode.removeAllChildren();
        if (root.getChildren() != null) {
            Node<FileSystemNode> current = root.getChildren().getHead();
            while (current != null) {
                buildTreeNodes(rootNode, current.data);
                current = current.next;
            }
        }
        treeModel.setRoot(rootNode);
        treeModel.reload();
        expandAllNodes();
    }

    private void expandAllNodes() {
        for (int i = 0; i < fileTree.getRowCount(); i++) fileTree.expandRow(i);
    }

   private void showFileInfo() {

    DefaultMutableTreeNode selectedNode =
            (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();

    if (selectedNode == null) {
        infoArea.setText("No hay ningún elemento seleccionado.");
        return;
    }

    Object userObj = selectedNode.getUserObject();

    if (!(userObj instanceof FileSystemNode)) {
        infoArea.setText("No hay información disponible para este elemento.");
        return;
    }

    FileSystemNode fsNode = (FileSystemNode) userObj;

    StringBuilder info = new StringBuilder();

    info.append("INFORMACION DEL ELEMENTO\n");
    info.append("====================================\n\n");

    info.append("Nombre        : ").append(fsNode.getName()).append("\n");
    info.append("Tipo          : ")
        .append(fsNode.isDirectory() ? "Directorio" : "Archivo").append("\n");
    info.append("Propietario   : ").append(fsNode.getOwner()).append("\n");
    info.append("Ruta completa : ").append(getFullPath(fsNode)).append("\n");
    info.append("Modo de sesion: ")
        .append(userSession.isAdmin() ? "Administrador" : "Usuario").append("\n");

    info.append("\n------------------------------------\n");

    if (!fsNode.isDirectory()) {

        info.append("INFORMACION DEL ARCHIVO\n");
        info.append("------------------------------------\n");

        info.append("Tamano en disco : ")
            .append(fsNode.getSizeInBlocks())
            .append(" bloques\n");

        info.append("Primer bloque   : ")
            .append(fsNode.getFirstBlock())
            .append("\n");

    } else {

        int count = (fsNode.getChildren() != null)
                ? fsNode.getChildren().size()
                : 0;

        info.append("INFORMACION DEL DIRECTORIO\n");
        info.append("------------------------------------\n");

        info.append("Elementos contenidos : ")
            .append(count)
            .append("\n");
    }

    infoArea.setText(info.toString());
}

    private String getFullPath(FileSystemNode node) {
        if (node == null) return "";
        if (node.getParent() == null) return node.getName();
        String parentPath = getFullPath(node.getParent());
        if (parentPath.equals("/")) return parentPath + node.getName();
        else return parentPath + "/" + node.getName();
    }

    private FileSystemNode getSelectedDirectory() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
        if (selectedNode == null) return root;
        Object userObj = selectedNode.getUserObject();
        if (userObj instanceof FileSystemNode) {
            FileSystemNode node = (FileSystemNode) userObj;
            if (!node.isDirectory()) return node.getParent();
            return node;
        }
        return root;
    }

    // ----- Métodos para actualizar vistas -----
    private void actualizarVistaProcesos() {
        SwingUtilities.invokeLater(() -> {
            // Limpiar todas las listas
            listosModel.clear();
            cpuModel.clear();
            bloqueadosModel.clear();

            synchronized (procesosActivos) {
                Node<ProcesoHilo> current = procesosActivos.getHead();
                while (current != null) {
                    Process p = current.data.getDatosProceso();
                    String estado = p.getEstado();
                    if ("LISTO".equals(estado) || "NUEVO".equals(estado)) {
                        listosModel.addElement(p);
                    } else if ("EJECUTANDO".equals(estado)) {
                        cpuModel.addElement(p);
                    } else if ("BLOQUEADO".equals(estado)) {
                        bloqueadosModel.addElement(p);
                    }
                    // Los terminados no se muestran
                    current = current.next;
                }
            }
            
            // Actualizar cabezal
            cabezaLabel.setText("Cabezal: " + (scheduler != null ? scheduler.getCabezaActual() : 0));
            
            // Actualizar solicitudes pendientes
            if (scheduler != null) {
                LinkedList<String> pendientes = scheduler.getSolicitudesPendientes();
                solicitudesModel.clear();
                Node<String> currentPend = pendientes.getHead();
                while (currentPend != null) {
                    solicitudesModel.addElement(currentPend.data);
                    currentPend = currentPend.next;
                }
            }
            
            // Actualizar locks
            actualizarLocksView();
        });
    }

    private void actualizarLocksView() {
        if (locksModel != null && lockManager != null) {
            LinkedList<String> info = lockManager.getLocksInfo();
            locksModel.clear();
            Node<String> current = info.getHead();
            while (current != null) {
                locksModel.addElement(current.data);
                current = current.next;
            }
        }
    }

    private void actualizarDesplazamiento() {
        desplazamientoLabel.setText("  Desplazamiento total: " + desplazamientoTotal);
    }

    // ----- Métodos para la tabla de asignación -----
    private void actualizarTablaAsignacion() {
        SwingUtilities.invokeLater(() -> {
            tablaModel.setRowCount(0);
            recorrerYAgregarArchivos(root);
        });
    }

    private void recorrerYAgregarArchivos(FileSystemNode nodo) {
        if (!nodo.isDirectory()) {
            tablaModel.addRow(new Object[]{
                nodo.getName(),
                nodo.getSizeInBlocks(),
                nodo.getFirstBlock(),
                "" // El color se muestra mediante renderer, no necesitamos texto
            });
        } else {
            if (nodo.getChildren() != null) {
                Node<FileSystemNode> current = nodo.getChildren().getHead();
                while (current != null) {
                    recorrerYAgregarArchivos(current.data);
                    current = current.next;
                }
            }
        }
    }

    // ----- Método auxiliar para buscar un archivo por nombre -----
    private FileSystemNode buscarArchivo(FileSystemNode nodo, String nombre) {
        if (!nodo.isDirectory()) {
            if (nodo.getName().equals(nombre)) {
                return nodo;
            }
        }

        if (nodo.getChildren() != null) {
            Node<FileSystemNode> current = nodo.getChildren().getHead();

            while (current != null) {
                FileSystemNode encontrado = buscarArchivo(current.data, nombre);

                if (encontrado != null) {
                    return encontrado;
                }

                current = current.next;
            }
        }

        return null;
    }

    // ----- Método para añadir entradas al journal -----
    public void agregarEntradaJournal(String operacion, String archivo, int bloque, String estado) {
        SwingUtilities.invokeLater(() -> {
            journalModel.addRow(new Object[]{operacion, archivo, bloque, estado});
            // Mantener solo las últimas 20 entradas para no saturar
            if (journalModel.getRowCount() > 20) {
                journalModel.removeRow(0);
            }
        });
    }

    // ----- Implementación de Terminable -----
    @Override
    public void onTerminate(ProcesoHilo hilo) {
        SwingUtilities.invokeLater(() -> {
            synchronized (procesosActivos) {
                procesosActivos.remove(hilo);
            }
            refreshTree();
            updateDiskView();
            actualizarVistaProcesos();
            actualizarTablaAsignacion();
            actualizarVistaJournal();
        });
    }

    // ----- Implementación de LogListener -----
    @Override
    public void onMovimiento(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(mensaje + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    private void verificarJournalPendiente() {
        LinkedList<JournalEntry> pendientes = journalManager.getEntradasPendientes();
        if (pendientes.size() > 0) {
            int respuesta = JOptionPane.showConfirmDialog(this,
                "Se encontraron " + pendientes.size() + " operaciones pendientes en el journal.\n" +
                "¿Desea deshacerlas para recuperar la consistencia?",
                "Recuperación ante fallos", JOptionPane.YES_NO_OPTION);

            if (respuesta == JOptionPane.YES_OPTION) {
                journalManager.deshacerPendientes(root, disk);
                refreshTree();
                updateDiskView();
                actualizarTablaAsignacion();
                actualizarVistaJournal();
                JOptionPane.showMessageDialog(this, "Recuperación completada.");
            }
        }
    }
    private void limpiarDiscoCompleto() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "⚠️ ¿ESTÁ SEGURO?\n\n" +
            "Esta operación ELIMINARÁ TODOS los archivos y directorios,\n" +
            "dejando el disco completamente VACÍO.\n\n" +
            "Se perderán todos los cambios no guardados.",
            "Limpiar Disco", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            // Detener scheduler
            if (scheduler != null) {
                scheduler.detener();
                try {
                    scheduler.join(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Crear NUEVO disco vacío
            disk = new Disk();

            // Crear NUEVO root vacío (solo la raíz)
            root = new FileSystemNode();
            root.setName("/");
            root.setOwner("admin");
            root.setDirectory(true);
            root.setParent(null);
            root.setChildren(new LinkedList<>()); // Sin hijos

            // Reiniciar journal (opcional - mantener o limpiar)
            int mantenerJournal = JOptionPane.showConfirmDialog(this,
                "¿Mantener el journal actual?\n" +
                "(Recomendado: NO para empezar limpio)",
                "Journal", JOptionPane.YES_NO_OPTION);

            if (mantenerJournal == JOptionPane.NO_OPTION) {
                journalManager = new JournalManager(); // Crea journal vacío
            }

            // Recrear scheduler
            operaciones = new OperacionesArchivo(disk, root, journalManager);
            scheduler = new Scheduler(disk, operaciones, this);
            int politicaSeleccionada = politicaCombo.getSelectedIndex();
            scheduler.setPolitica(politicaSeleccionada);
            scheduler.setModoManual(chkModoManual.isSelected());
            scheduler.start();

            procesosActivos = new LinkedList<>();
            lockManager = new LockManager();

            // Actualizar todas las vistas
            refreshTree();
            updateDiskView();
            actualizarVistaProcesos();
            actualizarTablaAsignacion();
            actualizarVistaJournal();
            logArea.setText("🧹 Disco limpiado completamente\n");

            JOptionPane.showMessageDialog(this, 
                "✅ Disco limpiado exitosamente.\n" +
                "Ahora puede cargar cualquier JSON sin conflictos.",
                "Disco Limpio", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ----- Métodos de solicitud de operaciones -----
    private void solicitarCrearDirectorio() {
        JTextField nameField = new JTextField(20);
        JTextField ownerField = new JTextField(userSession.getCurrentUser(), 20);
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.add(new JLabel("Nombre:"));
        panel.add(nameField);
        panel.add(new JLabel("Dueño:"));
        panel.add(ownerField);

        FileSystemNode parentDir = getSelectedDirectory();

        // VERIFICACIÓN DE PERMISOS
        if (!userSession.canCreateIn(parentDir)) {
            String mensaje = userSession.isAdmin() ? 
                "No tiene permisos para crear directorios aquí" :
                "Modo usuario: solo puede crear en sus propios directorios";
            JOptionPane.showMessageDialog(this, mensaje, "Permiso denegado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // En modo usuario, el dueño debe ser el usuario actual
        if (!userSession.isAdmin()) {
            ownerField.setText(userSession.getCurrentUser());
            ownerField.setEnabled(false);
        }

        String parentPath = (parentDir != null) ? getFullPath(parentDir) : "/";
        panel.add(new JLabel(""));
        panel.add(new JLabel("Directorio actual: " + parentPath));

        int result = JOptionPane.showConfirmDialog(this, panel, "Crear directorio", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String owner = ownerField.getText().trim();

            if (name.isEmpty() || owner.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nombre y dueño no pueden estar vacíos", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // En modo usuario, verificar dueño
            if (!userSession.isAdmin() && !owner.equals(userSession.getCurrentUser())) {
                JOptionPane.showMessageDialog(this, 
                    "Modo usuario: no puede crear directorios para otro usuario", 
                    "Permiso denegado", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (parentDir == null) {
                JOptionPane.showMessageDialog(this, "Debe seleccionar un directorio padre", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            createDirectory(parentDir, name, owner);
            refreshTree();
            logArea.append("📁 Directorio creado: " + name + " en " + parentPath + "\n");
        }
    }
    private void abrirArchivoJournal() {
        try {
            File file = new File("data/journal.txt");
            if (file.exists()) {
                Desktop.getDesktop().open(file); // Abre con programa predeterminado
            } else {
                JOptionPane.showMessageDialog(this, 
                    "El archivo journal.txt no existe.",
                    "Información", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Error al abrir el archivo: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void solicitarCrearArchivo() {
        JTextField nameField = new JTextField(20);
        JTextField ownerField = new JTextField(userSession.getCurrentUser(), 20); // Dueño por defecto = usuario actual
        JTextField sizeField = new JTextField("5", 10);
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.add(new JLabel("Nombre:"));
        panel.add(nameField);
        panel.add(new JLabel("Dueño:"));
        panel.add(ownerField);
        panel.add(new JLabel("Tamaño (bloques):"));
        panel.add(sizeField);

        FileSystemNode parentDir = getSelectedDirectory();

        // VERIFICACIÓN DE PERMISOS CORREGIDA
        if (!userSession.canCreateIn(parentDir)) {
            String mensaje = userSession.isAdmin() ? 
                "No tiene permisos para crear archivos en este directorio" :
                "Modo usuario: solo puede crear en sus propios directorios (dueño: " + parentDir.getOwner() + ")";
            JOptionPane.showMessageDialog(this, mensaje, "Permiso denegado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // En modo usuario, el dueño debe ser el usuario actual
        if (!userSession.isAdmin()) {
            ownerField.setText(userSession.getCurrentUser());
            ownerField.setEnabled(false); // No puede cambiar el dueño
        }

        String parentPath = (parentDir != null) ? getFullPath(parentDir) : "/";
        panel.add(new JLabel(""));
        panel.add(new JLabel("Directorio actual: " + parentPath));
        panel.add(new JLabel(""));
        panel.add(new JLabel("Dueño fijado: " + (userSession.isAdmin() ? "editable" : userSession.getCurrentUser())));

        int result = JOptionPane.showConfirmDialog(this, panel, "Crear archivo", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String owner = ownerField.getText().trim();

            if (name.isEmpty() || owner.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nombre y dueño no pueden estar vacíos", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // En modo usuario, verificar que el dueño sea el usuario actual
            if (!userSession.isAdmin() && !owner.equals(userSession.getCurrentUser())) {
                JOptionPane.showMessageDialog(this, 
                    "Modo usuario: no puede crear archivos para otro usuario", 
                    "Permiso denegado", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (parentDir == null) {
                JOptionPane.showMessageDialog(this, "Debe seleccionar un directorio padre", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                int size = Integer.parseInt(sizeField.getText().trim());
                if (size <= 0) throw new NumberFormatException();

                // Registrar en journal ANTES de ejecutar
                String rutaCompleta = getFullPath(parentDir) + "/" + name;
                JournalEntry entry = new JournalEntry("CREATE", rutaCompleta, owner, -1, size);
                journalManager.agregarEntrada(entry);
                agregarEntradaJournal("CREATE", name, -1, "PENDIENTE");

                Process p = new Process("CREATE", name, owner, size, parentDir);
                RWLock lock = lockManager.getLock(parentDir);
                ProcesoHilo hilo = new ProcesoHilo(p, parentDir, lock, scheduler, disk, lockManager, this);

                synchronized (procesosActivos) {
                    procesosActivos.add(hilo);
                }
                hilo.start();
                actualizarVistaProcesos();

                logArea.append("📝 Proceso de creación creado: " + name + " en " + parentPath + "\n");

            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Tamaño debe ser un número entero positivo", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void solicitarModificarNombre() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof FileSystemNode)) {
            JOptionPane.showMessageDialog(this, "Seleccione un elemento para modificar", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        FileSystemNode fsNode = (FileSystemNode) selectedNode.getUserObject();

        // VERIFICACIÓN DE PERMISOS CORREGIDA
        if (!userSession.canModify(fsNode)) {
            String mensaje = userSession.isAdmin() ? 
                "No tiene permisos para modificar este elemento" :
                "Modo usuario: solo puede modificar sus propios archivos (dueño: " + fsNode.getOwner() + ")";
            JOptionPane.showMessageDialog(this, mensaje, "Permiso denegado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (fsNode == root) {
            JOptionPane.showMessageDialog(this, "No se puede modificar el nombre de la raíz", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Mostrar dueño en el diálogo
        String nuevoNombre = JOptionPane.showInputDialog(this,
            "Modificar nombre de '" + fsNode.getName() + "'\n" +
            "Dueño: " + fsNode.getOwner() + "\n" +
            "Nuevo nombre:",
            "Modificar nombre", JOptionPane.QUESTION_MESSAGE);

        if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
            // Registrar en journal ANTES de ejecutar
            String rutaCompleta = fsNode.getFullPath();
            JournalEntry entry = new JournalEntry("UPDATE", rutaCompleta, fsNode.getName(), nuevoNombre.trim());
            journalManager.agregarEntrada(entry);
            agregarEntradaJournal("UPDATE", fsNode.getName(), fsNode.getFirstBlock(), "PENDIENTE");

            Process p = new Process("UPDATE", fsNode, userSession.getCurrentUser());
            p.setNuevoNombre(nuevoNombre.trim());
            RWLock lock = lockManager.getLock(fsNode);
            ProcesoHilo hilo = new ProcesoHilo(p, fsNode, lock, scheduler, disk, lockManager, this);

            synchronized (procesosActivos) {
                procesosActivos.add(hilo);
            }
            hilo.start();
            actualizarVistaProcesos();

            logArea.append("✏️ Proceso de modificación creado: " + fsNode.getName() + " → " + nuevoNombre + "\n");
        }
    }
    private void simularFallo() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "¿Simular fallo del sistema AHORA?\n" +
            "Se interrumpirá la ejecución y se reiniciará,\n" +
            "dejando las operaciones PENDIENTES en el journal.",
            "Simular Fallo", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            // Guardar journal
            if (journalManager != null) {
                journalManager.guardarJournal();
            }

            // Mostrar pendientes
            LinkedList<JournalEntry> pendientes = journalManager.getEntradasPendientes();
            StringBuilder mensaje = new StringBuilder("⚠️ FALLO SIMULADO\n\n");
            mensaje.append("Operaciones PENDIENTES en journal:\n");

            Node<JournalEntry> current = pendientes.getHead();
            while (current != null) {
                JournalEntry e = current.data;
                mensaje.append("  • ").append(e.getOperacion())
                       .append(" ").append(e.getRutaArchivo())
                       .append(" (").append(e.getEstado()).append(")\n");
                current = current.next;
            }

            // Detener scheduler pero NO crear nuevo disk
            if (scheduler != null) {
                scheduler.detener();
            }

            JOptionPane.showMessageDialog(this, mensaje.toString(), "Fallo Simulado", JOptionPane.WARNING_MESSAGE);

            int recuperar = JOptionPane.showConfirmDialog(this,
                "¿Reiniciar el sistema y recuperar del journal?",
                "Recuperación", JOptionPane.YES_NO_OPTION);

            if (recuperar == JOptionPane.YES_OPTION) {
                // Recuperar: deshacer pendientes
                journalManager.deshacerPendientes(root, disk);

                // Reiniciar scheduler con los MISMOS disk y root
                reiniciarScheduler();

                JOptionPane.showMessageDialog(this, "Recuperación completada.");
            } else {
                // No recuperar: solo reiniciar scheduler
                reiniciarScheduler();
            }

            // Actualizar vistas
            actualizarVistaJournal();
            updateDiskView();
            refreshTree();
            actualizarTablaAsignacion();
        }
    }

    private void reiniciarScheduler() {
        if (scheduler != null) {
            scheduler.detener();
            try {
                scheduler.join(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        operaciones = new OperacionesArchivo(disk, root, journalManager);
        scheduler = new Scheduler(disk, operaciones, this);

        int politicaSeleccionada = politicaCombo.getSelectedIndex();
        scheduler.setPolitica(politicaSeleccionada);
        scheduler.setModoManual(chkModoManual.isSelected());
        scheduler.start();

        procesosActivos = new LinkedList<>();
        lockManager = new LockManager();
    }
    

    private void solicitarEliminar() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof FileSystemNode)) {
            JOptionPane.showMessageDialog(this, "Seleccione un elemento para eliminar", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        FileSystemNode fsNode = (FileSystemNode) selectedNode.getUserObject();

        // VERIFICACIÓN DE PERMISOS CORREGIDA
        if (!userSession.canDelete(fsNode)) {
            String mensaje = userSession.isAdmin() ? 
                "No tiene permisos para eliminar este elemento" :
                "Modo usuario: solo puede eliminar sus propios archivos";
            JOptionPane.showMessageDialog(this, mensaje, "Permiso denegado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (fsNode == root) {
            JOptionPane.showMessageDialog(this, "No se puede eliminar el directorio raíz", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Mostrar información del dueño en el mensaje de confirmación
        String mensajeConfirmacion = "¿Está seguro de eliminar " + fsNode.getFullPath() + "?\n" +
                                     "Dueño: " + fsNode.getOwner() + "\n" +
                                     (fsNode.isDirectory() ? "Se eliminarán todos sus contenidos." : "");

        int confirm = JOptionPane.showConfirmDialog(this,
                mensajeConfirmacion,
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            Process p = new Process("DELETE", fsNode, userSession.getCurrentUser());
            RWLock lock = lockManager.getLock(fsNode);
            ProcesoHilo hilo = new ProcesoHilo(p, fsNode, lock, scheduler, disk, lockManager, this);
            synchronized (procesosActivos) {
                procesosActivos.add(hilo);
            }
            hilo.start();
            actualizarVistaProcesos();

            // Mensaje de log
            logArea.append("🗑️ Proceso de eliminación creado para: " + fsNode.getName() + "\n");
        }
    }
    // ===== MÉTODO PARA ACTUALIZAR LA VISTA DEL JOURNAL =====
    private void actualizarVistaJournal() {
        SwingUtilities.invokeLater(() -> {
            journalModel.setRowCount(0); // Limpiar la tabla

            if (journalManager == null) {
                System.out.println("journalManager es null");
                return;
            }

            LinkedList<JournalEntry> entradas = journalManager.getEntradas();
            System.out.println("Entradas en journal: " + (entradas != null ? entradas.size() : "null"));

            if (entradas == null || entradas.size() == 0) {
                System.out.println("No hay entradas en journal");
                journalModel.fireTableDataChanged();
                return;
            }

            Node<JournalEntry> current = entradas.getHead();
            java.util.ArrayList<JournalEntry> listaTemporal = new java.util.ArrayList<>();
            while (current != null) {
                listaTemporal.add(current.data);
                current = current.next;
            }

            for (int i = listaTemporal.size() - 1; i >= 0; i--) {
                JournalEntry e = listaTemporal.get(i);

                String archivo = e.getRutaArchivo();
                if (archivo != null && archivo.length() > 25) {
                    archivo = "..." + archivo.substring(archivo.length() - 22);
                }

                String bloqueStr = (e.getPrimerBloque() == -1) ? "⏳" : String.valueOf(e.getPrimerBloque());

                String estadoStr = e.getEstado();
                if (e.getEstado() != null) {
                    if (e.getEstado().equals("PENDIENTE")) {
                        estadoStr = "⏳ " + e.getEstado();
                    } else if (e.getEstado().equals("CONFIRMADA")) {
                        estadoStr = "✅ " + e.getEstado();
                    } else if (e.getEstado().equals("DESHECHA")) {
                        estadoStr = "↩️ " + e.getEstado();
                    }
                }

                journalModel.addRow(new Object[]{
                    e.getOperacion(),
                    archivo,
                    bloqueStr,
                    estadoStr
                });
            }

            while (journalModel.getRowCount() > 30) {
                journalModel.removeRow(0);
            }

            journalModel.fireTableDataChanged();
            journalTable.repaint();
        });
    }
    // Para limpiar el journal si es necesario
    private void limpiarJournal() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "¿Eliminar TODAS las entradas del journal?\n\n" +
            "Esta operación:\n" +
            "• Borrará el archivo journal.txt\n" +
            "• Vaciará la tabla de journal\n" +
            "• No afectará los archivos del sistema\n\n" +
            "¿Está seguro?",
            "Limpiar Journal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                // 1. LIMPIAR EL JOURNAL MANAGER (nuevo objeto)
                JournalManager nuevoJournal = new JournalManager();
                this.journalManager = nuevoJournal;

                // 2. Actualizar la referencia en OperacionesArchivo
                if (operaciones != null) {
                    operaciones.setJournalManager(journalManager);
                }

                // 3. Borrar el archivo físico
                File journalFile = new File("data/journal.txt");
                if (journalFile.exists()) {
                    boolean deleted = journalFile.delete();
                    if (deleted) {
                        logArea.append("📄 Archivo journal.txt eliminado\n");
                    }
                }

                // 4. Crear archivo vacío
                journalFile.getParentFile().mkdirs();
                journalFile.createNewFile();

                // 5. Guardar el journal vacío
                journalManager.guardarJournal();

                // 6. ACTUALIZAR LA VISTA - ESTO ES CRÍTICO
                actualizarVistaJournal(); // <-- LLAMAR EXPLÍCITAMENTE

                // 7. Forzar actualización de la UI
                SwingUtilities.invokeLater(() -> {
                    journalModel.fireTableDataChanged();
                    journalTable.repaint();
                });

                JOptionPane.showMessageDialog(this, 
                    "✅ Journal limpiado exitosamente",
                    "Journal limpiado", JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, 
                    "Error al limpiar el journal:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void liberarBloquesRecursivo(FileSystemNode node) {
        if (node.isDirectory()) {
            if (node.getChildren() != null) {
                Node<FileSystemNode> current = node.getChildren().getHead();
                while (current != null) {
                    liberarBloquesRecursivo(current.data);
                    current = current.next;
                }
            }
        } else {
            disk.liberarBloques(node.getFirstBlock(), node.getColor());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            FileSystemGUI gui = new FileSystemGUI();
            gui.setVisible(true);
            System.out.println("✅ Ventana del simulador abierta correctamente");
        });
    }
}