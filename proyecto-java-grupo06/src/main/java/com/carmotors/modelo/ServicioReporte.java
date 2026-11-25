package com.carmotors.modelo;

import java.time.LocalDateTime;
import java.util.Objects;

/** Una orden de servicio tal como la necesitan los reportes. */
public class ServicioReporte {
    private final int id;
    private final String tipo;
    private final String nombreServicio;
    private final String estado;
    private final LocalDateTime fechaRecepcion;
    private final LocalDateTime fechaEntrega;

    /** Para órdenes sin nombre de servicio (las creadas antes de que existiera la lista). */
    public ServicioReporte(int id, String tipo, String estado, LocalDateTime fechaRecepcion) {
        this(id, tipo, null, estado, fechaRecepcion, null);
    }

    /** Compatibilidad: sin fecha de entrega. */
    public ServicioReporte(int id, String tipo, String estado, LocalDateTime fechaRecepcion,
                           LocalDateTime fechaEntrega) {
        this(id, tipo, null, estado, fechaRecepcion, fechaEntrega);
    }

    /** Compatibilidad: sin fecha de entrega. */
    public ServicioReporte(int id, String tipo, String nombreServicio, String estado,
                           LocalDateTime fechaRecepcion) {
        this(id, tipo, nombreServicio, estado, fechaRecepcion, null);
    }

    /** nombreServicio puede ser null: el reporte la cuenta como "Sin nombre".
     *  fechaEntrega puede ser null si aún no se entregó. */
    public ServicioReporte(int id, String tipo, String nombreServicio, String estado,
                           LocalDateTime fechaRecepcion, LocalDateTime fechaEntrega) {
        this.id = id;
        this.tipo = Objects.requireNonNullElse(tipo, "Sin tipo");
        this.nombreServicio = nombreServicio;
        this.estado = Objects.requireNonNullElse(estado, "Sin estado");
        this.fechaRecepcion = Objects.requireNonNull(fechaRecepcion);
        this.fechaEntrega = fechaEntrega;
    }

    public int getId() { return id; }
    public String getTipo() { return tipo; }
    public String getNombreServicio() { return nombreServicio; }
    public String getEstado() { return estado; }
    public LocalDateTime getFechaRecepcion() { return fechaRecepcion; }
    public LocalDateTime getFechaEntrega() { return fechaEntrega; }
}