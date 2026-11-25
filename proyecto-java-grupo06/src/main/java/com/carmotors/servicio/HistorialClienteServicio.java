package com.carmotors.servicio;

import com.carmotors.dao.HistorialClienteDao;
import com.carmotors.modelo.HistorialVehiculo;
import com.carmotors.notificacion.GestorNotificaciones;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

public class HistorialClienteServicio {
    private final HistorialClienteDao dao;
    private final GestorNotificaciones notificaciones;

    public HistorialClienteServicio(HistorialClienteDao dao) {
        this(dao, null);
    }

    public HistorialClienteServicio(HistorialClienteDao dao, GestorNotificaciones notificaciones) {
        this.dao = dao;
        this.notificaciones = notificaciones;
    }

    public List<HistorialVehiculo> historial(String documento) throws SQLException {
        if (documento == null || documento.isBlank()) {
            throw new IllegalArgumentException("Ingresa el documento del cliente.");
        }
        return dao.buscar(documento.strip());
    }

    public List<Recordatorio> recordatorios(LocalDate fechaConsulta) throws SQLException {
        List<Recordatorio> pendientes = new ArrayList<>();
        for (HistorialVehiculo vehiculo : dao.buscar(null)) {
            LocalDate ultimaEntrega = ultimaEntregaPreventiva(vehiculo);
            if (ultimaEntrega != null) {
                LocalDate fecha = ultimaEntrega.plusMonths(6);
                if (!fecha.isAfter(fechaConsulta)) {
                    Recordatorio aviso = new Recordatorio(vehiculo.getNombre(), vehiculo.getEmail(),
                            vehiculo.getPlaca(), fecha);
                    pendientes.add(aviso);
                    if (notificaciones != null) {
                        notificaciones.notificarRecordatorio(
                                aviso.getCliente(),
                                aviso.getEmail(),
                                aviso.getPlaca(),
                                aviso.getFecha().toString());
                    }
                }
            }
        }
        pendientes.sort(Comparator.comparing((Recordatorio aviso) -> aviso.getFecha())
                .thenComparing(aviso -> aviso.getPlaca()));
        return pendientes;
    }

    private LocalDate ultimaEntregaPreventiva(HistorialVehiculo vehiculo) {
        List<HistorialVehiculo.Visita> preventivos = vehiculo.getVisitas().stream()
                .filter(visita -> "Preventivo".equals(visita.getTipo()))
                .filter(visita -> "Entregado".equals(visita.getEstado()) && visita.getEntrega() != null)
                .toList();

        LocalDate ultimaEntrega = null;
        for (HistorialVehiculo.Visita visita : preventivos) {
            LocalDate entrega = visita.getEntrega();
            if (ultimaEntrega == null || entrega.isAfter(ultimaEntrega)) {
                ultimaEntrega = entrega;
            }
        }
        return ultimaEntrega;
    }

    public static class Recordatorio {
        private final String cliente;
        private final String email;
        private final String placa;
        private final LocalDate fecha;

        public Recordatorio(String cliente, String email, String placa, LocalDate fecha) {
            this.cliente = cliente;
            this.email = email;
            this.placa = placa;
            this.fecha = fecha;
        }

        public String getCliente() {
            return cliente;
        }

        public String getEmail() {
            return email;
        }

        public String getPlaca() {
            return placa;
        }

        public LocalDate getFecha() {
            return fecha;
        }
    }
}