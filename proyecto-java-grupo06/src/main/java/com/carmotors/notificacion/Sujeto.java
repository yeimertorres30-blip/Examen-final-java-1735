package com.carmotors.notificacion;

public interface Sujeto {
    void agregarObservador(Observador observador);
    void eliminarObservador(Observador observador);
    void notificarObservadores(String mensaje);
}