package com.carmotors.simulacion;

import com.carmotors.controlador.ControladorSimulacionReserva;

public class MainReservaConcurrente {

    public static void main(String[] args) {
        ControladorSimulacionReserva controlador = new ControladorSimulacionReserva();
        controlador.ejecutar();
    }
}