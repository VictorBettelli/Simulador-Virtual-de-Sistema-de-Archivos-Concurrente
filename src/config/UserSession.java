/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package config;
import model.FileSystemNode;
/**
 *
 * @author luisf
 */


public class UserSession {
    private String currentUser;
    private boolean isAdmin;
    
    // Solo usuarios normales (sin public)
    public static final String[] USERS = {"admin", "usuario1", "usuario2"};
    
    public UserSession() {
        this.currentUser = "admin";
        this.isAdmin = true;
    }
    
    public void setUser(String username, boolean admin) {
        this.currentUser = username;
        this.isAdmin = admin;
    }
    
    public String getCurrentUser() {
        return currentUser;
    }
    
    public boolean isAdmin() {
        return isAdmin;
    }
    
    // En modo usuario: SOLO LECTURA de TODOS los archivos
    public boolean canRead(FileSystemNode node) {
        if (isAdmin) return true; // Admin ve todo
        if (node == null) return false;
        
        // En modo usuario: puede ver TODO (solo lectura)
        return true;
    }
    
    // Escritura: solo admin
    public boolean canWrite(FileSystemNode node) {
        return isAdmin; // Solo admin puede escribir
    }
    
    // Eliminar: solo admin
    public boolean canDelete(FileSystemNode node) {
        return isAdmin; // Solo admin puede eliminar
    }
    
    // Crear: solo admin
    public boolean canCreateIn(FileSystemNode parent) {
        return isAdmin; // Solo admin puede crear
    }
    
    public String getModeDisplay() {
        if (isAdmin) {
            return "Administrador (" + currentUser + ")";
        } else {
            return "Usuario (" + currentUser + " - SOLO LECTURA)";
        }
    }
}
