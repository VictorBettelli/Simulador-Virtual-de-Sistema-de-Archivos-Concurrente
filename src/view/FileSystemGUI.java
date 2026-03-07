package view;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import model.FileSystemNode;
import model.Disk;
import util.LinkedList;
import util.Node;

public class FileSystemGUI extends JFrame {
    private JTree fileTree;
    private DefaultTreeModel treeModel;
    private JTextArea infoArea;
    private FileSystemNode root;
    private Disk disk;
    private JPanel diskPanel;

    public FileSystemGUI() {
        disk = new Disk();
        initComponents();
        initFileSystem();
        refreshTree();
        updateDiskView();
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

        // Panel de control
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Acciones"));

        JButton btnCreateDir = new JButton("Crear Directorio");
        JButton btnCreateFile = new JButton("Crear Archivo");
        JButton btnDelete = new JButton("Eliminar");
        JButton btnRefresh = new JButton("Refrescar");

        btnCreateDir.addActionListener(e -> showCreateDialog(true));
        btnCreateFile.addActionListener(e -> showCreateDialog(false));
        btnDelete.addActionListener(e -> deleteSelected());
        btnRefresh.addActionListener(e -> refreshTree());

        controlPanel.add(btnCreateDir);
        controlPanel.add(btnCreateFile);
        controlPanel.add(btnDelete);
        controlPanel.add(btnRefresh);
        add(controlPanel, BorderLayout.SOUTH);

        // Panel de estado (temporal, luego se mejorará con modos de usuario)
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        JLabel statusLabel = new JLabel(" Modo: Administrador | Usuario actual: admin");
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.NORTH);

        setSize(900, 700);
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

    private void createDirectory(FileSystemNode parent, String name, String owner) {
        if (parent == null || !parent.isDirectory()) return;

        FileSystemNode newDir = new FileSystemNode();
        newDir.setName(name);
        newDir.setOwner(owner);
        newDir.setDirectory(true);
        newDir.setParent(parent);
        newDir.setChildren(new LinkedList<>());

        if (parent.getChildren() == null) {
            parent.setChildren(new LinkedList<>());
        }
        parent.getChildren().add(newDir);
    }

    private void createFile(FileSystemNode parent, String name, String owner, int size) {
        if (parent == null || !parent.isDirectory()) return;

        if (!disk.hayEspacio(size)) {
            JOptionPane.showMessageDialog(this,
                "No hay suficiente espacio en disco.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Color color = disk.generateUniqueColor();
        int firstBlock = disk.asignarBloques(size, color);
        if (firstBlock == -1) {
            JOptionPane.showMessageDialog(this,
                "Error al asignar bloques.",
                "Error", JOptionPane.ERROR_MESSAGE);
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

        if (parent.getChildren() == null) {
            parent.setChildren(new LinkedList<>());
        }
        parent.getChildren().add(newFile);
    }

    private FileSystemNode findNodeByPath(String path) {
        if (path.equals("/") || path.equals("")) {
            return root;
        }

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
            } else {
                return null;
            }
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
        for (int i = 0; i < fileTree.getRowCount(); i++) {
            fileTree.expandRow(i);
        }
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

        if (fsNode.isDirectory()) {
            info.append("📁 ");
        } else {
            info.append("📄 ");
        }

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
        if (parentPath.equals("/")) {
            return parentPath + node.getName();
        } else {
            return parentPath + "/" + node.getName();
        }
    }

    private FileSystemNode getSelectedDirectory() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
        if (selectedNode == null) {
            return root;
        }

        Object userObj = selectedNode.getUserObject();
        if (userObj instanceof FileSystemNode) {
            FileSystemNode node = (FileSystemNode) userObj;
            if (!node.isDirectory()) {
                return node.getParent();
            }
            return node;
        }
        return root;
    }

    private void showCreateDialog(boolean isDirectory) {
        String type = isDirectory ? "directorio" : "archivo";
        JTextField nameField = new JTextField(20);
        JTextField ownerField = new JTextField("usuario1", 20);
        JTextField sizeField = new JTextField("5", 10);

        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.add(new JLabel("Nombre:"));
        panel.add(nameField);
        panel.add(new JLabel("Dueño:"));
        panel.add(ownerField);

        if (!isDirectory) {
            panel.add(new JLabel("Tamaño (bloques):"));
            panel.add(sizeField);
        }

        FileSystemNode parentDir = getSelectedDirectory();
        String parentPath = (parentDir != null) ? getFullPath(parentDir) : "/";
        panel.add(new JLabel(""));
        panel.add(new JLabel("Directorio actual: " + parentPath));

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Crear " + type, JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String owner = ownerField.getText().trim();

            if (name.isEmpty() || owner.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Nombre y dueño no pueden estar vacíos",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (parentDir == null) {
                JOptionPane.showMessageDialog(this,
                        "Debe seleccionar un directorio padre",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (isDirectory) {
                createDirectory(parentDir, name, owner);
            } else {
                try {
                    int size = Integer.parseInt(sizeField.getText().trim());
                    if (size <= 0) throw new NumberFormatException();
                    createFile(parentDir, name, owner, size);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                            "Tamaño debe ser un número entero positivo",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            refreshTree();
            updateDiskView();
        }
    }

    private void deleteSelected() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();

        if (selectedNode == null || !(selectedNode.getUserObject() instanceof FileSystemNode)) {
            JOptionPane.showMessageDialog(this,
                    "Seleccione un elemento para eliminar",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        FileSystemNode fsNode = (FileSystemNode) selectedNode.getUserObject();

        if (fsNode == root) {
            JOptionPane.showMessageDialog(this,
                    "No se puede eliminar el directorio raíz",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Está seguro de eliminar " + fsNode.getFullPath() + "?\n" +
                        (fsNode.isDirectory() ? "Se eliminarán todos sus contenidos." : ""),
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (!fsNode.isDirectory()) {
                disk.liberarBloques(fsNode.getFirstBlock(), fsNode.getColor());
            } else {
                liberarBloquesRecursivo(fsNode);
            }

            FileSystemNode parent = fsNode.getParent();
            if (parent != null && parent.getChildren() != null) {
                parent.getChildren().remove(fsNode);
                refreshTree();
                updateDiskView();
                JOptionPane.showMessageDialog(this,
                        "Elemento eliminado correctamente",
                        "Éxito", JOptionPane.INFORMATION_MESSAGE);
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