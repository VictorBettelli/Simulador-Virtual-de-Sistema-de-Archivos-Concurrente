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
    
    // SOLO usuarios normales (sin admin ni public)
    public static final String[] USERS = {"usuario1", "usuario2"};
    
    public UserSession() {
        this.currentUser = "usuario1"; // Por defecto, usuario1
        this.isAdmin = false; // Por defecto, NO admin
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
    
    // ===== PERMISOS =====
    
        /**
     * TODOS los usuarios pueden leer TODOS los archivos
     * (sin importar quién sea el dueño)
     */
    public boolean canRead(FileSystemNode node) {
        if (node == null) return false;
        // Todos pueden leer todo
        return true;
    }
    
    public boolean canCreateIn(FileSystemNode parent) {
        if (isAdmin) return true;
        if (parent == null) return false;
        
        String owner = parent.getOwner();
        return owner != null && owner.equals(currentUser);
    }
    
    public boolean canModify(FileSystemNode node) {
        if (isAdmin) return true;
        if (node == null) return false;
        
        String owner = node.getOwner();
        return owner != null && owner.equals(currentUser);
    }
    
    public boolean canDelete(FileSystemNode node) {
        if (isAdmin) return true;
        if (node == null) return false;
        
        String owner = node.getOwner();
        return owner != null && owner.equals(currentUser);
    }
    
    public boolean canWrite(FileSystemNode node) {
        if (isAdmin) return true;
        if (node == null) return false;
        
        String owner = node.getOwner();
        return owner != null && owner.equals(currentUser);
    }
    
    public String getModeDisplay() {
        if (isAdmin) {
            return "Administrador";
        } else {
            return "Usuario (" + currentUser + ")";
        }
    }
}