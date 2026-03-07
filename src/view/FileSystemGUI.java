/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package view;

/**
 *
 * @author luisf
 */
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import model.FileSystemNode;
import util.LinkedList;
import util.Node;

public class FileSystemGUI extends JFrame {
    private JTree fileTree;
    private DefaultTreeModel treeModel;
    private JTextArea infoArea;
    private FileSystemNode root;
    
    public FileSystemGUI() {
        initComponents();
        initFileSystem();
        refreshTree();
    }
    
    private void initComponents() {
        setTitle("Simulador de Sistema de Archivos");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Panel izquierdo con el árbol
        JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.setBorder(BorderFactory.createTitledBorder("Estructura de Archivos"));
        treePanel.setPreferredSize(new Dimension(300, 500));
        
        // Crear nodo raíz para el JTree
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
        
        // Agregar paneles al frame
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, infoPanel);
        splitPane.setDividerLocation(300);
        add(splitPane, BorderLayout.CENTER);
        
        // Panel de control
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Acciones"));
        
        JButton btnCreateDir = new JButton("Crear Directorio");
        JButton btnCreateFile = new JButton("Crear Archivo");
        JButton btnDelete = new JButton("Eliminar");
        JButton btnRefresh = new JButton("Refrescar");
        
        // Listeners
        btnCreateDir.addActionListener(e -> showCreateDialog(true));
        btnCreateFile.addActionListener(e -> showCreateDialog(false));
        btnDelete.addActionListener(e -> deleteSelected());
        btnRefresh.addActionListener(e -> refreshTree());
        
        controlPanel.add(btnCreateDir);
        controlPanel.add(btnCreateFile);
        controlPanel.add(btnDelete);
        controlPanel.add(btnRefresh);
        add(controlPanel, BorderLayout.SOUTH);
        
