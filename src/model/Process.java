/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;

/**
 *
 * @author VictorB
 */

public class Process {
    private static int contadorId = 1;
    
    private int id;
    private String estado; // "NUEVO", "LISTO", "EJECUTANDO", "BLOQUEADO", "TERMINADO"
    private String operacion; // "CREATE", "READ", "UPDATE", "DELETE"
    private FileSystemNode archivo; // archivo involucrado (puede ser null para CREATE)
    private int bloqueSolicitado; // para operaciones que requieren posición específica
    private int tamanio; // para CREATE (tamaño en bloques)
    private String owner; // dueño del proceso
    private String nombreArchivo; // para cuando el archivo aún no existe (CREATE)
    private FileSystemNode padre; // directorio padre para CREATE
    private String nuevoNombre; // para UPDATE
    private static int contadorOrden = 0;
    private int orden;
    // Constructor para operaciones sobre archivo existente
    public Process(String operacion, FileSystemNode archivo, String owner) {
        this.id = contadorId++;
        synchronized (Process.class) {
        orden = ++contadorOrden;}
        this.estado = "NUEVO";
        this.operacion = operacion;
        this.archivo = archivo;
        this.owner = owner;
        this.bloqueSolicitado = -1;
        this.tamanio = 0;
        this.nombreArchivo = null;
        this.padre = null;
        this.nuevoNombre = null;
    }
    
    // Constructor para operación CREATE
    public Process(String operacion, String nombreArchivo, String owner, int tamanio, FileSystemNode padre) {
        this.id = contadorId++;
        synchronized (Process.class) {
        orden = ++contadorOrden;}
        this.estado = "NUEVO";
        this.operacion = operacion;
        this.owner = owner;
        this.tamanio = tamanio;
        this.nombreArchivo = nombreArchivo;
        this.padre = padre;
        this.archivo = null;
        this.bloqueSolicitado = -1;
        this.nuevoNombre = null;
    }
    
    // Getters y setters
    public int getId() { return id; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getOperacion() { return operacion; }
    public FileSystemNode getArchivo() { return archivo; }
    public void setArchivo(FileSystemNode archivo) { this.archivo = archivo; }
    public int getBloqueSolicitado() { return bloqueSolicitado; }
    public void setBloqueSolicitado(int bloqueSolicitado) { this.bloqueSolicitado = bloqueSolicitado; }
    public int getTamanio() { return tamanio; }
    public void setTamanio(int tamanio) { this.tamanio = tamanio; }
    public String getOwner() { return owner; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public FileSystemNode getPadre() { return padre; }
    public void setPadre(FileSystemNode padre) { this.padre = padre; }
    public String getNuevoNombre() { return nuevoNombre; }
    public void setNuevoNombre(String nuevoNombre) { this.nuevoNombre = nuevoNombre; }
    public int getOrden() { return orden; }
    // Obtener el bloque principal para planificación
    public int getBloqueParaPlanificacion() {
        if (bloqueSolicitado != -1) return bloqueSolicitado;
        if (archivo != null && !archivo.isDirectory() && archivo.getFirstBlock() != -1) {
            return archivo.getFirstBlock();
        }
        return -1;
    }
    
    // Métodos estáticos para el contador
    public static int getContadorId() { return contadorId; }
    public static void setContadorId(int id) { contadorId = id; }
    
    @Override
public String toString() {
    String tipo = (archivo != null && archivo.isDirectory()) ? "DIR" : "FILE";
    String nombre = (archivo != null) ? archivo.getName() : nombreArchivo;
    String base = "P" + id + " [" + operacion + " " + tipo + "] " + nombre + " - " + estado;
    if (operacion.equals("UPDATE") && nuevoNombre != null) {
        base += " → " + nuevoNombre;
    }
    return base;
}
}