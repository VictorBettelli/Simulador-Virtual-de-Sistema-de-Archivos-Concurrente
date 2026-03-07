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
import java.awt.Color;

public class Disk {
    public static final int SIZE = 256;
    private Block[] blocks;
    private LinkedList<Integer> freeList;
    private LinkedList<Color> usedColors;

    public Disk() {
        blocks = new Block[SIZE];
        freeList = new LinkedList<>();
        usedColors = new LinkedList<>();
        for (int i = 0; i < SIZE; i++) {
            blocks[i] = new Block();
            freeList.add(i);
        }
    }

    public int asignarBloques(int cantidad, Color fileColor) {
        if (freeList.size() < cantidad) return -1;

        // Registrar el color si no está ya en uso
        if (!usedColors.contains(fileColor)) {
            usedColors.add(fileColor);
        }

        int first = freeList.get(0);
        int current = first;
        for (int i = 0; i < cantidad; i++) {
            int next = (i == cantidad - 1) ? -1 : freeList.get(i + 1);
            blocks[current].setNext(next);
            blocks[current].setLibre(false);
            blocks[current].setColor(fileColor);
            freeList.remove((Integer) current); // eliminar de libres
            current = next;
        }
        return first;
    }

    public void liberarBloques(int firstBlock, Color color) {
        int current = firstBlock;
        while (current != -1) {
            blocks[current].setLibre(true);
            blocks[current].setColor(null);
            freeList.add(current);
            int next = blocks[current].getNext();
            blocks[current].setNext(-1);
            current = next;
        }
        if (color != null) {
            usedColors.remove(color);
        }
    }

    public boolean hayEspacio(int cantidad) {
        return freeList.size() >= cantidad;
    }

    public Color generateUniqueColor() {
        Color newColor;
        do {
            newColor = new Color((int)(Math.random() * 0x1000000));
        } while (usedColors.contains(newColor)); // AHORA SÍ FUNCIONA
        return newColor;
    }

    public Block[] getBlocks() {
        return blocks;
    }

    public static class Block {
        private boolean libre;
        private int next;
        private Color color;

        public Block() {
            this.libre = true;
            this.next = -1;
            this.color = null;
        }

        public boolean isLibre() { return libre; }
        public void setLibre(boolean libre) { this.libre = libre; }
        public int getNext() { return next; }
        public void setNext(int next) { this.next = next; }
        public Color getColor() { return color; }
        public void setColor(Color color) { this.color = color; }
    }
}