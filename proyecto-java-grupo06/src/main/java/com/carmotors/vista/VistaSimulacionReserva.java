package com.carmotors.vista;

public class VistaSimulacionReserva {

    public void mostrarInicio(String nombreRepuesto) {
        System.out.println("Iniciando simulación de reserva concurrente para el repuesto '"
                + nombreRepuesto + "'...");
    }

    public void mostrarStockInicial(int stock) {
        System.out.println("Stock inicial: " + stock);
        System.out.println();
    }

    public void mostrarIntento(String tecnico) {
        System.out.println(tecnico + ": Intentando reservar el repuesto...");
    }

    public void mostrarExito(String tecnico, int stockFinal) {
        System.out.println(tecnico + ": Verificando stock. Hay 1 disponible.");
        System.out.println(tecnico + ": ¡Reserva exitosa! Stock actualizado a " + stockFinal + ".");
    }

    public void mostrarFallo(String tecnico) {
        System.out.println(tecnico + ": Verificando stock. Hay 0 disponible.");
        System.out.println(tecnico + ": Fallo en la reserva. No hay suficiente stock.");
    }

    public void mostrarError(String tecnico, String mensaje) {
        System.out.println(tecnico + ": Fallo en la reserva. " + mensaje);
    }

    public void mostrarFin(int stockFinal) {
        System.out.println();
        System.out.println("Simulación finalizada.");
        System.out.println("Stock final del repuesto: " + stockFinal);
    }

    public void mostrarErrorGeneral(String mensaje) {
        System.err.println("Error en la simulación: " + mensaje);
    }
}