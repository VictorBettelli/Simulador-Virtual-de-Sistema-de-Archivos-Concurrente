/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package test;

/**
 *
 * @author luisf
 */
import test.TestCase;
import model.FileSystemNode;
import model.Disk;
import model.Process;
import util.LinkedList;
import util.Node;

import java.io.*;
import java.awt.Color;
import model.Scheduler;
import org.json.simple.*;
import org.json.simple.parser.*;

public class TestCaseLoader {
    
    public static TestCase cargarTestCase(String rutaArchivo) {
        TestCase testCase = new TestCase();
        
        try {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(new FileReader(rutaArchivo));
            
            // Leer test_id
            testCase.setTestId((String) json.get("test_id"));
            
            // Leer initial_head
            Long head = (Long) json.get("initial_head");
            testCase.setInitialHead(head != null ? head.intValue() : 0);
            
            // Leer requests
            LinkedList<TestCase.Request> requests = new LinkedList<>();
            JSONArray requestsArray = (JSONArray) json.get("requests");
            if (requestsArray != null) {
                for (Object obj : requestsArray) {
                    JSONObject reqJson = (JSONObject) obj;
                    Long pos = (Long) reqJson.get("pos");
                    String op = (String) reqJson.get("op");
                    requests.add(new TestCase.Request(pos.intValue(), op));
                }
            }
            testCase.setRequests(requests);
            
            // Leer system_files (si existe)
            JSONObject systemFilesJson = (JSONObject) json.get("system_files");
            if (systemFilesJson != null) {
                // Procesar archivos del sistema
                System.out.println("Archivos del sistema encontrados:");
                for (Object key : systemFilesJson.keySet()) {
                    String blockStr = (String) key;
                    JSONObject fileJson = (JSONObject) systemFilesJson.get(key);
                    String name = (String) fileJson.get("name");
                    Long blocks = (Long) fileJson.get("blocks");
                    System.out.println("  Bloque " + blockStr + ": " + name + " (" + blocks + " bloques)");
                }
            }
            
            return testCase;
            
        } catch (Exception e) {
            System.err.println("Error cargando caso de prueba: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    public static void aplicarTestCase(TestCase testCase, FileSystemNode root, Disk disk, Scheduler scheduler) {
        if (testCase == null) return;
        
        // Establecer cabeza inicial
        scheduler.setCabezaActual(testCase.getInitialHead());
        
        // Crear procesos a partir de requests
        LinkedList<TestCase.Request> requests = testCase.getRequests();
        if (requests != null) {
            Node<TestCase.Request> current = requests.getHead();
            while (current != null) {
                TestCase.Request req = current.data;
                
                // Crear un proceso para cada request
                FileSystemNode tempFile = new FileSystemNode();
                tempFile.setName("test_" + req.getPos());
                tempFile.setDirectory(false);
                
                Process p = new Process(req.getOp(), tempFile, "test");
                p.setBloqueSolicitado(req.getPos());
                
                scheduler.agregarProceso(p);
                
                current = current.next;
            }
        }
        
        // Aquí también podrías crear los archivos del sistema si existen
        System.out.println("✅ Caso de prueba aplicado: " + testCase.getTestId());
    }
}