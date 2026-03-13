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
import test.TestCase;
import test.TestCaseLoader;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import persistence.FileSystemPersistence;

public class FileSystemGUI extends JFrame implements Terminable, LogListener {
    private JTree fileTree;
    private DefaultTreeModel treeModel;
    private JTextArea infoArea;
    private FileSystemNode root;
    private Disk disk;
    private JPanel diskPanel;
    
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
        initComponents();
        initFileSystem();
        
        // Crear operaciones y scheduler después de tener root
        operaciones = new OperacionesArchivo(disk, root);
        scheduler = new Scheduler(disk, operaciones, this);
        scheduler.start(); // Inicia el hilo del scheduler
        
        lockManager = new LockManager();
        procesosActivos = new LinkedList<>();
        
        refreshTree();
        updateDiskView();
        actualizarVistaProcesos();
        actualizarTablaAsignacion(); // Mostrar archivos iniciales
        actualizarPermisosInterfaz();
        
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

        // Panel derecho con información, tabla y disco
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("Información del elemento"));
        infoArea = new JTextArea(10, 25);
        infoArea.setEditable(false);
        infoArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        infoPanel.add(new JScrollPane(infoArea), BorderLayout.CENTER);

        // --- Tabla de asignación ---
        tablaModel = new DefaultTableModel(new String[]{"Nombre", "Bloques", "Primer Bloque", "Color"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaAsignacion = new JTable(tablaModel);
        tablaAsignacion.setFillsViewportHeight(true);
        tablaAsignacion.setRowHeight(22);
        tablaAsignacion.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tablaAsignacion.setFont(new Font("Segoe UI", Font.PLAIN, 12));

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
        scrollTabla.setBorder(BorderFactory.createTitledBorder("Tabla de Asignación"));
        scrollTabla.setPreferredSize(new Dimension(400, 150));

        // --- Panel del disco (matriz) ---
        diskPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawDisk(g);
            }
        };
        diskPanel.setPreferredSize(new Dimension(600, 600));
        diskPanel.setBackground(Color.WHITE);
        JScrollPane scrollDisco = new JScrollPane(diskPanel);
        scrollDisco.setBorder(BorderFactory.createTitledBorder("Disco (bloques)"));

        // --- Tabla de journaling (nuevo panel a la derecha del disco) ---
        journalModel = new DefaultTableModel(new String[]{"Operación", "Archivo", "Bloque", "Estado"}, 0);
        journalTable = new JTable(journalModel);
        journalTable.setRowHeight(22);
        journalTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        journalTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JScrollPane scrollJournal = new JScrollPane(journalTable);
        scrollJournal.setBorder(BorderFactory.createTitledBorder("Journaling"));

        // Dividir el espacio del disco y el journal horizontalmente
        JSplitPane diskSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                scrollDisco,
                scrollJournal
        );
        diskSplit.setResizeWeight(0.65); // 65% para el disco, 35% para journal

        // Panel que contiene todo lo del disco (matriz + journal)
        JPanel diskViewPanel = new JPanel(new BorderLayout());
        diskViewPanel.setBorder(BorderFactory.createTitledBorder("Disco y Journal"));
        diskViewPanel.add(diskSplit, BorderLayout.CENTER);

        // ===== PANEL DERECHO COMPLETO =====
        // Organizamos verticalmente: tabla de asignación, información, disco+journal
        JSplitPane topRightSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                scrollTabla,
                infoPanel
        );
        topRightSplit.setResizeWeight(0.3);

        JSplitPane rightPanel = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                topRightSplit,
                diskViewPanel
        );
        rightPanel.setResizeWeight(0.35);

        // Split horizontal principal
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                treePanel,
                rightPanel
        );
        splitPane.setDividerLocation(300);
        add(splitPane, BorderLayout.CENTER);

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

        btnCreateDir.addActionListener(e -> solicitarCrearDirectorio());
        btnCreateFile.addActionListener(e -> solicitarCrearArchivo());
        btnUpdate.addActionListener(e -> solicitarModificarNombre());
        btnDelete.addActionListener(e -> solicitarEliminar());
        btnRefresh.addActionListener(e -> refreshTree());
        btnSave.addActionListener(e -> guardarEstado());   
        btnLoad.addActionListener(e -> cargarEstado());
        btnLoadTestCase.addActionListener(e -> cargarCasoPrueba());

        controlPanel.add(btnCreateDir);
        controlPanel.add(btnCreateFile);
        controlPanel.add(btnUpdate);
        controlPanel.add(btnDelete);
        controlPanel.add(btnRefresh);
        controlPanel.add(btnSave);   
        controlPanel.add(btnLoad);
        controlPanel.add(btnLoadTestCase);

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

        setSize(1400, 950); // Un poco más ancho para el journal
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
        }
    }
    
    private void cargarEstado() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "¿Cargar estado guardado?\n" +
            "Se perderán los cambios no guardados.",
            "Cargar estado", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            reiniciarSistema();
            
            LinkedList<Process> procesosCargados = new LinkedList<>();
            FileSystemPersistence.cargarTodo(root, disk, procesosCargados);
            
            refreshTree();
            updateDiskView();
            actualizarVistaProcesos();
            actualizarTablaAsignacion();
            
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

            try {
                TestCase testCase = TestCaseLoader.cargarTestCase(archivo.getAbsolutePath());

                if (testCase != null) {
                    reiniciarSistema();

                    LinkedList<Process> procesosCreados = new LinkedList<>();
                    TestCaseLoader.aplicarTestCase(testCase, root, disk, scheduler, procesosCreados);

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

                    logArea.append("📋 Caso de prueba cargado: " + testCase.getTestId() + "\n");
                    logArea.append("   Cabezal inicial: " + testCase.getInitialHead() + "\n");
                    logArea.append("   Solicitudes: " + testCase.getRequests().size() + "\n");

                    JOptionPane.showMessageDialog(this, 
                        "Caso de prueba cargado exitosamente:\n" +
                        "ID: " + testCase.getTestId() + "\n" +
                        "Solicitudes: " + testCase.getRequests().size(),
                        "Éxito", JOptionPane.INFORMATION_MESSAGE);
                }

            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "Error cargando caso de prueba:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
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
        disk = new Disk();
        root = new FileSystemNode();
        root.setName("/");
        root.setOwner("admin");
        root.setDirectory(true);
        root.setParent(null);
        root.setChildren(new LinkedList<>());

        operaciones = new OperacionesArchivo(disk, root);
        scheduler = new Scheduler(disk, operaciones, this);
        
        int politicaSeleccionada = politicaCombo.getSelectedIndex();
        scheduler.setPolitica(politicaSeleccionada);
        System.out.println("Reiniciando sistema con política: " + politicaSeleccionada);
        
        scheduler.setModoManual(chkModoManual.isSelected());
        scheduler.start();

        procesosActivos = new LinkedList<>();
        lockManager = new LockManager();

        logArea.setText("");
        btnPaso.setEnabled(chkModoManual.isSelected());
        
        // Actualizar tabla (vacía al reiniciar)
        actualizarTablaAsignacion();
        // Limpiar journal
        journalModel.setRowCount(0);
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
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
        if (selectedNode == null) return;

        Object userObj = selectedNode.getUserObject();
        if (!(userObj instanceof FileSystemNode)) {
            infoArea.setText("No hay información disponible para este elemento");
            return;
        }

        FileSystemNode fsNode = (FileSystemNode) userObj;

        StringBuilder info = new StringBuilder();
        info.append("══════════════════════════════\n");
        info.append("  INFORMACIÓN DEL ELEMENTO\n");
        info.append("══════════════════════════════\n\n");

        info.append(fsNode.isDirectory() ? "📁 " : "📄 ");
        info.append("Nombre: ").append(fsNode.getName()).append("\n");
        info.append("👤 Dueño: ").append(fsNode.getOwner()).append("\n");
        info.append("📍 Ruta: ").append(getFullPath(fsNode)).append("\n");
        info.append("📋 Tipo: ").append(fsNode.isDirectory() ? "Directorio" : "Archivo").append("\n");

        if (userSession.isAdmin()) {
            info.append("🔓 Modo Administrador: Acceso total\n");
        } else {
            info.append("🔒 Modo Usuario: SOLO LECTURA\n");
        }

        if (!fsNode.isDirectory()) {
            info.append("📦 Tamaño: ").append(fsNode.getSizeInBlocks()).append(" bloques\n");
            info.append("🔗 Primer bloque: ").append(fsNode.getFirstBlock()).append("\n");
        } else {
            int count = (fsNode.getChildren() != null) ? fsNode.getChildren().size() : 0;
            info.append("📊 Contenido: ").append(count).append(" elementos\n");
        }

        info.append("\n══════════════════════════════");
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
        
        // Actualizar cabezal y desplazamiento total
        cabezaLabel.setText("Cabezal: " + (scheduler != null ? scheduler.getCabezaActual() : 0));
        if (scheduler != null) {
            desplazamientoTotal = scheduler.getDesplazamientoTotal(); // Tomamos el valor del scheduler
        }
        desplazamientoLabel.setText("  Desplazamiento total: " + desplazamientoTotal);
        
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

    // ----- Métodos de solicitud de operaciones -----
    private void solicitarCrearDirectorio() {
        if (!userSession.isAdmin()) {
            JOptionPane.showMessageDialog(this, 
                "Modo usuario: no tiene permisos para crear directorios", 
                "Permiso denegado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JTextField nameField = new JTextField(20);
        JTextField ownerField = new JTextField("usuario1", 20);
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.add(new JLabel("Nombre:"));
        panel.add(nameField);
        panel.add(new JLabel("Dueño:"));
        panel.add(ownerField);

        FileSystemNode parentDir = getSelectedDirectory();

        if (!userSession.canCreateIn(parentDir)) {
            JOptionPane.showMessageDialog(this, 
                "No tiene permisos para crear directorios aquí", 
                "Permiso denegado", JOptionPane.ERROR_MESSAGE);
            return;
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
            if (parentDir == null) {
                JOptionPane.showMessageDialog(this, "Debe seleccionar un directorio padre", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            createDirectory(parentDir, name, owner);
            refreshTree();
        }
    }

    private void solicitarCrearArchivo() {
        if (!userSession.isAdmin()) {
            JOptionPane.showMessageDialog(this, 
                "Modo usuario: no tiene permisos para crear archivos", 
                "Permiso denegado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JTextField nameField = new JTextField(20);
        JTextField ownerField = new JTextField("usuario1", 20);
        JTextField sizeField = new JTextField("5", 10);
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.add(new JLabel("Nombre:"));
        panel.add(nameField);
        panel.add(new JLabel("Dueño:"));
        panel.add(ownerField);
        panel.add(new JLabel("Tamaño (bloques):"));
        panel.add(sizeField);

        FileSystemNode parentDir = getSelectedDirectory();

        if (!userSession.canCreateIn(parentDir)) {
            JOptionPane.showMessageDialog(this, 
                "No tiene permisos para crear archivos en este directorio", 
                "Permiso denegado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String parentPath = (parentDir != null) ? getFullPath(parentDir) : "/";
        panel.add(new JLabel(""));
        panel.add(new JLabel("Directorio actual: " + parentPath));

        int result = JOptionPane.showConfirmDialog(this, panel, "Crear archivo", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String owner = ownerField.getText().trim();
            if (name.isEmpty() || owner.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nombre y dueño no pueden estar vacíos", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (parentDir == null) {
                JOptionPane.showMessageDialog(this, "Debe seleccionar un directorio padre", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                int size = Integer.parseInt(sizeField.getText().trim());
                if (size <= 0) throw new NumberFormatException();
                
                Process p = new Process("CREATE", name, owner, size, parentDir);
                RWLock lock = lockManager.getLock(parentDir);
                ProcesoHilo hilo = new ProcesoHilo(p, parentDir, lock, scheduler, disk, lockManager, this);
                synchronized (procesosActivos) {
                    procesosActivos.add(hilo);
                }
                hilo.start();
                actualizarVistaProcesos();
                
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Tamaño debe ser un número entero positivo", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void solicitarModificarNombre() {
        if (!userSession.isAdmin()) {
            JOptionPane.showMessageDialog(this, "Modo usuario: no tiene permisos para modificar nombres", "Permiso denegado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof FileSystemNode)) {
            JOptionPane.showMessageDialog(this, "Seleccione un elemento para modificar", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        FileSystemNode fsNode = (FileSystemNode) selectedNode.getUserObject();

        if (fsNode == root) {
            JOptionPane.showMessageDialog(this, "No se puede modificar el nombre de la raíz", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String nuevoNombre = JOptionPane.showInputDialog(this,
            "Nuevo nombre para '" + fsNode.getName() + "':",
            "Modificar nombre", JOptionPane.QUESTION_MESSAGE);

        if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
            Process p = new Process("UPDATE", fsNode, userSession.getCurrentUser());
            p.setNuevoNombre(nuevoNombre.trim());
            RWLock lock = lockManager.getLock(fsNode);
            ProcesoHilo hilo = new ProcesoHilo(p, fsNode, lock, scheduler, disk, lockManager, this);
            synchronized (procesosActivos) {
                procesosActivos.add(hilo);
            }
            hilo.start();
            actualizarVistaProcesos();
        }
    }

    private void solicitarEliminar() {
        if (!userSession.isAdmin()) {
            JOptionPane.showMessageDialog(this, 
                "Modo usuario: no tiene permisos para eliminar elementos", 
                "Permiso denegado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof FileSystemNode)) {
            JOptionPane.showMessageDialog(this, "Seleccione un elemento para eliminar", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        FileSystemNode fsNode = (FileSystemNode) selectedNode.getUserObject();

        if (!userSession.canDelete(fsNode)) {
            JOptionPane.showMessageDialog(this, 
                "No tiene permisos para eliminar este elemento", 
                "Permiso denegado", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (fsNode == root) {
            JOptionPane.showMessageDialog(this, "No se puede eliminar el directorio raíz", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Está seguro de eliminar " + fsNode.getFullPath() + "?\n" +
                (fsNode.isDirectory() ? "Se eliminarán todos sus contenidos." : ""),
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