/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;

/**
 *
 * @author luisf
 */
public class JournalEntry {
    private int id;
    private String operacion; // CREATE, DELETE, UPDATE
    private String rutaArchivo;
    private String owner;
    private int primerBloque;
    private int tamanio;
    private String estado; // PENDIENTE, CONFIRMADA, DESHECHA
    private String nombreAnterior; // Para UPDATE
    private String nombreNuevo;     // Para UPDATE
    
    // Constructor para CREATE
    public JournalEntry(String operacion, String rutaArchivo, String owner, int primerBloque, int tamanio) {
        this.id = generateId();
        this.operacion = operacion;
        this.rutaArchivo = rutaArchivo;
        this.owner = owner;
        this.primerBloque = primerBloque;
        this.tamanio = tamanio;
        this.estado = "PENDIENTE";
    }
    
    // Constructor para DELETE
    public JournalEntry(String operacion, String rutaArchivo, String owner, int primerBloque) {
        this.id = generateId();
        this.operacion = operacion;
        this.rutaArchivo = rutaArchivo;
        this.owner = owner;
        this.primerBloque = primerBloque;
        this.estado = "PENDIENTE";
    }
    
    // Constructor para UPDATE
    public JournalEntry(String operacion, String rutaArchivo, String nombreAnterior, String nombreNuevo) {
        this.id = generateId();
        this.operacion = operacion;
        this.rutaArchivo = rutaArchivo;
        this.nombreAnterior = nombreAnterior;
        this.nombreNuevo = nombreNuevo;
        this.estado = "PENDIENTE";
        this.primerBloque = -1;
        this.tamanio = 0;
        this.owner = null;
    }
    
    private int generateId() {
        return (int)(System.currentTimeMillis() % 10000);
    }
    
    // Getters y setters
    public int getId() { return id; }
    public String getOperacion() { return operacion; }
    public String getRutaArchivo() { return rutaArchivo; }
    public String getOwner() { return owner; }
    public int getPrimerBloque() { return primerBloque; }
    public int getTamanio() { return tamanio; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getNombreAnterior() { return nombreAnterior; }
    public String getNombreNuevo() { return nombreNuevo; }
    
    @Override
    public String toString() {
        return "JournalEntry{" + "id=" + id + ", operacion=" + operacion + ", ruta=" + rutaArchivo + ", estado=" + estado + '}';
    }
}
