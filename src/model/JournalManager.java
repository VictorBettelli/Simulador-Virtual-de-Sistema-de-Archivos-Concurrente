/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;

/**
 *
 * @author luisf
 */
import util.LinkedList;
import util.Node;
import java.io.*;

public class JournalManager {
    private LinkedList<JournalEntry> entradas;
    private static final String JOURNAL_FILE = "data/journal.txt";
    
    public JournalManager() {
        this.entradas = new LinkedList<>();
        File dir = new File("data");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        cargarJournal();
    }
    
    // NUEVO MÉTODO: Limpiar todo el journal
    public synchronized void limpiar() {
        this.entradas = new LinkedList<>(); // Crear nueva lista vacía
        guardarJournal(); // Guardar el journal vacío
        System.out.println("🧹 Journal limpiado completamente");
    }
    
    // Agregar entrada PENDIENTE
    public synchronized void agregarEntrada(JournalEntry entrada) {
        entradas.add(entrada);
        guardarJournal();
        System.out.println("📝 Journal: " + entrada.getOperacion() + " PENDIENTE - " + entrada.getRutaArchivo());
    }
    
    // Confirmar entrada (COMMIT)
    public synchronized void confirmarEntrada(int id) {
        Node<JournalEntry> current = entradas.getHead();
        while (current != null) {
            if (current.data.getId() == id) {
                current.data.setEstado("CONFIRMADA");
                guardarJournal();
                System.out.println("✅ Journal: Entrada " + id + " CONFIRMADA");
                return;
            }
            current = current.next;
        }
    }
    
    // Confirmar la última entrada de un archivo
    public synchronized void confirmarUltimaEntrada(String rutaArchivo) {
        Node<JournalEntry> current = entradas.getHead();
        JournalEntry ultima = null;
        while (current != null) {
            if (current.data.getRutaArchivo().equals(rutaArchivo) && 
                current.data.getEstado().equals("PENDIENTE")) {
                ultima = current.data;
            }
            current = current.next;
        }
        if (ultima != null) {
            ultima.setEstado("CONFIRMADA");
            guardarJournal();
        }
    }
    
    // Obtener entradas pendientes
    public synchronized LinkedList<JournalEntry> getEntradasPendientes() {
        LinkedList<JournalEntry> pendientes = new LinkedList<>();
        Node<JournalEntry> current = entradas.getHead();
        while (current != null) {
            if (current.data.getEstado().equals("PENDIENTE")) {
                pendientes.add(current.data);
            }
            current = current.next;
        }
        return pendientes;
    }
    
    // Deshacer operaciones pendientes (para recuperación ante fallos)
    public void deshacerPendientes(FileSystemNode root, Disk disk) {
        LinkedList<JournalEntry> pendientes = getEntradasPendientes();
        Node<JournalEntry> current = pendientes.getHead();

        while (current != null) {
            JournalEntry entry = current.data;
            System.out.println("🔄 Deshaciendo: " + entry.getOperacion() + " " + entry.getRutaArchivo());

            // Solo deshacer si es una operación que afecta al sistema
            if (entry.getOperacion().equals("CREATE")) {
                deshacerCreate(entry, root, disk);
            } else if (entry.getOperacion().equals("DELETE")) {
                deshacerDelete(entry, root, disk);
            } else if (entry.getOperacion().equals("UPDATE")) {
                deshacerUpdate(entry, root);
            }

            entry.setEstado("DESHECHA");
            current = current.next;
        }

        guardarJournal();
    }
    
    private void deshacerCreate(JournalEntry entry, FileSystemNode root, Disk disk) {
        // Buscar el archivo por ruta y eliminarlo
        FileSystemNode archivo = buscarPorRuta(root, entry.getRutaArchivo());
        if (archivo != null && !archivo.isDirectory()) {
            // Liberar bloques
            disk.liberarBloques(archivo.getFirstBlock(), archivo.getColor());
            
            // Eliminar del padre
            FileSystemNode padre = archivo.getParent();
            if (padre != null && padre.getChildren() != null) {
                padre.getChildren().remove(archivo);
            }
            System.out.println("   Archivo eliminado: " + entry.getRutaArchivo());
        }
    }
    
