/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;

/**
 *
 * @author VictorB
 */
import util.LinkedList;
import util.Node;
import java.awt.Color;

public class OperacionesArchivo {
    private Disk disk;
    private FileSystemNode root;

    public OperacionesArchivo(Disk disk, FileSystemNode root) {
        this.disk = disk;
        this.root = root;
    }

    public boolean crear(Process p) {
        String nombre = p.getNombreArchivo();
        String owner = p.getOwner();
        int tamanio = p.getTamanio();
        FileSystemNode padre = p.getPadre();
        if (padre == null) return false;
        if (!disk.hayEspacio(tamanio)) return false;
        Color color = disk.generateUniqueColor();
        int firstBlock = disk.asignarBloques(tamanio, color);
        if (firstBlock == -1) return false;
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
        return true;
    }

    public boolean eliminar(Process p) {
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
            return true;
        }
        return false;
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

    public boolean leer(Process p) {
        // Solo simula lectura
        return true;
    }

    public boolean actualizar(Process p) {
        FileSystemNode archivo = p.getArchivo();
        if (archivo == null || archivo.isDirectory()) return false;
        String nuevoNombre = p.getNuevoNombre();
        if (nuevoNombre != null) {
            archivo.setName(nuevoNombre);
        }
        return true;
    }
}
