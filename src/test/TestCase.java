/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package test;

/**
 *
 * @author luisf
 */
import util.LinkedList;

public class TestCase {
    private String testId;
    private int initialHead;
    private LinkedList<Request> requests;
    private SystemFiles systemFiles;
    
    public static class Request {
        private int pos;
        private String op; // READ, WRITE, CREATE, DELETE
        
        public Request(int pos, String op) {
            this.pos = pos;
            this.op = op;
        }
        
        public int getPos() { return pos; }
        public String getOp() { return op; }
    }
    
    public static class SystemFiles {
        // Mapa de bloque -> archivo
        private LinkedList<FileEntry> files;
        
        public static class FileEntry {
            private int block;
            private String name;
            private int blocks;
            
            public FileEntry(int block, String name, int blocks) {
                this.block = block;
                this.name = name;
                this.blocks = blocks;
            }
            
            public int getBlock() { return block; }
            public String getName() { return name; }
            public int getBlocks() { return blocks; }
            
        }
        public void setFiles(LinkedList<FileEntry> files) {this.files = files;}
            
        public LinkedList<FileEntry> getFiles() {return files;}
 }
    
    // Getters y setters
    public String getTestId() { return testId; }
    public void setTestId(String testId) { this.testId = testId; }
    
    public int getInitialHead() { return initialHead; }
    public void setInitialHead(int initialHead) { this.initialHead = initialHead; }
    
    public LinkedList<Request> getRequests() { return requests; }
    public void setRequests(LinkedList<Request> requests) { this.requests = requests; }
    
    public SystemFiles getSystemFiles() { return systemFiles; }
    public void setSystemFiles(SystemFiles systemFiles) { this.systemFiles = systemFiles; }


  
}