    private void deshacerDelete(JournalEntry entry, FileSystemNode root, Disk disk) {
        // Recrear el archivo
        String[] partes = entry.getRutaArchivo().split("/");
        String nombre = partes[partes.length - 1];
        String rutaPadre = entry.getRutaArchivo().substring(0, entry.getRutaArchivo().length() - nombre.length());
        
        FileSystemNode padre = buscarPorRuta(root, rutaPadre);
        if (padre == null) padre = root;
        
        FileSystemNode archivo = new FileSystemNode();
        archivo.setName(nombre);
        archivo.setOwner(entry.getOwner());
        archivo.setDirectory(false);
        archivo.setSizeInBlocks(entry.getTamanio());
        archivo.setFirstBlock(entry.getPrimerBloque());
        
        // Reasignar bloques (asumiendo que siguen libres)
        if (disk.asignarBloquesExactos(entry.getPrimerBloque(), entry.getTamanio(), archivo.getColor())) {
            if (padre.getChildren() == null) padre.setChildren(new LinkedList<>());
            padre.getChildren().add(archivo);
            archivo.setParent(padre);
            System.out.println("   Archivo restaurado: " + entry.getRutaArchivo());
        }
    }
    
    private void deshacerUpdate(JournalEntry entry, FileSystemNode root) {
        FileSystemNode archivo = buscarPorRuta(root, entry.getRutaArchivo());
        if (archivo != null) {
            archivo.setName(entry.getNombreAnterior());
            System.out.println("   Nombre restaurado: " + entry.getNombreAnterior());
        }
    }
    
    private FileSystemNode buscarPorRuta(FileSystemNode node, String ruta) {
        if (ruta.equals("/") || ruta.equals("")) return node;
        String[] partes = ruta.split("/");
        FileSystemNode current = node;
        
        for (String parte : partes) {
            if (parte.isEmpty()) continue;
            if (current.getChildren() != null) {
                Node<FileSystemNode> child = current.getChildren().getHead();
                boolean encontrado = false;
                while (child != null) {
                    if (child.data.getName().equals(parte)) {
                        current = child.data;
                        encontrado = true;
                        break;
                    }
                    child = child.next;
                }
                if (!encontrado) return null;
            } else {
                return null;
            }
        }
        return current;
    }
    
    // Guardar journal en archivo (SOBREESCRIBE)
    public void guardarJournal() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(JOURNAL_FILE, false))) { // false = sobrescribir
            Node<JournalEntry> current = entradas.getHead();
            while (current != null) {
                JournalEntry e = current.data;
                writer.println(e.getId() + "|" + 
                              e.getOperacion() + "|" + 
                              e.getRutaArchivo() + "|" + 
                              (e.getOwner() != null ? e.getOwner() : "") + "|" + 
                              e.getPrimerBloque() + "|" + 
                              e.getTamanio() + "|" + 
                              e.getEstado() + "|" + 
                              (e.getNombreAnterior() != null ? e.getNombreAnterior() : "") + "|" + 
                              (e.getNombreNuevo() != null ? e.getNombreNuevo() : ""));
                current = current.next;
            }
            System.out.println("💾 Journal guardado en " + JOURNAL_FILE);
        } catch (IOException e) {
            System.err.println("Error guardando journal: " + e.getMessage());
        }
    }
    
    // Cargar journal desde archivo
    private void cargarJournal() {
        File file = new File(JOURNAL_FILE);
        if (!file.exists()) return;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split("\\|", -1);
                if (partes.length >= 7) {
                    int id = Integer.parseInt(partes[0]);
                    String operacion = partes[1];
                    String ruta = partes[2];
                    String owner = partes[3];
                    int primerBloque = Integer.parseInt(partes[4]);
                    int tamanio = Integer.parseInt(partes[5]);
                    String estado = partes[6];
                    String nombreAnterior = partes.length > 7 ? partes[7] : null;
                    String nombreNuevo = partes.length > 8 ? partes[8] : null;
                    
                    JournalEntry entry;
                    if ("UPDATE".equals(operacion)) {
                        entry = new JournalEntry(operacion, ruta, nombreAnterior, nombreNuevo);
                    } else if ("CREATE".equals(operacion)) {
                        entry = new JournalEntry(operacion, ruta, owner, primerBloque, tamanio);
                    } else {
                        entry = new JournalEntry(operacion, ruta, owner, primerBloque);
                    }
                    entry.setEstado(estado);
                    entradas.add(entry);
                }
            }
        } catch (IOException e) {
            System.err.println("Error cargando journal: " + e.getMessage());
        }
    }
    
    public LinkedList<JournalEntry> getEntradas() {
        return entradas;
    }
}