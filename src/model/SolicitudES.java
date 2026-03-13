/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;

/**
 *
 * @author VictorB
 */
public class SolicitudES {
    private ProcesoHilo proceso;
    private int bloque;
    private String operacion;

    public SolicitudES(ProcesoHilo proceso, int bloque, String operacion) {
        this.proceso = proceso;
        this.bloque = bloque;
        this.operacion = operacion;
    }

    public ProcesoHilo getProceso() { return proceso; }
    public int getBloque() { return bloque; }
    public String getOperacion() { return operacion; }
}
