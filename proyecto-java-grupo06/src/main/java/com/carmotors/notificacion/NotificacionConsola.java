package com.carmotors.notificacion;

/**
 * Observador concreto: muestra las notificaciones en la consola (vista MVC).
 */
public class NotificacionConsola implements Observador {

    @Override
    public void actualizar(String mensaje) {
        System.out.println();
        System.out.println("══════════════════════════════════════");
        System.out.println("  NOTIFICACIÓN AL CLIENTE");
        System.out.println("══════════════════════════════════════");
        System.out.println("  " + mensaje);
        System.out.println("══════════════════════════════════════");
        System.out.println();
    }
}