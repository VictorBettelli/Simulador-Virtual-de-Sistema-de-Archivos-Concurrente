/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;
import util.LinkedList;
import util.Node;
import java.awt.Color; 

/**
 *
 * @author VictorB
 */

import util.LinkedList;
import util.Node;
import java.awt.Color;

public class Scheduler {
    public static final int FIFO = 0;
    public static final int SSTF = 1;
    public static final int SCAN = 2;
    public static final int CSCAN = 3;
    
    private int politicaActual;
    private LinkedList<Process> colaProcesos;
    private int cabezaActual;
    private boolean direccionAscendente;
    private Disk disk;
    private LinkedList<Process> todosLosProcesos = new LinkedList<>();
    
    public Scheduler(Disk disk) {
        this.disk = disk;
        this.colaProcesos = new LinkedList<>();
        this.todosLosProcesos = new LinkedList<>();
        this.politicaActual = FIFO;
        this.cabezaActual = 0;
        this.direccionAscendente = true;
    }
    
    public void setPolitica(int politica) {
        this.politicaActual = politica;
    }
    
    public void setCabezaActual(int posicion) {
        this.cabezaActual = posicion;
    }
    
    public int getCabezaActual() {
        return cabezaActual;
    }
    
    public int getBloqueSolicitado(Process p) {
        return obtenerBloqueSolicitado(p);
    }
    
    public void agregarProceso(Process p) {
        p.setEstado("LISTO");
        colaProcesos.add(p);
        todosLosProcesos.add(p);
        System.out.println("Proceso agregado: " + p);
    }
    
    public LinkedList<Process> getProcesosTodos() {
        return todosLosProcesos;
    }
    
    public Process ejecutarSiguiente() {
        if (colaProcesos.isEmpty()) return null;
        Process siguiente = null;
        switch (politicaActual) {
            case FIFO:  siguiente = fifo(); break;
            case SSTF:  siguiente = sstf(); break;
            case SCAN:  siguiente = scan(); break;
            case CSCAN: siguiente = cscan(); break;
        }
        if (siguiente != null) {
            colaProcesos.remove(siguiente);
            siguiente.setEstado("EJECUTANDO");
        }
        return siguiente;
    }
    
    private Process fifo() {
        return colaProcesos.get(0);
    }
    
    private Process sstf() {
        Node<Process> current = colaProcesos.getHead();
        Process mejor = null;
        int menorDistancia = Integer.MAX_VALUE;
        while (current != null) {
            Process p = current.data;
            int bloque = obtenerBloqueSolicitado(p);
            if (bloque != -1) {
                int distancia = Math.abs(bloque - cabezaActual);
                if (distancia < menorDistancia) {
                    menorDistancia = distancia;
                    mejor = p;
                }
            }
            current = current.next;
        }
        return (mejor != null) ? mejor : fifo();
    }
    
    private Process scan() {
        Node<Process> current = colaProcesos.getHead();
        Process mejor = null;
        int menorDistancia = Integer.MAX_VALUE;
        while (current != null) {
            Process p = current.data;
            int bloque = obtenerBloqueSolicitado(p);
            if (bloque != -1) {
                if (direccionAscendente) {
                    if (bloque >= cabezaActual && (bloque - cabezaActual) < menorDistancia) {
                        menorDistancia = bloque - cabezaActual;
                        mejor = p;
                    }
                } else {
                    if (bloque <= cabezaActual && (cabezaActual - bloque) < menorDistancia) {
                        menorDistancia = cabezaActual - bloque;
                        mejor = p;
                    }
                }
            }
            current = current.next;
        }
        if (mejor == null) {
            direccionAscendente = !direccionAscendente;
            return scan();
        }
        return mejor;
    }
    
    private Process cscan() {
        Node<Process> current = colaProcesos.getHead();
        Process mejor = null;
        int menorDistancia = Integer.MAX_VALUE;
        while (current != null) {
            Process p = current.data;
            int bloque = obtenerBloqueSolicitado(p);
            if (bloque != -1) {
                if (bloque >= cabezaActual) {
                    int distancia = bloque - cabezaActual;
                    if (distancia < menorDistancia) {
                        menorDistancia = distancia;
                        mejor = p;
                    }
                }
            }
            current = current.next;
        }
        if (mejor == null) {
            current = colaProcesos.getHead();
            int menor = Integer.MAX_VALUE;
            while (current != null) {
                Process p = current.data;
                int bloque = obtenerBloqueSolicitado(p);
                if (bloque != -1 && bloque < menor) {
                    menor = bloque;
                    mejor = p;
                }
                current = current.next;
            }
        }
        return (mejor != null) ? mejor : fifo();
    }
    
    private int obtenerBloqueSolicitado(Process p) {
        String op = p.getOperacion();
        if (op.equals("CREATE") || op.equals("DELETE")) {
            FileSystemNode archivo = p.getArchivo();
            if (archivo != null && archivo.getFirstBlock() != -1) {
                return archivo.getFirstBlock();
            }
        } else if (op.equals("READ") || op.equals("UPDATE")) {
            if (p.getBloqueSolicitado() != -1) {
                return p.getBloqueSolicitado();
            } else if (p.getArchivo() != null && p.getArchivo().getFirstBlock() != -1) {
                return p.getArchivo().getFirstBlock();
            }
        }
        return -1;
    }
    
    public LinkedList<Process> getColaProcesos() {
        return colaProcesos;
    }
}