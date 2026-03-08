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
    
    public FileSystemGUI() {
        disk = new Disk();
        scheduler = new Scheduler(disk);
        desplazamientoTotal = 0;  
        initComponents();
        initFileSystem();
        refreshTree();
        updateDiskView();
        actualizarVistaProcesos();
        agregarProcesosPrueba();
        
    }

   private void initComponents() {
    setTitle("Simulador de Sistema de Archivos");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLayout(new BorderLayout());

    // Panel izquierdo con el árbol
    JPanel treePanel = new JPanel(new BorderLayout());
    treePanel.setBorder(BorderFactory.createTitledBorder("Estructura de Archivos"));
    treePanel.setPreferredSize(new Dimension(300, 500));

    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Raíz (/)");
    treeModel = new DefaultTreeModel(rootNode);
    fileTree = new JTree(treeModel);
    fileTree.addTreeSelectionListener(e -> showFileInfo());

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
    JButton btnDelete = new JButton("Eliminar");
    JButton btnRefresh = new JButton("Refrescar");

    btnCreateDir.addActionListener(e -> solicitarCrearDirectorio());
    btnCreateFile.addActionListener(e -> solicitarCrearArchivo());
    btnDelete.addActionListener(e -> solicitarEliminar());
    btnRefresh.addActionListener(e -> refreshTree());

    controlPanel.add(btnCreateDir);
    controlPanel.add(btnCreateFile);
    controlPanel.add(btnDelete);
    controlPanel.add(btnRefresh);

    // Panel de planificación (scheduler)
    JPanel schedulerPanel = new JPanel(new BorderLayout());
    schedulerPanel.setBorder(BorderFactory.createTitledBorder("Planificación de Disco"));

    // Panel superior del scheduler: selector de política y posición del cabezal
    JPanel topSchedulerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    topSchedulerPanel.add(new JLabel("Política:"));
    politicaCombo = new JComboBox<>(new String[]{"FIFO", "SSTF", "SCAN", "C-SCAN"});
    politicaCombo.addActionListener(e -> {
        int politica = politicaCombo.getSelectedIndex();
        scheduler.setPolitica(politica);
        actualizarVistaProcesos(); // Refrescar distancias al cambiar política
    });
    topSchedulerPanel.add(politicaCombo);
    
    cabezaLabel = new JLabel("Cabezal: " + scheduler.getCabezaActual());
    topSchedulerPanel.add(cabezaLabel);
    
    // Selector de posición inicial y desplazamiento total
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
        desplazamientoTotal = 0; // Reiniciamos el contador al cambiar manualmente
        actualizarDesplazamiento();
        actualizarVistaProcesos();
    });
    topSchedulerPanel.add(btnSetCabeza);
    
    desplazamientoLabel = new JLabel("  Desplazamiento total: 0");
    topSchedulerPanel.add(desplazamientoLabel);
    
    btnEjecutar = new JButton("Ejecutar siguiente");
    btnEjecutar.addActionListener(e -> ejecutarSiguienteProceso());
    topSchedulerPanel.add(btnEjecutar);

    // Panel de cola de procesos con renderizado personalizado (con círculo de color)
    procesosModel = new DefaultListModel<>();
    procesosList = new JList<>(procesosModel);
    procesosList.setCellRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            // Usamos un panel para combinar círculo y texto
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
                
                // Texto con la información
                int bloque = scheduler.getBloqueSolicitado(p);
                int distancia = Math.abs(bloque - scheduler.getCabezaActual());
                String texto = p.toString() + " | Bloque: " + (bloque == -1 ? "N/A" : bloque) + " | Dist: " + (bloque == -1 ? "-" : distancia);
                JLabel textLabel = new JLabel(texto);
                panel.add(textLabel);
                
                // Colores de selección
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

    // Área de log para movimientos
    logArea = new JTextArea(8, 50);
    logArea.setEditable(false);
    logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
    JScrollPane scrollLog = new JScrollPane(logArea);
    scrollLog.setBorder(BorderFactory.createTitledBorder("Log de movimientos"));

    // Panel central del scheduler: cola y log
    JPanel centerSchedulerPanel = new JPanel(new BorderLayout());
    centerSchedulerPanel.add(scrollProcesos, BorderLayout.CENTER);
    centerSchedulerPanel.add(scrollLog, BorderLayout.SOUTH);

    schedulerPanel.add(topSchedulerPanel, BorderLayout.NORTH);
    schedulerPanel.add(centerSchedulerPanel, BorderLayout.CENTER);

    // Panel sur completo (control + scheduler)
    JPanel southPanel = new JPanel(new BorderLayout());
    southPanel.add(controlPanel, BorderLayout.NORTH);
    southPanel.add(schedulerPanel, BorderLayout.CENTER);

    add(southPanel, BorderLayout.SOUTH);

    // Panel de estado (modo usuario)
    JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    statusPanel.setBorder(BorderFactory.createEtchedBorder());
    JLabel statusLabel = new JLabel(" Modo: Administrador | Usuario actual: admin");
    statusPanel.add(statusLabel);
    add(statusPanel, BorderLayout.NORTH);

    setSize(900, 850); // Ajustar altura
    setLocationRelativeTo(null);
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
    
    private void agregarProcesosPrueba() {
    int[] bloques = {95, 180, 34, 119, 11, 123, 62, 64};
    for (int i = 0; i < bloques.length; i++) {
        FileSystemNode temp = new FileSystemNode();
        temp.setName("test" + i);
        temp.setDirectory(false);
        Process p = new Process("LEER", temp, "admin");
        p.setBloqueSolicitado(bloques[i]);
        scheduler.agregarProceso(p);
    }
    scheduler.setCabezaActual(50);
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
                case "CREAR":
                    exito = ejecutarCrear(p);
                    break;
                case "ELIMINAR":
                    exito = ejecutarEliminar(p);
                    break;
                case "LEER":
                    exito = ejecutarLeer(p);
                    break;
                case "ACTUALIZAR":
                    exito = ejecutarActualizar(p);
                    break;
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
        FileSystemNode archivo = p.getArchivo();
        String nombre = archivo.getName();
        String owner = p.getOwner();
        int tamano = p.getTamano();
        FileSystemNode padre = archivo.getParent();
        if (padre == null) return false;
        
        if (!disk.hayEspacio(tamano)) return false;
        
        Color color = disk.generateUniqueColor();
        int firstBlock = disk.asignarBloques(tamano, color);
        if (firstBlock == -1) return false;
        
        FileSystemNode newFile = new FileSystemNode();
        newFile.setName(nombre);
        newFile.setOwner(owner);
        newFile.setDirectory(false);
        newFile.setSizeInBlocks(tamano);
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
        if (nodo == null || nodo == root) return false;
        
        if (!nodo.isDirectory()) {
            disk.liberarBloques(nodo.getFirstBlock(), nodo.getColor());
        } else {
            liberarBloquesRecursivo(nodo);
        }
        
        FileSystemNode parent = nodo.getParent();
        if (parent != null && parent.getChildren() != null) {
            parent.getChildren().remove(nodo);
            if (!nodo.isDirectory() && nodo.getFirstBlock() != -1) {
                scheduler.setCabezaActual(nodo.getFirstBlock());
            }
            return true;
        }
        return false;
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
        System.out.println("Actualizando archivo: " + archivo.getFullPath());
        scheduler.setCabezaActual(archivo.getFirstBlock());
        return true;
    }

    // Métodos para solicitar operaciones
    private void solicitarCrearDirectorio() {
        JTextField nameField = new JTextField(20);
        JTextField ownerField = new JTextField("usuario1", 20);
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.add(new JLabel("Nombre:"));
        panel.add(nameField);
        panel.add(new JLabel("Dueño:"));
        panel.add(ownerField);
        FileSystemNode parentDir = getSelectedDirectory();
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
                Process p = new Process("CREAR", name, owner, size, parentDir);
                scheduler.agregarProceso(p);
                actualizarVistaProcesos();
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Tamaño debe ser un número entero positivo", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void solicitarEliminar() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof FileSystemNode)) {
            JOptionPane.showMessageDialog(this, "Seleccione un elemento para eliminar", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        FileSystemNode fsNode = (FileSystemNode) selectedNode.getUserObject();
        if (fsNode == root) {
            JOptionPane.showMessageDialog(this, "No se puede eliminar el directorio raíz", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Está seguro de eliminar " + fsNode.getFullPath() + "?\n" +
                (fsNode.isDirectory() ? "Se eliminarán todos sus contenidos." : ""),
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            Process p = new Process("ELIMINAR", fsNode, "admin");
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
