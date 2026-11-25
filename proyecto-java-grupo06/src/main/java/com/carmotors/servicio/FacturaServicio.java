package com.carmotors.servicio;

import com.carmotors.dao.FacturaDao;
import com.carmotors.modelo.CicloOrden;
import com.carmotors.modelo.DatosFactura;
import com.carmotors.modelo.Factura;
import com.carmotors.notificacion.GestorNotificaciones;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Emite la factura de una orden Completado:
 * calcula el IVA, la guarda en MySQL, pasa la orden a Facturado y escribe el .txt.
 */
public class FacturaServicio {

    /** Resultado de emitir: la factura con su número y el archivo .txt que se escribió. */
    public record FacturaEmitida(Factura factura, Path archivo) { }

    private final OrdenServicioServicio.Conexiones conexiones;
    private final FacturaDao facturas;
    private final OrdenServicioServicio ordenes;
    private final Path carpeta;
    private final GestorNotificaciones notificaciones;

    public FacturaServicio(OrdenServicioServicio.Conexiones conexiones, FacturaDao facturas,
                           OrdenServicioServicio ordenes, Path carpeta) {
        this(conexiones, facturas, ordenes, carpeta, null);
    }

    /** Constructor con notificaciones (Observer). */
    public FacturaServicio(OrdenServicioServicio.Conexiones conexiones, FacturaDao facturas,
                           OrdenServicioServicio ordenes, Path carpeta,
                           GestorNotificaciones notificaciones) {
        this.conexiones = Objects.requireNonNull(conexiones);
        this.facturas = Objects.requireNonNull(facturas);
        this.ordenes = Objects.requireNonNull(ordenes);
        this.carpeta = Objects.requireNonNull(carpeta);
        this.notificaciones = notificaciones;
    }

    /** Cómo quedaría la factura, sin guardar nada. Solo para órdenes Completado. */
    public Factura vistaPrevia(int orden) throws SQLException {
        validar(orden);
        try (Connection c = conexiones.abrir()) {
            DatosFactura datos = facturas.buscar(c, orden);
            if (datos == null) {
                throw new IllegalArgumentException("La orden " + orden + " no existe.");
            }
            if (!datos.sePuedeFacturar()) {
                throw new IllegalArgumentException("La orden " + orden + " está en '" + datos.getEstado()
                        + "'. Solo se factura cuando está '" + CicloOrden.COMPLETADO + "'.");
            }
            return Factura.calcular(datos, LocalDateTime.now());
        }
    }

    /**
     * Emite la factura. La orden pasa a Facturado y la factura se guarda en la MISMA transacción.
     * El .txt se escribe después de confirmar: si falla el archivo, la factura ya quedó guardada
     * y se puede volver a generar con regenerarTexto.
     */
    public FacturaEmitida emitir(int orden) throws SQLException {
        validar(orden);
        Factura factura;
        try (Connection c = conexiones.abrir()) {
            c.setAutoCommit(false);
            try {
                ordenes.marcarFacturada(c, orden);   // bloquea la orden, revisa que esté Completado y la cambia
                DatosFactura datos = facturas.buscar(c, orden);
                Factura calculada = Factura.calcular(datos, LocalDateTime.now());
                factura = calculada.conNumero(facturas.insertar(c, calculada));
                c.commit();
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
        if (notificaciones != null) {
            notificaciones.notificarFacturaEmitida(orden, factura.getNumero());
        }
        return new FacturaEmitida(factura, escribir(factura));
    }

    /** Vuelve a escribir el .txt de una orden que ya fue facturada. */
    public FacturaEmitida regenerarTexto(int orden) throws SQLException {
        validar(orden);
        Factura factura;
        try (Connection c = conexiones.abrir()) {
            factura = facturas.buscarEmitida(c, orden);
        }
        if (factura == null) {
            throw new IllegalArgumentException("La orden " + orden + " todavía no tiene factura.");
        }
        return new FacturaEmitida(factura, escribir(factura));
    }

    private Path escribir(Factura factura) {
        Path archivo = carpeta.resolve(String.format("factura-%06d-orden-%d.txt",
                factura.getNumero(), factura.getOrdenId()));
        try {
            Files.createDirectories(carpeta);
            Files.writeString(archivo, FacturaTexto.generar(factura), StandardCharsets.UTF_8);
            return archivo.toAbsolutePath();
        } catch (IOException e) {
            throw new IllegalStateException("La factura " + factura.getNumero() + " quedó guardada en la base, "
                    + "pero no se pudo escribir el archivo " + archivo + ". Usa 'Regenerar .txt'.", e);
        }
    }

    private static void validar(int orden) {
        if (orden <= 0) {
            throw new IllegalArgumentException("Orden inválida.");
        }
    }
}