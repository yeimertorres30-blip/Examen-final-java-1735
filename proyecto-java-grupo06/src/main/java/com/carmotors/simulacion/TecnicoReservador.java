package com.carmotors.simulacion;

import com.carmotors.servicio.OrdenServicioServicio;
import com.carmotors.vista.VistaSimulacionReserva;

public class TecnicoReservador implements Runnable {

    private final String nombreHilo;
    private final OrdenServicioServicio servicio;
    private final int ordenId;
    private final int repuestoId;
    private final PreparadorEscenario preparador;
    private final VistaSimulacionReserva vista;

    public TecnicoReservador(String nombreHilo, OrdenServicioServicio servicio,
                             int ordenId, int repuestoId,
                             PreparadorEscenario preparador, VistaSimulacionReserva vista) {
        this.nombreHilo = nombreHilo;
        this.servicio = servicio;
        this.ordenId = ordenId;
        this.repuestoId = repuestoId;
        this.preparador = preparador;
        this.vista = vista;
    }

    @Override
    public void run() {
        vista.mostrarIntento(nombreHilo);
        try {
            servicio.agregarRepuesto(ordenId, repuestoId, 1);
            int disponible = preparador.consultarDisponible(repuestoId);
            vista.mostrarExito(nombreHilo, disponible);
        } catch (IllegalArgumentException e) {
            vista.mostrarFallo(nombreHilo);
        } catch (Exception e) {
            vista.mostrarError(nombreHilo, e.getMessage());
        }
    }
}