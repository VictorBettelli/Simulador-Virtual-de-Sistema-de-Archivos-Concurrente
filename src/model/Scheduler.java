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

public class Scheduler {
    public static final int FIFO = 0;
    public static final int SSTF = 1;
    public static final int SCAN = 2;
    public static final int CSCAN = 3;
    
    private int politicaActual;
    private LinkedList<Process> colaProcesos; // Cola de listos (pendientes)
    private int cabezaActual; // posición actual del cabezal
    private boolean direccionAscendente; // para SCAN y C-SCAN (true = hacia arriba/derecha)
    private Disk disk; // referencia al disco para conocer posiciones de bloques
    private LinkedList<Process> todosLosProcesos = new LinkedList<>();
    private int totalProcesos;
    
    public Scheduler(Disk disk) {
        this.disk = disk;
        this.colaProcesos = new LinkedList<>();
        this.todosLosProcesos = new LinkedList<>();
        this.politicaActual = FIFO;
        this.cabezaActual = 0; // posición inicial por defecto
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
        todosLosProcesos.add(p); // <-- agregar también aquí
        totalProcesos++;
        System.out.println("Proceso agregado: " + p);
        }
    public LinkedList<Process> getProcesosTodos() {
        return todosLosProcesos; // <-- nuevo método
    }
    
    // Ejecuta el siguiente proceso según la política actual
    public Process ejecutarSiguiente() {
        if (colaProcesos.isEmpty()) return null;
        
        Process siguiente = null;
        
        switch (politicaActual) {
            case FIFO:
                siguiente = fifo();
                break;
            case SSTF:
                siguiente = sstf();
                break;
            case SCAN:
                siguiente = scan();
                break;
            case CSCAN:
                siguiente = cscan();
                break;
        }
        
        if (siguiente != null) {
            // Remover de la cola y cambiar estado
            colaProcesos.remove(siguiente);
            siguiente.setEstado("EJECUTANDO");
            // Simular ejecución (luego se llamará a un método que realice la operación real)
        }
        return siguiente;
    }
    
    // FIFO: simplemente el primero que llegó
    private Process fifo() {
        return colaProcesos.get(0);
    }
    
    // SSTF: el que tenga el bloque más cercano a la cabeza actual
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
        
        if (mejor == null) {
            // Si ningún proceso tiene bloque (ej. crear directorio), usar FIFO
            return fifo();
        }
        return mejor;
    }
    
    // SCAN: barrido en una dirección
    private Process scan() {
        // Implementación básica: buscar el más cercano en la dirección actual
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
            // Cambiar dirección y buscar de nuevo
            direccionAscendente = !direccionAscendente;
            return scan();
        }
        return mejor;
    }
    
    // C-SCAN: similar a SCAN pero solo en una dirección y vuelve al inicio
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
            // Si no hay en dirección ascendente, tomar el menor (dará la vuelta)
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
        return mejor != null ? mejor : fifo();
    }
    
    // Obtiene el bloque involucrado en la operación del proceso
    private int obtenerBloqueSolicitado(Process p) {
        if (p.getOperacion().equals("CREAR") || p.getOperacion().equals("ELIMINAR")) {
            // Estas operaciones afectan varios bloques, pero para planificación podemos considerar el primer bloque
            FileSystemNode archivo = p.getArchivo();
            if (archivo != null && archivo.getFirstBlock() != -1) {
                return archivo.getFirstBlock();
            }
        } else if (p.getOperacion().equals("LEER") || p.getOperacion().equals("ACTUALIZAR")) {
            // Podríamos tener un bloque específico
            if (p.getBloqueSolicitado() != -1) {
                return p.getBloqueSolicitado();
            } else if (p.getArchivo() != null && p.getArchivo().getFirstBlock() != -1) {
                return p.getArchivo().getFirstBlock();
            }
        }
        return -1; // No aplica (ej. crear directorio)
    }
    
    public LinkedList<Process> getColaProcesos() {
        return colaProcesos;
    }
}