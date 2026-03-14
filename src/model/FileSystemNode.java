/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;
import util.LinkedList;
import java.awt.Color;

/**
 *
 * @author luisf
 */
// Archivo: FileSystemNode.java
public class FileSystemNode {
    private String name;
    private String owner;
    private FileSystemNode parent;
    private LinkedList<FileSystemNode> children;
    private boolean isDirectory;
    private int sizeInBlocks;
    private int firstBlock;
    private Color color;

    public FileSystemNode() {
        this.children = null;
        this.sizeInBlocks = 0;
        this.firstBlock = -1;
        this.color = null;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public FileSystemNode getParent() { return parent; }
    public void setParent(FileSystemNode parent) { this.parent = parent; }

    public LinkedList<FileSystemNode> getChildren() { return children; }
    public void setChildren(LinkedList<FileSystemNode> children) { this.children = children; }

    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean isDirectory) { this.isDirectory = isDirectory; }

    public int getSizeInBlocks() { return sizeInBlocks; }
    public void setSizeInBlocks(int sizeInBlocks) { this.sizeInBlocks = sizeInBlocks; }

    public int getFirstBlock() { return firstBlock; }
    public void setFirstBlock(int firstBlock) { this.firstBlock = firstBlock; }

    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        FileSystemNode that = (FileSystemNode) obj;
        return name != null ? name.equals(that.name) : that.name == null;
    }

    public String getFullPath() {
        if (parent == null) return name;
        if (parent.getParent() == null) return parent.getName() + name;
        return parent.getFullPath() + "/" + name;
    }
        
    public static String getRutaCompleta(FileSystemNode node) {
        if (node == null) return "";
        if (node.getParent() == null) return node.getName();
        return getRutaCompleta(node.getParent()) + "/" + node.getName();
    }

    @Override
    public String toString() {
        return name;
    }
}