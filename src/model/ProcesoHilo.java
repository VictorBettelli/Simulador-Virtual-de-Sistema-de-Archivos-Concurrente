/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;
import view.FileSystemGUI;
/**
 *
 * @author VictorB
 */
public class ProcesoHilo extends Thread {
    private Process datosProceso;
    private FileSystemNode archivo;
    private RWLock lock;
    private Scheduler scheduler;
    private Disk disk;
    private LockManager lockManager;
    private Terminable callback;
    private FileSystemGUI gui;

    public ProcesoHilo(Process datos, FileSystemNode archivo, RWLock lock,
                       Scheduler scheduler, Disk disk, LockManager lockManager,
                       Terminable callback) {
        this.datosProceso = datos;
        this.archivo = archivo;
        this.lock = lock;
        this.scheduler = scheduler;
        this.disk = disk;
        this.lockManager = lockManager;
        this.callback = callback;
    }

    public Process getDatosProceso() { return datosProceso; }

@Override
public void run() {
    datosProceso.setEstado("NUEVO");
    try {
        datosProceso.setEstado("LISTO");
        Thread.sleep(1000); // Permite ver el estado LISTO en la GUI

        boolean esLectura = datosProceso.getOperacion().equals("READ");
        if (esLectura) {
            lock.readLock();
        } else {
            lock.writeLock();
        }
        datosProceso.setEstado("EJECUTANDO");
        Thread.sleep(1000); // Permite ver el estado EJECUTANDO antes de enviar la solicitud

        int bloque = datosProceso.getBloqueParaPlanificacion();
        SolicitudES solicitud = new SolicitudES(this, bloque, datosProceso.getOperacion());
        scheduler.agregarSolicitud(solicitud);

        datosProceso.setEstado("BLOQUEADO");
        synchronized (this) {
            wait(); // Espera a que el scheduler lo despierte
        }

        datosProceso.setEstado("EJECUTANDO"); // Vuelve a ejecutarse brevemente
        // No añadimos sleep aquí porque es muy breve y luego termina

        if (esLectura) {
            lock.readUnlock();
        } else {
            lock.writeUnlock();
        }

        datosProceso.setEstado("TERMINADO");

    } catch (InterruptedException e) {
        datosProceso.setEstado("ERROR");
    } finally {
        if (callback != null) {
            callback.onTerminate(this);
        }
    }
}
}