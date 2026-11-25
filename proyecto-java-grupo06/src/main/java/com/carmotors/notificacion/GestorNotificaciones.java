package com.carmotors.notificacion;

import java.util.ArrayList;
import java.util.List;


public class GestorNotificaciones implements Sujeto {

    private final List<Observador> observadores = new ArrayList<>();

    @Override
    public void agregarObservador(Observador observador) {
        if (observador != null && !observadores.contains(observador)) {
            observadores.add(observador);
        }
    }

    @Override
    public void eliminarObservador(Observador observador) {
        observadores.remove(observador);
    }

    @Override
    public void notificarObservadores(String mensaje) {
        for (Observador o : observadores) {
            o.actualizar(mensaje);
        }
    }

    public void notificarCambioEstado(int ordenId, String estadoAnterior, String estadoNuevo) {
        notificarObservadores(String.format(
                "[ORDEN #%d] Cambió de estado: %s → %s",
                ordenId, estadoAnterior, estadoNuevo));
    }

    public void notificarFacturaEmitida(int ordenId, long numeroFactura) {
        notificarObservadores(String.format(
                "[FACTURA] Se emitió la factura N° %d para la orden #%d",
                numeroFactura, ordenId));
    }

    public void notificarRecordatorio(String cliente, String email, String placa, String fecha) {
        notificarObservadores(String.format(
                "[RECORDATORIO] Cliente %s (%s) – vehículo %s – mantenimiento preventivo vencido o por vencer el %s",
                cliente, email, placa, fecha));
    }
}