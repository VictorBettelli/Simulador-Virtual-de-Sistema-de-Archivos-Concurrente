/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package test;

/**
 *
 * @author luisf
 */

import model.FileSystemNode;
import model.Disk;
import model.Process;
import model.Scheduler;
import util.LinkedList;
import util.Node;
import java.awt.Color;
import java.io.FileReader;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class TestCaseLoader {

    public static TestCase cargarTestCase(String rutaArchivo) {
        TestCase testCase = new TestCase();

        try {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(new FileReader(rutaArchivo));

            testCase.setTestId((String) json.get("test_id"));

            Long head = (Long) json.get("initial_head");
            testCase.setInitialHead(head != null ? head.intValue() : 0);

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

            // Guardar system_files
            JSONObject systemFilesJson = (JSONObject) json.get("system_files");
            if (systemFilesJson != null) {
                TestCase.SystemFiles sysFiles = new TestCase.SystemFiles();
                LinkedList<TestCase.SystemFiles.FileEntry> entries = new LinkedList<>();
                for (Object key : systemFilesJson.keySet()) {
                    String blockStr = (String) key;
                    JSONObject fileJson = (JSONObject) systemFilesJson.get(key);
                    String name = (String) fileJson.get("name");
                    Long blocks = (Long) fileJson.get("blocks");
                    int block = Integer.parseInt(blockStr);
                    TestCase.SystemFiles.FileEntry entry =
                            new TestCase.SystemFiles.FileEntry(block, name, blocks.intValue());
                    entries.add(entry);
                }
                sysFiles.setFiles(entries);
                testCase.setSystemFiles(sysFiles);
            }

            return testCase;

        } catch (Exception e) {
            System.err.println("Error cargando caso de prueba: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static void aplicarTestCase(TestCase testCase, FileSystemNode root, Disk disk, Scheduler scheduler, LinkedList<Process> procesosCreados) {
        if (testCase == null) return;

        scheduler.setCabezaActual(testCase.getInitialHead());

        FileSystemNode[] bloqueAFile = new FileSystemNode[Disk.SIZE];

        // 1. Crear archivos del sistema (system_files)
        TestCase.SystemFiles systemFiles = testCase.getSystemFiles();
        if (systemFiles != null) {
            LinkedList<TestCase.SystemFiles.FileEntry> entries = systemFiles.getFiles();
            if (entries != null) {
                Node<TestCase.SystemFiles.FileEntry> current = entries.getHead();
                while (current != null) {
                    TestCase.SystemFiles.FileEntry entry = current.data;
                    int bloqueInicial = entry.getBlock();
                    String nombre = entry.getName();
                    int cantidad = entry.getBlocks();

                    FileSystemNode fileNode = new FileSystemNode();
                    fileNode.setName(nombre);
                    fileNode.setOwner("system");
                    fileNode.setDirectory(false);
                    fileNode.setSizeInBlocks(cantidad);
                    fileNode.setParent(root);

                    Color color = disk.generateUniqueColor();
                    boolean ok = disk.asignarBloquesExactos(bloqueInicial, cantidad, color);
                    if (!ok) {
                        System.err.println("Error al asignar bloques para " + nombre);
                        current = current.next;
                        continue;
                    }
                    fileNode.setFirstBlock(bloqueInicial);
                    fileNode.setColor(color);

                    if (root.getChildren() == null) root.setChildren(new LinkedList<>());
                    root.getChildren().add(fileNode);

                    for (int i = 0; i < cantidad; i++) {
                        bloqueAFile[bloqueInicial + i] = fileNode;
                    }

                    current = current.next;
                }
            }
        }

        // 2. Crear procesos a partir de las solicitudes (requests)
        LinkedList<TestCase.Request> requests = testCase.getRequests();
        if (requests != null) {
            Node<TestCase.Request> current = requests.getHead();
            while (current != null) {
                TestCase.Request req = current.data;
                int bloque = req.getPos();
                String operacion = req.getOp();

                FileSystemNode archivo = bloqueAFile[bloque];
                if (archivo == null) {
                    archivo = new FileSystemNode();
                    archivo.setName("temp_" + bloque);
                    archivo.setDirectory(false);
                }

                Process p = new Process(operacion, archivo, "test");
                p.setBloqueSolicitado(bloque);
                procesosCreados.add(p);  // Ya no se agrega al scheduler directamente

                current = current.next;
            }
        }

        System.out.println("✅ Caso de prueba aplicado: " + testCase.getTestId());
    }
}