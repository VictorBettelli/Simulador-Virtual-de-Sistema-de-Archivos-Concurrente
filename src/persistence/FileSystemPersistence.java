/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package persistence;

/**
 *
 * @author luisf
 */
import model.FileSystemNode;
import model.Disk;
import model.Process;
import util.LinkedList;
import util.Node;

import java.io.*;
import java.awt.Color;
import org.json.simple.*;
import org.json.simple.parser.*;

public class FileSystemPersistence {
    
    private static final String DATA_DIR = "data";
    private static final String FS_FILE = DATA_DIR + "/filesystem.json";
    private static final String PROCS_FILE = DATA_DIR + "/procesos.json";
    
    // Crear directorio data si no existe
    static {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    // ===== GUARDAR TODO =====
    public static void guardarTodo(FileSystemNode root, Disk disk, LinkedList<Process> procesos) {
        guardarFileSystem(root, disk);
        guardarProcesos(procesos);
        System.out.println("💾 Datos guardados en " + DATA_DIR);
    }
    
    // ===== CARGAR TODO =====
    public static void cargarTodo(FileSystemNode root, Disk disk, LinkedList<Process> procesos) {
        cargarFileSystem(root, disk);
        cargarProcesos(procesos);
        System.out.println("📂 Datos cargados desde " + DATA_DIR);
    }
    
    // ===== GUARDAR SISTEMA DE ARCHIVOS =====
    private static void guardarFileSystem(FileSystemNode root, Disk disk) {
        JSONObject json = new JSONObject();
        
        // Guardar disco
        JSONArray bloquesArray = new JSONArray();
        Disk.Block[] blocks = disk.getBlocks();
        for (int i = 0; i < blocks.length; i++) {
            JSONObject blockJson = new JSONObject();
            blockJson.put("index", i);
            blockJson.put("libre", blocks[i].isLibre());
            blockJson.put("next", blocks[i].getNext());
            if (blocks[i].getColor() != null) {
                blockJson.put("color", blocks[i].getColor().getRGB());
            }
            bloquesArray.add(blockJson);
        }
        json.put("bloques", bloquesArray);
        
        // Guardar árbol de archivos
        JSONObject rootJson = new JSONObject();
        guardarNodo(rootJson, root);
        json.put("root", rootJson);
        
        // Escribir archivo
        try (FileWriter writer = new FileWriter(FS_FILE)) {
            writer.write(json.toJSONString());
            writer.flush();
        } catch (IOException e) {
            System.err.println("Error guardando filesystem: " + e.getMessage());
        }
    }
    
    // ===== GUARDAR UN NODO RECURSIVAMENTE =====
    private static void guardarNodo(JSONObject json, FileSystemNode node) {
        json.put("nombre", node.getName());
        json.put("owner", node.getOwner());
        json.put("isDirectory", node.isDirectory());
        json.put("sizeInBlocks", node.getSizeInBlocks());
        json.put("firstBlock", node.getFirstBlock());
        if (node.getColor() != null) {
            json.put("color", node.getColor().getRGB());
        }
        
        // Guardar hijos
        if (node.getChildren() != null && node.getChildren().size() > 0) {
            JSONArray hijosArray = new JSONArray();
            Node<FileSystemNode> current = node.getChildren().getHead();
            while (current != null) {
                JSONObject hijoJson = new JSONObject();
                guardarNodo(hijoJson, current.data);
                hijosArray.add(hijoJson);
                current = current.next;
            }
            json.put("hijos", hijosArray);
        }
    }
    
    // ===== CARGAR SISTEMA DE ARCHIVOS =====
    private static void cargarFileSystem(FileSystemNode root, Disk disk) {
        File file = new File(FS_FILE);
        if (!file.exists()) {
            System.out.println("No hay archivo de filesystem para cargar");
            return;
        }
        
        try {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(new FileReader(file));
            
            // Cargar disco
            JSONArray bloquesArray = (JSONArray) json.get("bloques");
            Disk.Block[] blocks = disk.getBlocks();
            for (Object obj : bloquesArray) {
                JSONObject blockJson = (JSONObject) obj;
                int index = ((Long) blockJson.get("index")).intValue();
                boolean libre = (Boolean) blockJson.get("libre");
                int next = ((Long) blockJson.get("next")).intValue();
                
                blocks[index].setLibre(libre);
                blocks[index].setNext(next);
                
                if (blockJson.containsKey("color")) {
                    int colorRGB = ((Long) blockJson.get("color")).intValue();
                    blocks[index].setColor(new Color(colorRGB));
                }
            }
            
            // Cargar árbol (reemplazar root)
            JSONObject rootJson = (JSONObject) json.get("root");
            cargarNodo(root, rootJson, null);
            
        } catch (Exception e) {
            System.err.println("Error cargando filesystem: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // ===== CARGAR UN NODO RECURSIVAMENTE =====
    private static void cargarNodo(FileSystemNode node, JSONObject json, FileSystemNode parent) {
        node.setName((String) json.get("nombre"));
        node.setOwner((String) json.get("owner"));
        node.setDirectory((Boolean) json.get("isDirectory"));
        node.setSizeInBlocks(((Long) json.get("sizeInBlocks")).intValue());
        node.setFirstBlock(((Long) json.get("firstBlock")).intValue());
        node.setParent(parent);
        
        if (json.containsKey("color")) {
            int colorRGB = ((Long) json.get("color")).intValue();
            node.setColor(new Color(colorRGB));
        }
        
        // Cargar hijos
        node.setChildren(new LinkedList<>());
        if (json.containsKey("hijos")) {
            JSONArray hijosArray = (JSONArray) json.get("hijos");
            for (Object obj : hijosArray) {
                JSONObject hijoJson = (JSONObject) obj;
                FileSystemNode hijo = new FileSystemNode();
                cargarNodo(hijo, hijoJson, node);
                node.getChildren().add(hijo);
            }
        }
    }
    
    // ===== GUARDAR PROCESOS =====
    private static void guardarProcesos(LinkedList<Process> procesos) {
        JSONArray procesosArray = new JSONArray();
        
        Node<Process> current = procesos.getHead();
        while (current != null) {
            Process p = current.data;
            JSONObject pJson = new JSONObject();
            
            pJson.put("id", p.getId());
            pJson.put("estado", p.getEstado());
            pJson.put("operacion", p.getOperacion());
            pJson.put("owner", p.getOwner());
            pJson.put("tamano", p.getTamanio());
            pJson.put("bloqueSolicitado", p.getBloqueSolicitado());
            pJson.put("nombreArchivo", p.getNombreArchivo());
            
            // No guardamos referencias a objetos FileSystemNode porque son complejas
            // En su lugar guardamos la ruta
            if (p.getArchivo() != null) {
                pJson.put("rutaArchivo", getRuta(p.getArchivo()));
            }
            if (p.getPadre() != null) {
                pJson.put("rutaPadre", getRuta(p.getPadre()));
            }
            
            procesosArray.add(pJson);
            current = current.next;
        }
        
        JSONObject json = new JSONObject();
        json.put("procesos", procesosArray);
        json.put("contadorId", Process.getContadorId()); // Necesitas getter estático
        
        try (FileWriter writer = new FileWriter(PROCS_FILE)) {
            writer.write(json.toJSONString());
            writer.flush();
        } catch (IOException e) {
            System.err.println("Error guardando procesos: " + e.getMessage());
        }
    }
    
    // ===== CARGAR PROCESOS =====
    private static void cargarProcesos(LinkedList<Process> procesos) {
        File file = new File(PROCS_FILE);
        if (!file.exists()) {
            System.out.println("No hay archivo de procesos para cargar");
            return;
        }
        
        try {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(new FileReader(file));
            
            JSONArray procesosArray = (JSONArray) json.get("procesos");
            
            // Limpiar lista actual
            while (procesos.size() > 0) {
                procesos.remove(procesos.get(0));
            }
            
            // Cargar procesos
            for (Object obj : procesosArray) {
                JSONObject pJson = (JSONObject) obj;
                
                // Por ahora solo recreamos procesos básicos
                // Las referencias a archivos se reconstruirán después
                String operacion = (String) pJson.get("operacion");
                String owner = (String) pJson.get("owner");
                String nombreArchivo = (String) pJson.get("nombreArchivo");
                int tamano = ((Long) pJson.get("tamano")).intValue();
                
                Process p = new Process(operacion, nombreArchivo, owner, tamano, null);
                p.setEstado((String) pJson.get("estado"));
                p.setBloqueSolicitado(((Long) pJson.get("bloqueSolicitado")).intValue());
                
                procesos.add(p);
            }
            
            // Restaurar contador de IDs
            if (json.containsKey("contadorId")) {
                Process.setContadorId(((Long) json.get("contadorId")).intValue());
            }
            
        } catch (Exception e) {
            System.err.println("Error cargando procesos: " + e.getMessage());
        }
    }
    
    // ===== UTILIDAD: Obtener ruta de un nodo =====
    private static String getRuta(FileSystemNode node) {
        if (node == null) return "";
        if (node.getParent() == null) return node.getName();
        return getRuta(node.getParent()) + "/" + node.getName();
    }
}