        // Panel de estado
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        JLabel statusLabel = new JLabel(" Modo: Administrador | Usuario actual: admin");
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.NORTH);
        
        setSize(800, 600);
        setLocationRelativeTo(null);
    }
    
    private void initFileSystem() {
        // Inicializar sistema de archivos con directorio raíz
        root = new FileSystemNode();
        root.setName("/");
        root.setOwner("admin");
        root.setDirectory(true);
        root.setParent(null);
        root.setChildren(new LinkedList<>());

        System.out.println("Creando estructura de archivos...");

        // Crear estructura inicial
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

        // Crear usuario1 en home
        FileSystemNode user1 = new FileSystemNode();
        user1.setName("usuario1");
        user1.setOwner("usuario1");
        user1.setDirectory(true);
        user1.setParent(home);
        user1.setChildren(new LinkedList<>());
        home.getChildren().add(user1);

        // Crear usuario2 en home
        FileSystemNode user2 = new FileSystemNode();
        user2.setName("usuario2");
        user2.setOwner("usuario2");
        user2.setDirectory(true);
        user2.setParent(home);
        user2.setChildren(new LinkedList<>());
        home.getChildren().add(user2);

        // Archivos para usuario1
        FileSystemNode doc1 = new FileSystemNode();
        doc1.setName("documento.txt");
        doc1.setOwner("usuario1");
        doc1.setDirectory(false);
        doc1.setSizeInBlocks(5);
        doc1.setFirstBlock(10);
        doc1.setParent(user1);
        user1.getChildren().add(doc1);

        FileSystemNode foto = new FileSystemNode();
        foto.setName("foto.jpg");
        foto.setOwner("usuario1");
        foto.setDirectory(false);
        foto.setSizeInBlocks(8);
        foto.setFirstBlock(15);
        foto.setParent(user1);
        user1.getChildren().add(foto);

        // Archivos para usuario2
        FileSystemNode notas = new FileSystemNode();
        notas.setName("notas.txt");
        notas.setOwner("usuario2");
        notas.setDirectory(false);
        notas.setSizeInBlocks(3);
        notas.setFirstBlock(23);
        notas.setParent(user2);
        user2.getChildren().add(notas);

        // Archivo en etc
        FileSystemNode config = new FileSystemNode();
        config.setName("config.conf");
        config.setOwner("admin");
        config.setDirectory(false);
        config.setSizeInBlocks(2);
        config.setFirstBlock(30);
        config.setParent(etc);
        etc.getChildren().add(config);

        System.out.println("Estructura creada. Root hijos: " + root.getChildren().size());
    }

    // Método para crear directorio (recibe FileSystemNode, no String)
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

        System.out.println("Directorio creado: " + name + " en " + parent.getName());
    }

        // Método para crear archivo (recibe FileSystemNode, no String)
    private void createFile(FileSystemNode parent, String name, String owner, int size, int firstBlock) {
        if (parent == null || !parent.isDirectory()) return;

        FileSystemNode newFile = new FileSystemNode();
        newFile.setName(name);
        newFile.setOwner(owner);
        newFile.setDirectory(false);
        newFile.setSizeInBlocks(size);
        newFile.setFirstBlock(firstBlock);
        newFile.setParent(parent);

        if (parent.getChildren() == null) {
            parent.setChildren(new LinkedList<>());
        }
        parent.getChildren().add(newFile);

        System.out.println("Archivo creado: " + name + " (" + size + " bloques) en " + parent.getName());
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
                Node<FileSystemNode> childNode = current.getChildren().getHead(); // CORREGIDO
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
        // Crear nodo con el NOMBRE como texto visible
        DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(fsNode.getName());

        // Guardar el objeto COMPLETO como user object para poder recuperarlo después
        childNode.setUserObject(fsNode);

        // Agregar al padre
        treeNode.add(childNode);

        // Si es directorio y tiene hijos, procesarlos recursivamente
        if (fsNode.isDirectory() && fsNode.getChildren() != null && fsNode.getChildren().size() > 0) {
            Node<FileSystemNode> current = fsNode.getChildren().getHead();
            while (current != null) {
                buildTreeNodes(childNode, current.data);
                current = current.next;
            }
        }
    }

    // Método refreshTree CORREGIDO
    private void refreshTree() {
        // Crear nodo raíz del JTree con nombre visible
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Raíz (/)");

        // Guardar el objeto root como user object
        rootNode.setUserObject(root);

        // Limpiar hijos existentes
        rootNode.removeAllChildren();

        // Construir el árbol desde nuestra estructura
        if (root.getChildren() != null) {
            Node<FileSystemNode> current = root.getChildren().getHead();
            while (current != null) {
                buildTreeNodes(rootNode, current.data);
                current = current.next;
            }
        }

        // Actualizar el modelo del árbol
        treeModel.setRoot(rootNode);
        treeModel.reload();

        // Expandir todo para ver la estructura
        expandAllNodes();
    }
    
    private void expandAllNodes() {
        for (int i = 0; i < fileTree.getRowCount(); i++) {
            fileTree.expandRow(i);
        }
    }
    
    // Método showFileInfo CORREGIDO
    private void showFileInfo() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) 
            fileTree.getLastSelectedPathComponent();

        if (selectedNode == null) return;

        // Recuperar el objeto FileSystemNode del user object
        Object userObj = selectedNode.getUserObject();

        // Si no hay user object o no es FileSystemNode, no podemos mostrar info
        if (!(userObj instanceof FileSystemNode)) {
            infoArea.setText("No hay información disponible para este elemento");
            return;
        }

        FileSystemNode fsNode = (FileSystemNode) userObj;

        StringBuilder info = new StringBuilder();
        info.append("══════════════════════════════\n");
        info.append("  INFORMACIÓN DEL ELEMENTO\n");
        info.append("══════════════════════════════\n\n");

        // Mostrar icono según tipo
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

    // Método auxiliar para obtener ruta completa
    private String getFullPath(FileSystemNode node) {
        if (node == null) return "";
        if (node.getParent() == null) return node.getName();

        // Construir ruta recursivamente
        String parentPath = getFullPath(node.getParent());
        if (parentPath.equals("/")) {
            return parentPath + node.getName();
        } else {
            return parentPath + "/" + node.getName();
        }
    }

    // Método auxiliar para obtener el directorio seleccionado
    private FileSystemNode getSelectedDirectory() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) 
            fileTree.getLastSelectedPathComponent();

        if (selectedNode == null) {
            // Si no hay selección, usar la raíz
            return root;
        }

        Object userObj = selectedNode.getUserObject();

        if (userObj instanceof FileSystemNode) {
            FileSystemNode node = (FileSystemNode) userObj;
            // Si es un archivo, usar su padre
            if (!node.isDirectory()) {
                return node.getParent();
            }
            // Si es directorio, usarlo directamente
            return node;
        }

        // Por defecto, usar la raíz
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

        // Obtener el directorio padre seleccionado (como objeto FileSystemNode, NO como String)
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
                // Crear directorio - pasamos el objeto FileSystemNode, no el String
                createDirectory(parentDir, name, owner);
            } else {
                try {
                    int size = Integer.parseInt(sizeField.getText().trim());
                    if (size <= 0) {
                        throw new NumberFormatException();
                    }
                    // Por ahora asignamos un primer bloque aleatorio
                    int firstBlock = (int)(Math.random() * 100) + 1;
                    // Crear archivo - pasamos el objeto FileSystemNode, no el String
                    createFile(parentDir, name, owner, size, firstBlock);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this, 
                        "Tamaño debe ser un número entero positivo", 
                        "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            refreshTree();
        }
    }
    
    private void deleteSelected() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) 
            fileTree.getLastSelectedPathComponent();
        
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
            FileSystemNode parent = fsNode.getParent();
            if (parent != null && parent.getChildren() != null) {
                parent.getChildren().remove(fsNode);
                refreshTree();
                
                JOptionPane.showMessageDialog(this, 
                    "Elemento eliminado correctamente", 
                    "Éxito", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }
    
    
    public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
        try {
            // Establecer el Look and Feel del sistema
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Crear y mostrar la ventana
        FileSystemGUI gui = new FileSystemGUI();
        gui.setVisible(true);
        
        System.out.println("✅ Ventana del simulador abierta correctamente");
    });

}
}