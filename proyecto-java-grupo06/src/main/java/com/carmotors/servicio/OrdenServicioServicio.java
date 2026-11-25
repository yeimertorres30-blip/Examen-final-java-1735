package com.carmotors.servicio;

import com.carmotors.dao.*;
import com.carmotors.modelo.CatalogoServicios;
import com.carmotors.modelo.CicloOrden;
import com.carmotors.modelo.OrdenServicio;
import com.carmotors.modelo.ReservaRepuesto;
import com.carmotors.modelo.Tecnico;
import com.carmotors.notificacion.GestorNotificaciones;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

/** Creación, consulta, técnicos, repuestos y ciclo de estados de las órdenes de servicio. */
public class OrdenServicioServicio {
    @FunctionalInterface
    public interface Conexiones { Connection abrir() throws SQLException; }

    /** Un trabajo que se hace dentro de una transacción. */
    @FunctionalInterface
    private interface Trabajo<T> { T hacer(Connection c) throws SQLException; }

    private final Conexiones conexiones;
    private final OrdenServicioDao ordenes = new OrdenServicioDao();
    private final EstadoServicioDao estados = new EstadoServicioDao();
    private final TecnicoOrdenDao tecnicos = new TecnicoOrdenDao();
    private final RepuestoOrdenDao repuestos = new RepuestoOrdenDao();
    private final GestorNotificaciones notificaciones;

    /** Candado para que solo un hilo a la vez ejecute la sección crítica de reserva de stock. */
    private static final Object CANDADO_RESERVA = new Object();

    public OrdenServicioServicio(Conexiones conexiones) {
        this(conexiones, null);
    }

    public OrdenServicioServicio(Conexiones conexiones, GestorNotificaciones notificaciones) {
        this.conexiones = java.util.Objects.requireNonNull(conexiones);
        this.notificaciones = notificaciones;
    }

    public int crear(int vehiculo, int tipo, String nombreServicio, String problema,
                     BigDecimal manoObra) throws SQLException {
        nombreServicio = ValidacionRegistro.nombreServicio(nombreServicio);
        return crearOrden(vehiculo, tipo, nombreServicio.strip(), problema, manoObra);
    }

    public int crear(int vehiculo, int tipo, String problema, BigDecimal manoObra) throws SQLException {
        return crearOrden(vehiculo, tipo, null, problema, manoObra);
    }

