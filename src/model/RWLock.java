package model;

import java.util.concurrent.Semaphore;

public class RWLock {
    private Semaphore mutex;
    private Semaphore writeLock;
    private int readers;
    private int writers; // Contador de escritores activos

    public RWLock() {
        mutex = new Semaphore(1);
        writeLock = new Semaphore(1);
        readers = 0;
        writers = 0;
    }

    public void readLock() throws InterruptedException {
        mutex.acquire();
        if (readers == 0) {
            writeLock.acquire(); // Bloquea escritores mientras haya lectores
        }
        readers++;
        mutex.release();
    }

    public void readUnlock() throws InterruptedException {
        mutex.acquire();
        readers--;
        if (readers == 0) {
            writeLock.release(); // Libera a los escritores cuando no hay lectores
        }
        mutex.release();
    }

    public void writeLock() throws InterruptedException {
        writeLock.acquire(); // Espera a que no haya lectores ni otro escritor
        mutex.acquire();
        writers++;
        mutex.release();
    }

    public void writeUnlock() {
        try {
            mutex.acquire();
            writers--;
            mutex.release();
            writeLock.release(); // Libera para que otros puedan escribir o leer
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isWriteLocked() {
        return writers > 0; // Solo true si hay un escritor activo
    }

    public int getReadLockCount() {
        return readers;
    }
}