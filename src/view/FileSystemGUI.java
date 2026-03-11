package view;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import model.FileSystemNode;
import model.Disk;
import model.Process;
import model.Scheduler;
import util.LinkedList;
import util.Node;
import config.UserSession;
import java.io.File;
import test.TestCase;
import test.TestCaseLoader;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import persistence.FileSystemPersistence;

public class FileSystemGUI extends JFrame {
    private JTree fileTree;
    private DefaultTreeModel treeModel;
    private JTextArea infoArea;
    private FileSystemNode root;
    private Disk disk;
    private JPanel diskPanel;
    
    // Atributos para planificación
    private Scheduler scheduler;
    private DefaultListModel<Process> procesosModel;
    private JList<Process> procesosList;
    private JComboBox<String> politicaCombo;
    private JLabel cabezaLabel;
    private JButton btnEjecutar;
    private JTextArea logArea; // Área de log para movimientos
    private int desplazamientoTotal;
    private JLabel desplazamientoLabel;
    private JSpinner cabezaSpinner;
    private JButton btnSetCabeza;
    
    // Atributos para modo usuario
    private UserSession userSession;           
    private JComboBox<String> userModeCombo;   
    private JComboBox<String> userSelectorCombo;
    private JLabel currentModeLabel;           
    
    public FileSystemGUI() {
        userSession = new UserSession();      
        disk = new Disk();
        scheduler = new Scheduler(disk);
        desplazamientoTotal = 0;
        initComponents();
        initFileSystem();
        refreshTree();
        updateDiskView();
        actualizarVistaProcesos();
       
        actualizarPermisosInterfaz();
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

    // --- RENDERIZADOR PARA DIFERENCIAR DIRECTORIOS Y ARCHIVOS ---
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
    // -----------------------------------------------------------

    JScrollPane treeScroll = new JScrollPane(fileTree);
    treePanel.add(treeScroll, BorderLayout.CENTER);

    // Panel derecho con información
    JPanel infoPanel = new JPanel(new BorderLayout());
    infoPanel.setBorder(BorderFactory.createTitledBorder("Información del elemento"));
    infoArea = new JTextArea(10, 25);
    infoArea.setEditable(false);
    infoArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    infoPanel.add(new JScrollPane(infoArea), BorderLayout.CENTER);

    // Panel para el disco
    JPanel diskViewPanel = new JPanel(new BorderLayout());
    diskViewPanel.setBorder(BorderFactory.createTitledBorder("Disco (bloques)"));
    diskPanel = new JPanel() {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            drawDisk(g);
        }
    };
    diskPanel.setPreferredSize(new Dimension(300, 300));
    diskPanel.setBackground(Color.WHITE);
    diskViewPanel.add(new JScrollPane(diskPanel), BorderLayout.CENTER);

    // Panel combinado derecho
    JPanel rightPanel = new JPanel(new BorderLayout());
    rightPanel.add(infoPanel, BorderLayout.CENTER);
    rightPanel.add(diskViewPanel, BorderLayout.SOUTH);