    private int crearOrden(int vehiculo, int tipo, String nombreServicio, String problema,
                           BigDecimal manoObra) throws SQLException {
        if (vehiculo <= 0 || tipo <= 0) throw new IllegalArgumentException("Vehículo y tipo deben ser positivos.");
        problema = ValidacionRegistro.textoConLetras(problema, 65535, "Descripción del problema");
        if (manoObra == null || manoObra.signum() < 0 || manoObra.scale() > 2
                || manoObra.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("Mano de obra inválida.");
        try (Connection c = conexiones.abrir()) {
            OrdenServicio orden = new OrdenServicio(vehiculo, tipo, problema.strip(), manoObra,
                    estados.idPorNombre(c, CicloOrden.PENDIENTE));
            orden.setNombreServicio(nombreServicio);
            return ordenes.insertar(c, orden);
        }
    }

    public OrdenServicio consultar(int id) throws SQLException {
        if (id <= 0) throw new IllegalArgumentException("Orden inválida.");
        try (Connection c = conexiones.abrir()) { return ordenes.buscar(c, id, false); }
    }

    public List<OrdenServicio> listar() throws SQLException {
        try (Connection c = conexiones.abrir()) { return ordenes.listar(c); }
    }

    public List<Integer> tecnicosDe(int orden) throws SQLException {
        if (orden <= 0) throw new IllegalArgumentException("Orden inválida.");
        try (Connection c = conexiones.abrir()) {
            ordenes.buscar(c, orden, false);
            return tecnicos.listar(c, orden);
        }
    }

    public void asignarTecnico(int orden, int tecnico) throws SQLException {
        if (orden <= 0 || tecnico <= 0) throw new IllegalArgumentException("Orden y técnico deben ser positivos.");
        try (Connection c = conexiones.abrir()) {
            c.setAutoCommit(false);
            try {
                ordenes.buscar(c, orden, true);
                if (!tecnicos.listar(c, orden).contains(tecnico)) tecnicos.asignar(c, orden, tecnico);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }

    public int registrarTecnico(String nombre) throws SQLException {
        nombre = ValidacionRegistro.nombrePersona(nombre, "Nombre del técnico");
        try (Connection conexion = conexiones.abrir()) {
            return new TecnicoDao().insertar(conexion, nombre.trim());
        }
    }

    public List<Tecnico> listarTecnicos() throws SQLException {
        try (Connection conexion = conexiones.abrir()) {
            return new TecnicoDao().listar(conexion);
        }
    }

    /** La lista vive en CatalogoServicios para que órdenes y reportes usen la misma. */
    public List<String> serviciosSugeridos() {
        return CatalogoServicios.NOMBRES;
    }

    // ------------------------------------------------------------------
    // Ciclo de estados y repuestos
    // ------------------------------------------------------------------

    /** Nombre del estado actual de la orden, por ejemplo "En Proceso". */
    public String estadoDe(int orden) throws SQLException {
        if (orden <= 0) throw new IllegalArgumentException("Orden inválida.");
        try (Connection c = conexiones.abrir()) {
            return nombreEstado(c, ordenes.buscar(c, orden, false));
        }
    }

    /**
     * Aparta (reserva) unidades de un repuesto para la orden. El stock NO baja todavía:
     * baja una sola vez, cuando la orden pasa a Completado.
     * Disponible = stock - lo que ya está reservado por otras órdenes.
     *
     * Sección crítica protegida con synchronized para evitar race condition
     * cuando varios técnicos intentan reservar el mismo repuesto al mismo tiempo.
     */
    public void agregarRepuesto(int orden, int repuesto, int cantidad) throws SQLException {
        if (orden <= 0 || repuesto <= 0) throw new IllegalArgumentException("Orden y repuesto deben ser positivos.");
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");

        // Solo un hilo puede entrar aquí a la vez (thread-safe)
        synchronized (CANDADO_RESERVA) {
            enTransaccion(c -> {
                String estado = nombreEstado(c, ordenes.buscar(c, orden, true));
                if (!CicloOrden.admiteRepuestos(estado)) {
                    throw new IllegalArgumentException("Solo se agregan repuestos a órdenes Pendiente o En Proceso. "
                            + "La orden " + orden + " está en '" + estado + "'.");
                }
                RepuestoOrdenDao.RepuestoBloqueado datos = repuestos.bloquear(c, repuesto);
                if (datos.precioVenta() == null) {
                    throw new IllegalArgumentException("El repuesto '" + datos.nombre() + "' no tiene precio de venta.");
                }
                int disponible = datos.stock() - repuestos.totalReservado(c, repuesto);
                if (cantidad > disponible) {
                    throw new IllegalArgumentException(String.format(
                            "Stock insuficiente de '%s'. Pediste %d y hay %d disponibles (stock %d, ya reservado %d).",
                            datos.nombre(), cantidad, Math.max(disponible, 0), datos.stock(), datos.stock() - disponible));
                }
                if (repuestos.reservasDe(c, orden).stream()
                        .anyMatch(reserva -> reserva.getRepuestoId() == repuesto && reserva.estaActiva())) {
                    throw new IllegalArgumentException("Ese repuesto ya está asignado a la orden.");
                }
                repuestos.reservar(c, orden, repuesto, cantidad, datos.precioVenta());
                return null;
            });
        }
    }

    /** Reservas de la orden con su estado: RESERVADO, DESCONTADO o CANCELADO. */
    public List<ReservaRepuesto> repuestosDe(int orden) throws SQLException {
        if (orden <= 0) throw new IllegalArgumentException("Orden inválida.");
        try (Connection c = conexiones.abrir()) {
            ordenes.buscar(c, orden, false);
            return repuestos.reservasDe(c, orden);
        }
    }

    /**
     * Pasa la orden a su estado siguiente y devuelve el nombre del estado nuevo.
     * Al llegar a Completado descuenta del stock lo reservado (una sola vez).
     * El paso a Facturado no se hace aquí: lo hace FacturaServicio al emitir la factura.
     */
    public String avanzarEstado(int orden) throws SQLException {
        if (orden <= 0) throw new IllegalArgumentException("Orden inválida.");
        return enTransaccion(c -> {
            OrdenServicio datos = ordenes.buscar(c, orden, true);
            String actual = nombreEstado(c, datos);
            String siguiente = CicloOrden.siguiente(actual).orElseThrow(() -> new IllegalArgumentException(
                    "La orden " + orden + " está en '" + actual + "' y ya no puede avanzar."));
            if (siguiente.equals(CicloOrden.FACTURADO)) {
                throw new IllegalArgumentException("La orden " + orden + " está Completado. "
                        + "Para pasarla a Facturado emite la factura en el menú Facturación.");
            }
            aplicar(c, orden, siguiente);
            if (notificaciones != null) {
                notificaciones.notificarCambioEstado(orden, actual, siguiente);
            }
            return siguiente;
        });
    }

    /**
     * Pasa una orden de Completado a Facturado usando la transacción que abrió facturación,
     * para que la factura y el cambio de estado se guarden juntos o no se guarde ninguno.
     */
    public void marcarFacturada(Connection c, int orden) throws SQLException {
        String actual = nombreEstado(c, ordenes.buscar(c, orden, true));
        if (!actual.equals(CicloOrden.COMPLETADO)) {
            throw new IllegalArgumentException("La orden " + orden + " está en '" + actual
                    + "'. Solo se factura cuando está 'Completado'.");
        }
        aplicar(c, orden, CicloOrden.FACTURADO);
        if (notificaciones != null) {
            notificaciones.notificarCambioEstado(orden, actual, CicloOrden.FACTURADO);
        }
    }

    private void aplicar(Connection c, int orden, String nuevo) throws SQLException {
        if (nuevo.equals(CicloOrden.COMPLETADO)) {
            for (ReservaRepuesto reserva : repuestos.reservasActivas(c, orden)) {
                repuestos.descontar(c, reserva);
            }
        }
        LocalDateTime entrega = nuevo.equals(CicloOrden.ENTREGADO) ? LocalDateTime.now() : null;
        ordenes.actualizarEstado(c, orden, estados.idPorNombre(c, nuevo), entrega);
    }

    private String nombreEstado(Connection c, OrdenServicio orden) throws SQLException {
        return estados.buscar(c, orden.getEstadoId()).getNombre();
    }

    /** Abre una conexión, hace el trabajo y confirma. Si algo falla, deshace todo (rollback). */
    private <T> T enTransaccion(Trabajo<T> trabajo) throws SQLException {
        try (Connection c = conexiones.abrir()) {
            c.setAutoCommit(false);
            try {
                T resultado = trabajo.hacer(c);
                c.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }

    public List<com.carmotors.modelo.EstadoServicio> listarEstados() throws SQLException {
        try (Connection c = conexiones.abrir()) {
            return estados.listar(c);
        }
    }

    public com.carmotors.modelo.EstadoServicio cambiarEstado(int orden, int estadoId) throws SQLException {
        if (orden <= 0 || estadoId <= 0) {
            throw new IllegalArgumentException("Orden y estado deben ser positivos.");
        }
        return enTransaccion(c -> {
            OrdenServicio datos = ordenes.buscar(c, orden, true);
            String actual = nombreEstado(c, datos);
            com.carmotors.modelo.EstadoServicio destino = estados.buscar(c, estadoId);
            if (!CicloOrden.siguiente(actual).orElse("").equals(destino.getNombre())) {
                throw new IllegalArgumentException("Cambio de estado inválido de " + actual
                        + " a " + destino.getNombre() + ".");
            }
            if (CicloOrden.FACTURADO.equals(destino.getNombre())) {
                throw new IllegalArgumentException("Emite la factura desde el menú Facturación.");
            }
            aplicar(c, orden, destino.getNombre());
            if (notificaciones != null) {
                notificaciones.notificarCambioEstado(orden, actual, destino.getNombre());
            }
            return destino;
        });
    }

    public void asignarRepuesto(int orden, int repuesto, int cantidad) throws SQLException {
        agregarRepuesto(orden, repuesto, cantidad);
    }
}