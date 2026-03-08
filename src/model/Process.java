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
    private String operacion; // "CREAR", "LEER", "ACTUALIZAR", "ELIMINAR"
    private FileSystemNode archivo; // archivo involucrado
    private int bloqueSolicitado; // para operaciones que requieren posición específica (ej. LEER)
    private int tamano; // para CREAR (tamaño en bloques)
    private String owner; // dueño del proceso (usuario que lo creó)
    
    public Process(String operacion, FileSystemNode archivo, String owner) {
        this.id = contadorId++;
        this.estado = "NUEVO";
        this.operacion = operacion;
        this.archivo = archivo;
        this.owner = owner;
        this.bloqueSolicitado = -1;
        this.tamano = 0;
    }
    
    // Constructor para operación CREAR (necesita tamaño)
    public Process(String operacion, String nombreArchivo, String owner, int tamano, FileSystemNode padre) {
        this.id = contadorId++;
        this.estado = "NUEVO";
        this.operacion = operacion;
        this.owner = owner;
        this.tamano = tamano;
        // El archivo aún no existe, pero podemos guardar el nombre y el padre
        FileSystemNode temp = new FileSystemNode();
        temp.setName(nombreArchivo);
        temp.setParent(padre);
        this.archivo = temp;
        this.bloqueSolicitado = -1;
    }
    
    // Getters y setters
    public int getId() { return id; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getOperacion() { return operacion; }
    public FileSystemNode getArchivo() { return archivo; }
    public int getBloqueSolicitado() { return bloqueSolicitado; }
    public void setBloqueSolicitado(int bloqueSolicitado) { this.bloqueSolicitado = bloqueSolicitado; }
    public int getTamano() { return tamano; }
    public String getOwner() { return owner; }
    
    @Override
    public String toString() {
        return "P" + id + " [" + operacion + "] " + archivo.getName() + " - " + estado;
    }
}