    // Split horizontal
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, rightPanel);
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

    // Panel de planificación (scheduler)
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
    });
    topSchedulerPanel.add(politicaCombo);

    cabezaLabel = new JLabel("Cabezal: " + scheduler.getCabezaActual());
    topSchedulerPanel.add(cabezaLabel);

    topSchedulerPanel.add(new JLabel("  Pos. inicial:"));
    SpinnerNumberModel spinnerModel = new SpinnerNumberModel(0, 0, Disk.SIZE - 1, 1);
    cabezaSpinner = new JSpinner(spinnerModel);
    cabezaSpinner.setPreferredSize(new Dimension(60, 25));
    topSchedulerPanel.add(cabezaSpinner);

    btnSetCabeza = new JButton("Establecer");
    btnSetCabeza.addActionListener(e -> {
        int nuevaPos = (int) cabezaSpinner.getValue();
        scheduler.setCabezaActual(nuevaPos);
        cabezaLabel.setText("Cabezal: " + scheduler.getCabezaActual());
        desplazamientoTotal = 0;
        actualizarDesplazamiento();
        actualizarVistaProcesos();
    });
    topSchedulerPanel.add(btnSetCabeza);

    desplazamientoLabel = new JLabel("  Desplazamiento total: 0");
    topSchedulerPanel.add(desplazamientoLabel);

    btnEjecutar = new JButton("Ejecutar siguiente");
    btnEjecutar.addActionListener(e -> ejecutarSiguienteProceso());
    topSchedulerPanel.add(btnEjecutar);

    // Panel de cola de procesos con círculo de color
    procesosModel = new DefaultListModel<>();
    procesosList = new JList<>(procesosModel);
    procesosList.setCellRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            panel.setOpaque(true);
            if (value instanceof Process) {
                Process p = (Process) value;
                FileSystemNode archivo = p.getArchivo();

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

                int bloque = scheduler.getBloqueSolicitado(p);
                int distancia = Math.abs(bloque - scheduler.getCabezaActual());
                String texto = p.toString() + " | Bloque: " + (bloque == -1 ? "N/A" : bloque) + " | Dist: " + (bloque == -1 ? "-" : distancia);
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
    });
    JScrollPane scrollProcesos = new JScrollPane(procesosList);
    scrollProcesos.setPreferredSize(new Dimension(400, 100));

    // Área de log
    logArea = new JTextArea(8, 50);
    logArea.setEditable(false);
    logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
    JScrollPane scrollLog = new JScrollPane(logArea);
    scrollLog.setBorder(BorderFactory.createTitledBorder("Log de movimientos"));

    JPanel centerSchedulerPanel = new JPanel(new BorderLayout());
    centerSchedulerPanel.add(scrollProcesos, BorderLayout.CENTER);
    centerSchedulerPanel.add(scrollLog, BorderLayout.SOUTH);

    schedulerPanel.add(topSchedulerPanel, BorderLayout.NORTH);
    schedulerPanel.add(centerSchedulerPanel, BorderLayout.CENTER);

    // Panel sur completo
    JPanel southPanel = new JPanel(new BorderLayout());
    southPanel.add(controlPanel, BorderLayout.NORTH);
    southPanel.add(schedulerPanel, BorderLayout.CENTER);

    add(southPanel, BorderLayout.SOUTH);

    setSize(900, 850);
    setLocationRelativeTo(null);
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
        
        // Mostrar mensaje de cambio
        System.out.println("Modo cambiado a: " + userSession.getModeDisplay());
    }
    
    private void cambiarUsuario() {
        if (userModeCombo.getSelectedIndex() == 1) { // Solo en modo usuario
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

        // Buscar el panel de acciones
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

                                    // Botones de escritura solo para admin
                                    if (text.equals("Crear Directorio") || 
                                        text.equals("Crear Archivo") || 
                                        text.equals("Modificar Nombre") || // <-- NUEVO
                                        text.equals("Eliminar")) {
                                        button.setEnabled(isAdmin);
                                    }
                                    // Botón Refrescar siempre habilitado
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

        // Botones de planificación siempre habilitados
        if (btnEjecutar != null) btnEjecutar.setEnabled(true);
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
            FileSystemPersistence.guardarTodo(root, disk, scheduler.getProcesosTodos());
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
            // Limpiar estructura actual
            root = new FileSystemNode();
            root.setName("/");
            root.setOwner("admin");
            root.setDirectory(true);
            root.setParent(null);
            root.setChildren(new LinkedList<>());
            
            // Cargar desde JSON
            FileSystemPersistence.cargarTodo(root, disk, scheduler.getProcesosTodos());
            
            // Actualizar vistas
            refreshTree();
            updateDiskView();
            actualizarVistaProcesos();
            
            JOptionPane.showMessageDialog(this, 
                "Estado cargado exitosamente", 
                "Éxito", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void drawDisk(Graphics g) {
        int cols = 16;
        int blockSize = 18;
        Disk.Block[] blocks = disk.getBlocks();
        for (int i = 0; i < Disk.SIZE; i++) {
            int x = (i % cols) * blockSize;
            int y = (i / cols) * blockSize;
            if (blocks[i].isLibre()) {
                g.setColor(Color.LIGHT_GRAY);
            } else {
                Color c = blocks[i].getColor();
                g.setColor(c != null ? c : Color.GREEN);
            }
            g.fillRect(x, y, blockSize - 1, blockSize - 1);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, blockSize - 1, blockSize - 1);
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
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos JSON", "json"));

        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File archivo = fileChooser.getSelectedFile();

            try {
                // Cargar el caso de prueba
                TestCase testCase = test.TestCaseLoader.cargarTestCase(archivo.getAbsolutePath());

                if (testCase != null) {
                    // Limpiar sistema actual
                    reiniciarSistema();

                    // Aplicar el caso de prueba
                    test.TestCaseLoader.aplicarTestCase(testCase, root, disk, scheduler);

                    // Actualizar vistas
                    refreshTree();
                    updateDiskView();
                    actualizarVistaProcesos();

                    // Registrar en log
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
        // Limpiar disco
        disk = new Disk();

        // Limpiar estructura de archivos
        root = new FileSystemNode();
        root.setName("/");
        root.setOwner("admin");
        root.setDirectory(true);
        root.setParent(null);
        root.setChildren(new LinkedList<>());

        // Limpiar scheduler
        scheduler = new Scheduler(disk);

        // Limpiar log
        logArea.setText("");
    }

    // Método para crear directorio (ejecución directa)
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

    // Método original createFile (se usa desde ejecutarCrear)
    private void createFile(FileSystemNode parent, String name, String owner, int size) {
        if (parent == null || !parent.isDirectory()) return;
        if (!disk.hayEspacio(size)) {
            JOptionPane.showMessageDialog(this, "No hay suficiente espacio en disco.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Color color = disk.generateUniqueColor();
        int firstBlock = disk.asignarBloques(size, color);
        if (firstBlock == -1) {
            JOptionPane.showMessageDialog(this, "Error al asignar bloques.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        FileSystemNode newFile = new FileSystemNode();
        newFile.setName(name);
        newFile.setOwner(owner);
        newFile.setDirectory(false);
        newFile.setSizeInBlocks(size);
        newFile.setFirstBlock(firstBlock);
        newFile.setColor(color);
        newFile.setParent(parent);
        if (parent.getChildren() == null) parent.setChildren(new LinkedList<>());
        parent.getChildren().add(newFile);
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
        // En modo usuario: puede ver todo (solo lectura)
        // En modo admin: también ve todo
        
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

        // Mostrar permisos claramente
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

    // ----- Métodos para planificación -----

    private void actualizarVistaProcesos() {
        procesosModel.clear();
        LinkedList<Process> cola = scheduler.getColaProcesos();
        if (cola != null && cola.getHead() != null) {
            Node<Process> current = cola.getHead();
            while (current != null) {
                procesosModel.addElement(current.data);
                current = current.next;
            }
        }
        cabezaLabel.setText("Cabezal: " + scheduler.getCabezaActual());
    }

    private void ejecutarSiguienteProceso() {
        int cabezaAntes = scheduler.getCabezaActual();
        Process p = scheduler.ejecutarSiguiente();
        if (p == null) {
            JOptionPane.showMessageDialog(this, "No hay procesos en la cola.");
            return;
        }
        
        // Registrar movimiento antes de ejecutar
        int bloque = scheduler.getBloqueSolicitado(p);
        if (bloque != -1) {
            int distancia = Math.abs(bloque - cabezaAntes);
            logArea.append("Cabezal: " + cabezaAntes + " → " + bloque + " (distancia " + distancia + ") para " + p + "\n");
        } else {
            logArea.append("Ejecutando " + p + " (sin bloque específico)\n");
        }
        
        boolean exito = false;
        try {
           switch (p.getOperacion()) {
              case "READ":    exito = ejecutarLeer(p); break;
              case "UPDATE":  exito = ejecutarActualizar(p); break;
              case "DELETE":  exito = ejecutarEliminar(p); break;
              case "CREATE":  exito = ejecutarCrear(p); break;
            default:
                JOptionPane.showMessageDialog(this, "Operación desconocida: " + p.getOperacion());
}
            p.setEstado(exito ? "TERMINADO" : "ERROR");
        } catch (Exception e) {
            p.setEstado("ERROR");
            e.printStackTrace();
        }
        
        refreshTree();
        updateDiskView();
        actualizarVistaProcesos();
        
        // Hacer scroll al final del log
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private boolean ejecutarCrear(Process p) {
    // Para CREATE, los datos están en getNombreArchivo() y getPadre()
    String nombre = p.getNombreArchivo();
    String owner = p.getOwner();
    int tamanio = p.getTamanio();
    FileSystemNode padre = p.getPadre();

    if (padre == null) {
        System.err.println("Error: padre nulo en ejecutarCrear");
        return false;
    }
    if (!disk.hayEspacio(tamanio)) {
        JOptionPane.showMessageDialog(this, "No hay suficiente espacio en disco.", "Error", JOptionPane.ERROR_MESSAGE);
        return false;
    }

    Color color = disk.generateUniqueColor();
    int firstBlock = disk.asignarBloques(tamanio, color);
    if (firstBlock == -1) {
        JOptionPane.showMessageDialog(this, "Error al asignar bloques.", "Error", JOptionPane.ERROR_MESSAGE);
        return false;
    }

    FileSystemNode newFile = new FileSystemNode();
    newFile.setName(nombre);
    newFile.setOwner(owner);
    newFile.setDirectory(false);
    newFile.setSizeInBlocks(tamanio);
    newFile.setFirstBlock(firstBlock);
    newFile.setColor(color);
    newFile.setParent(padre);

    if (padre.getChildren() == null) padre.setChildren(new LinkedList<>());
    padre.getChildren().add(newFile);

    scheduler.setCabezaActual(firstBlock);
    return true;
}

    private boolean ejecutarEliminar(Process p) {
    FileSystemNode nodo = p.getArchivo();
    if (nodo == null || nodo == root) {
        System.err.println("Error: nodo nulo o es la raíz");
        return false;
    }

    // Liberar bloques en disco
    if (!nodo.isDirectory()) {
        disk.liberarBloques(nodo.getFirstBlock(), nodo.getColor());
    } else {
        liberarBloquesRecursivo(nodo);
    }

    FileSystemNode parent = nodo.getParent();
    if (parent == null) {
        System.err.println("Error: el nodo no tiene padre");
        return false;
    }
    if (parent.getChildren() == null) {
        System.err.println("Error: el padre no tiene lista de hijos");
        return false;
    }

    // Eliminar de la lista de hijos usando comparación por referencia (no confiar en equals)
    LinkedList<FileSystemNode> hijos = parent.getChildren();
    LinkedList<FileSystemNode> nuevosHijos = new LinkedList<>();
    Node<FileSystemNode> current = hijos.getHead();
    while (current != null) {
        if (current.data != nodo) {  // Comparación por referencia
            nuevosHijos.add(current.data);
        }
        current = current.next;
    }
    parent.setChildren(nuevosHijos);  // Reemplazar la lista

    // Actualizar cabezal (opcional, para mantener coherencia)
    if (!nodo.isDirectory() && nodo.getFirstBlock() != -1) {
        scheduler.setCabezaActual(nodo.getFirstBlock());
    }

    return true;
}

    private boolean ejecutarLeer(Process p) {
        FileSystemNode archivo = p.getArchivo();
        if (archivo == null || archivo.isDirectory()) return false;
        System.out.println("Leyendo archivo: " + archivo.getFullPath());
        int bloque = p.getBloqueSolicitado() != -1 ? p.getBloqueSolicitado() : archivo.getFirstBlock();
        scheduler.setCabezaActual(bloque);
        return true;
    }

    private boolean ejecutarActualizar(Process p) {
    FileSystemNode archivo = p.getArchivo();
    if (archivo == null || archivo.isDirectory()) return false;
    
    String nuevoNombre = p.getNuevoNombre();
    if (nuevoNombre == null || nuevoNombre.isEmpty()) return false;
    
    // Cambiar el nombre
    archivo.setName(nuevoNombre);
    
    // Mover el cabezal al primer bloque del archivo
    scheduler.setCabezaActual(archivo.getFirstBlock());
    
    return true;
}

    // Métodos para solicitar operaciones
    private void solicitarCrearDirectorio() {
        // VERIFICACIÓN DE PERMISOS - MODO USUARIO
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

        // VERIFICACIÓN ADICIONAL - Permisos en el directorio padre
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
        // Crear proceso UPDATE
        Process p = new Process("UPDATE", fsNode, userSession.getCurrentUser());
        p.setNuevoNombre(nuevoNombre.trim());
        scheduler.agregarProceso(p);
        actualizarVistaProcesos();
    }
}

   private void solicitarCrearArchivo() {
    // VERIFICACIÓN DE PERMISOS - MODO USUARIO
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
            // CAMBIO: "CREAR" → "CREATE"
            Process p = new Process("CREATE", name, owner, size, parentDir);
            scheduler.agregarProceso(p);
            actualizarVistaProcesos();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Tamaño debe ser un número entero positivo", "Error", JOptionPane.ERROR_MESSAGE);
        }
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
        // CAMBIO: "ELIMINAR" → "DELETE"
        Process p = new Process("DELETE", fsNode, "admin");
        scheduler.agregarProceso(p);
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
    private void actualizarDesplazamiento() {
    desplazamientoLabel.setText("  Desplazamiento total: " + desplazamientoTotal);
}
}
