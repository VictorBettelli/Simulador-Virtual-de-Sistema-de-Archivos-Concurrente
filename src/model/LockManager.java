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

public class LockManager {
    private LinkedList<FileSystemNode> archivos;
    private LinkedList<RWLock> locks;

    public LockManager() {
        archivos = new LinkedList<>();
        locks = new LinkedList<>();
    }

    public synchronized RWLock getLock(FileSystemNode archivo) {
        Node<FileSystemNode> currentArch = archivos.getHead();
        Node<RWLock> currentLock = locks.getHead();
        while (currentArch != null) {
            if (currentArch.data == archivo) {
                return currentLock.data;
            }
            currentArch = currentArch.next;
            currentLock = currentLock.next;
        }
        RWLock newLock = new RWLock();
        archivos.add(archivo);
        locks.add(newLock);
        return newLock;
    }

    public synchronized LinkedList<String> getLocksInfo() {
        LinkedList<String> info = new LinkedList<>();
        Node<FileSystemNode> currentArch = archivos.getHead();
        Node<RWLock> currentLock = locks.getHead();
        while (currentArch != null) {
            info.add(currentArch.data.getName() + ": lock activo");
            currentArch = currentArch.next;
            currentLock = currentLock.next;
        }
        return info;
    }
}