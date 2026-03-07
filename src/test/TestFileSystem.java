/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package test;
import util.LinkedList;
import util.Node;
import model.FileSystemNode;
/**
 *
 * @author luisf
 */
public class TestFileSystem {
    public static void main(String[] args) {
        // Prueba 1: Crear estructura básica
        FileSystemNode root = new FileSystemNode();
        root.setName("/");
        root.setOwner("admin");
        root.setDirectory(true);
        
        // Prueba 2: Crear directorios
        FileSystemNode home = new FileSystemNode();
        home.setName("home");
        home.setOwner("admin");
        home.setDirectory(true);
        home.setParent(root);
        
        LinkedList<FileSystemNode> rootChildren = new LinkedList<>(); // CORREGIDO
        rootChildren.add(home);
        root.setChildren(rootChildren);
        
        FileSystemNode user1 = new FileSystemNode();
        user1.setName("usuario1");
        user1.setOwner("usuario1");
        user1.setDirectory(true);
        user1.setParent(home);
        
        LinkedList<FileSystemNode> homeChildren = new LinkedList<>(); // CORREGIDO
        homeChildren.add(user1);
        home.setChildren(homeChildren);
        
        // Prueba 3: Crear archivo
        FileSystemNode file1 = new FileSystemNode();
        file1.setName("documento.txt");
        file1.setOwner("usuario1");
        file1.setDirectory(false);
        file1.setSizeInBlocks(5);
        file1.setFirstBlock(10);
        file1.setParent(user1);
        
        LinkedList<FileSystemNode> user1Children = new LinkedList<>(); // CORREGIDO
        user1Children.add(file1);
        user1.setChildren(user1Children);
        
        // Prueba 4: Verificar estructura
        System.out.println("=== Verificando estructura ===");
        System.out.println("Raíz: " + root.getName());
        System.out.println("  Dueño: " + root.getOwner());
        
        Node<FileSystemNode> node = root.getChildren().getHead(); // CORREGIDO
        while (node != null) {
            FileSystemNode child = node.data;
            System.out.println("  Hijo: " + child.getName());
            System.out.println("    Dueño: " + child.getOwner());
            
            if (child.getChildren() != null) {
                Node<FileSystemNode> grandchild = child.getChildren().getHead(); // CORREGIDO
                while (grandchild != null) {
                    FileSystemNode gc = grandchild.data;
                    System.out.println("    Nieto: " + gc.getName());
                    if (!gc.isDirectory()) {
                        System.out.println("      Tamaño: " + gc.getSizeInBlocks() + " bloques");
                        System.out.println("      Primer bloque: " + gc.getFirstBlock());
                    }
                    grandchild = grandchild.next;
                }
            }
            node = node.next;
        }
    }
}
