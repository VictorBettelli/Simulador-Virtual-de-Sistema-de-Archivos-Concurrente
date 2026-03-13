/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;
import java.util.concurrent.Semaphore;
/**
 *
 * @author VictorB
 */


public class RWLock {
    private Semaphore mutex;        // para proteger contadores
    private Semaphore writeLock;     // exclusivo para escritores
    private int readers;             // número de lectores activos

    public RWLock() {
        mutex = new Semaphore(1);
        writeLock = new Semaphore(1);
        readers = 0;
    }

    // Lock compartido para lectura
    public void readLock() throws InterruptedException {
        mutex.acquire();
        if (readers == 0) {
            writeLock.acquire(); // primer lector bloquea escritores
        }
        readers++;
        mutex.release();
    }

    public void readUnlock() throws InterruptedException {
        mutex.acquire();
        readers--;
        if (readers == 0) {
            writeLock.release(); // último lector libera escritores
        }
        mutex.release();
    }

    // Lock exclusivo para escritura
    public void writeLock() throws InterruptedException {
        writeLock.acquire();
    }

    public void writeUnlock() {
        writeLock.release();
    }
}
