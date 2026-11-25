package com.carmotors.simulacion;

import com.carmotors.config.ConexionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Prepara datos de prueba para la simulación (stock = 1, órdenes).
 */
public class PreparadorEscenario {

    private final String nombreRepuesto;

    public PreparadorEscenario(String nombreRepuesto) {
        this.nombreRepuesto = nombreRepuesto;
    }

    public void preparar() throws SQLException {
        try (Connection c = ConexionFactory.getInstancia().abrir()) {
            c.setAutoCommit(false);
            try {
                cancelarReservasActivas(c);
                asegurarRepuestoConStockUno(c);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private void cancelarReservasActivas(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "UPDATE reservas_repuesto SET estado = 'CANCELADO' WHERE repuesto_id = "
                        + "(SELECT id FROM (SELECT id FROM repuestos WHERE nombre = ?) t) "
                        + "AND estado = 'RESERVADO'")) {
            p.setString(1, nombreRepuesto);
            p.executeUpdate();
        }
    }

    private void asegurarRepuestoConStockUno(Connection c) throws SQLException {
        Integer id = null;
        try (PreparedStatement p = c.prepareStatement(
                "SELECT id FROM repuestos WHERE nombre = ?")) {
            p.setString(1, nombreRepuesto);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    id = rs.getInt(1);
                }
            }
        }

        if (id == null) {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO repuestos (nombre, repuesto_tipo_id, proveedor_id, "
                            + "precio_compra, precio_venta, cantidad_stock, stock_minimo) "
                            + "VALUES (?, 1, 1, 15000.00, 25000.00, 1, 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, nombreRepuesto);
                p.executeUpdate();
            }
        } else {
            try (PreparedStatement p = c.prepareStatement(
                    "UPDATE repuestos SET cantidad_stock = 1 WHERE id = ?")) {
                p.setInt(1, id);
                p.executeUpdate();
            }
        }
    }

    public int obtenerIdRepuesto() throws SQLException {
        try (Connection c = ConexionFactory.getInstancia().abrir();
             PreparedStatement p = c.prepareStatement(
                     "SELECT id FROM repuestos WHERE nombre = ?")) {
            p.setString(1, nombreRepuesto);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("No se encontró el repuesto de prueba.");
                }
                return rs.getInt(1);
            }
        }
    }

    public int consultarDisponible(int repuestoId) throws SQLException {
        try (Connection c = ConexionFactory.getInstancia().abrir()) {
            int stock;
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT cantidad_stock FROM repuestos WHERE id = ?")) {
                p.setInt(1, repuestoId);
                try (ResultSet rs = p.executeQuery()) {
                    rs.next();
                    stock = rs.getInt(1);
                }
            }
            int reservado;
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT COALESCE(SUM(cantidad), 0) FROM reservas_repuesto "
                            + "WHERE repuesto_id = ? AND estado = 'RESERVADO'")) {
                p.setInt(1, repuestoId);
                try (ResultSet rs = p.executeQuery()) {
                    rs.next();
                    reservado = rs.getInt(1);
                }
            }
            return stock - reservado;
        }
    }

    public int crearOrdenPrueba(String problema) throws SQLException {
        try (Connection c = ConexionFactory.getInstancia().abrir();
             PreparedStatement p = c.prepareStatement(
                     "INSERT INTO servicios (vehiculo_id, mantenimiento_tipo_id, fecha_recepcion, "
                             + "descripcion_problema, costo_mano_obra, estado_id, nombre_servicio) "
                             + "VALUES (1, 1, NOW(), ?, 50000.00, "
                             + "(SELECT id FROM servicio_estados WHERE nombre = 'Pendiente' LIMIT 1), "
                             + "'Cambio de aceite')",
                     Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, problema);
            p.executeUpdate();
            try (ResultSet rs = p.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("No se pudo crear la orden de prueba.");
                }
                return rs.getInt(1);
            }
        }
    }
}