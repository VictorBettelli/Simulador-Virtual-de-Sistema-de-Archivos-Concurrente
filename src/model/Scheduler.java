package model;

import util.LinkedList;
import util.Node;

public class Scheduler extends Thread {
    public static final int FIFO = 0;
    public static final int SSTF = 1;
    public static final int SCAN = 2;
    public static final int CSCAN = 3;
    
    private volatile int politicaActual;
    private LinkedList<SolicitudES> colaSolicitudes;
    private int cabezaActual;
    private boolean direccionAscendente;
    private Disk disk;
    private OperacionesArchivo operaciones;
    private boolean ejecutando;
    private LogListener logListener;
    private int solicitudesEsperadas = 0;
    private boolean modoManual = false;
    private boolean siguientePermitido = false;
    
    public Scheduler(Disk disk, OperacionesArchivo operaciones, LogListener logListener) {
        this.disk = disk;
        this.operaciones = operaciones;
        this.logListener = logListener;
        this.colaSolicitudes = new LinkedList<>();
        this.politicaActual = FIFO;
        this.cabezaActual = 0;
        this.direccionAscendente = true;
        this.ejecutando = true;
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
    
    public synchronized void agregarSolicitud(SolicitudES solicitud) {
        colaSolicitudes.add(solicitud);
        notify();
    }
    
    public synchronized void setSolicitudesEsperadas(int n) {
        this.solicitudesEsperadas = n;
        notify();
    }
    
    public synchronized void setModoManual(boolean manual) {
        this.modoManual = manual;
        if (!manual) {
            notify(); // Si salimos de modo manual, desbloquear posible espera
        }
    }
    
    public synchronized void permitirSiguiente() {
        if (modoManual) {
            siguientePermitido = true;
            notify();
        }
    }
    
      @Override
public void run() {
    while (ejecutando) {
        SolicitudES solicitud = null;
        synchronized (this) {
            // Esperar a que haya al menos una solicitud en la cola
            while (colaSolicitudes.isEmpty() && ejecutando) {
                try {
                    wait();
                } catch (InterruptedException e) {}
            }
            if (!ejecutando) break;

            // Esperar a que lleguen todas las solicitudes si se ha fijado un número
            if (solicitudesEsperadas > 0) {
                while (colaSolicitudes.size() < solicitudesEsperadas && ejecutando) {
                    try {
                        wait();
                    } catch (InterruptedException e) {}
                }
                solicitudesEsperadas = 0; // Reseteamos para próximas tandas
            }

            // Si estamos en modo manual, esperar permiso del usuario
            if (modoManual && !colaSolicitudes.isEmpty()) {
                siguientePermitido = false;
                while (!siguientePermitido && ejecutando) {
                    try {
                        wait();
                    } catch (InterruptedException e) {}
                }
                if (!ejecutando) break;
            }

            // Seleccionar la siguiente solicitud según la política actual
            solicitud = obtenerSiguienteSolicitud();
        }

        if (solicitud != null) {
            atenderSolicitud(solicitud);
        }
    }
}  
    private synchronized SolicitudES obtenerSiguienteSolicitud() {
        if (colaSolicitudes.isEmpty()) return null;
        SolicitudES seleccionada = null;
        switch (politicaActual) {
            case FIFO:
                Node<SolicitudES> current = colaSolicitudes.getHead();
                int menorOrden = Integer.MAX_VALUE;
                while (current != null) {
                    SolicitudES sol = current.data;
                    int orden = sol.getProceso().getDatosProceso().getOrden();
                    if (orden < menorOrden) {
                        menorOrden = orden;
                        seleccionada = sol;
                    }
                    current = current.next;
                }
                break;
            case SSTF:
                seleccionada = sstf();
                break;
            case SCAN:
                seleccionada = scan();
                break;
            case CSCAN:
                seleccionada = cscan();
                break;
            default:
                seleccionada = colaSolicitudes.get(0);
        }
        if (seleccionada != null) {
            colaSolicitudes.remove(seleccionada);
        }
        return seleccionada;
    }
    
    private SolicitudES sstf() {
    Node<SolicitudES> current = colaSolicitudes.getHead();
    SolicitudES mejor = null;
    int menorDistancia = Integer.MAX_VALUE;
    System.out.println("=== SSTF ===");
    System.out.println("Cabeza actual: " + cabezaActual);
    while (current != null) {
        SolicitudES sol = current.data;
        int bloque = sol.getBloque();
        System.out.println("  Solicitud: bloque " + bloque + ", distancia " + (bloque != -1 ? Math.abs(bloque - cabezaActual) : "N/A"));
        if (bloque != -1) {
            int distancia = Math.abs(bloque - cabezaActual);
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                mejor = sol;
            }
        }
        current = current.next;
    }
    if (mejor == null) {
        System.out.println("  Ninguna con bloque, se toma la primera.");
        return colaSolicitudes.get(0);
    }
    System.out.println("  Seleccionada: bloque " + mejor.getBloque() + " con distancia " + menorDistancia);
    return mejor;
}
    
