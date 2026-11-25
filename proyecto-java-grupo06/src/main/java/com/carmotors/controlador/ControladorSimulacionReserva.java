package com.carmotors.controlador;

import com.carmotors.config.ConexionFactory;
import com.carmotors.servicio.OrdenServicioServicio;
import com.carmotors.simulacion.PreparadorEscenario;
import com.carmotors.simulacion.TecnicoReservador;
import com.carmotors.vista.VistaSimulacionReserva;

public class ControladorSimulacionReserva {

    private static final String NOMBRE_REPUESTO = "Filtro de Aceite XY-123";

    private final VistaSimulacionReserva vista;
    private final PreparadorEscenario preparador;
    private final OrdenServicioServicio servicio;

    public ControladorSimulacionReserva() {
        this.vista = new VistaSimulacionReserva();
        this.preparador = new PreparadorEscenario(NOMBRE_REPUESTO);
        this.servicio = new OrdenServicioServicio(ConexionFactory.getInstancia()::abrir);
    }

    public void ejecutar() {
        vista.mostrarInicio(NOMBRE_REPUESTO);
        try {
            preparador.preparar();
            int repuestoId = preparador.obtenerIdRepuesto();
            vista.mostrarStockInicial(preparador.consultarDisponible(repuestoId));

            int ordenA = preparador.crearOrdenPrueba("Técnico A - simulación concurrente");
            int ordenB = preparador.crearOrdenPrueba("Técnico B - simulación concurrente");

            TecnicoReservador tecnicoA = new TecnicoReservador(
                    "Hilo-Técnico-A", servicio, ordenA, repuestoId, preparador, vista);
            TecnicoReservador tecnicoB = new TecnicoReservador(
                    "Hilo-Técnico-B", servicio, ordenB, repuestoId, preparador, vista);

            Thread hiloA = new Thread(tecnicoA, "Hilo-Técnico-A");
            Thread hiloB = new Thread(tecnicoB, "Hilo-Técnico-B");

            hiloA.start();
            Thread.sleep(10);
            hiloB.start();

            hiloA.join();
            hiloB.join();

            vista.mostrarFin(preparador.consultarDisponible(repuestoId));
        } catch (Exception e) {
            vista.mostrarErrorGeneral(e.getMessage());
            e.printStackTrace();
        }
    }
}