    private SolicitudES scan() {
        Node<SolicitudES> current = colaSolicitudes.getHead();
        SolicitudES mejor = null;
        int menorDistancia = Integer.MAX_VALUE;
        while (current != null) {
            SolicitudES sol = current.data;
            int bloque = sol.getBloque();
            if (bloque != -1) {
                if (direccionAscendente) {
                    if (bloque >= cabezaActual && (bloque - cabezaActual) < menorDistancia) {
                        menorDistancia = bloque - cabezaActual;
                        mejor = sol;
                    }
                } else {
                    if (bloque <= cabezaActual && (cabezaActual - bloque) < menorDistancia) {
                        menorDistancia = cabezaActual - bloque;
                        mejor = sol;
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
    
    private SolicitudES cscan() {
        Node<SolicitudES> current = colaSolicitudes.getHead();
        SolicitudES mejor = null;
        int menorDistancia = Integer.MAX_VALUE;
        while (current != null) {
            SolicitudES sol = current.data;
            int bloque = sol.getBloque();
            if (bloque != -1) {
                if (bloque >= cabezaActual) {
                    int distancia = bloque - cabezaActual;
                    if (distancia < menorDistancia) {
                        menorDistancia = distancia;
                        mejor = sol;
                    }
                }
            }
            current = current.next;
        }
        if (mejor == null) {
            current = colaSolicitudes.getHead();
            int menor = Integer.MAX_VALUE;
            while (current != null) {
                SolicitudES sol = current.data;
                int bloque = sol.getBloque();
                if (bloque != -1 && bloque < menor) {
                    menor = bloque;
                    mejor = sol;
                }
                current = current.next;
            }
        }
        if (mejor == null) {
            return colaSolicitudes.get(0);
        }
        return mejor;
    }
    
    private void atenderSolicitud(SolicitudES solicitud) {
        ProcesoHilo hilo = solicitud.getProceso();
        Process datos = hilo.getDatosProceso();
        int cabezaAntes = cabezaActual;
        boolean exito = false;
        try {
            switch (datos.getOperacion()) {
                case "CREATE":
                    exito = operaciones.crear(datos);
                    break;
                case "DELETE":
                    exito = operaciones.eliminar(datos);
                    break;
                case "READ":
                    exito = operaciones.leer(datos);
                    break;
                case "UPDATE":
                    exito = operaciones.actualizar(datos);
                    break;
                default:
                    System.err.println("Operación desconocida: " + datos.getOperacion());
            }
        } catch (Exception e) {
            System.err.println("Error en operación: " + e.getMessage());
            exito = false;
        }
        
        int bloque = solicitud.getBloque();
        if (bloque != -1) {
            int distancia = Math.abs(bloque - cabezaAntes);
            cabezaActual = bloque;
            if (logListener != null) {
                String mensaje = "Cabezal: " + cabezaAntes + " → " + bloque + " (dist " + distancia + ") - " + datos;
                logListener.onMovimiento(mensaje);
            }
        } else {
            if (logListener != null) {
                String mensaje = "Operación sin bloque: " + datos + (exito ? " (éxito)" : " (fallo)");
                logListener.onMovimiento(mensaje);
            }
        }
        
        synchronized (hilo) {
            hilo.notify();
        }
    }
    
    public void detener() {
        ejecutando = false;
        interrupt();
    }
    
    public synchronized LinkedList<String> getSolicitudesPendientes() {
        LinkedList<String> info = new LinkedList<>();
        Node<SolicitudES> current = colaSolicitudes.getHead();
        while (current != null) {
            SolicitudES sol = current.data;
            Process p = sol.getProceso().getDatosProceso();
            info.add("P" + p.getId() + " [" + p.getOperacion() + "] bloque " + sol.getBloque());
            current = current.next;
        }
        return info;
    }
}