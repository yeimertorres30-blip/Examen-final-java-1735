package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.DatosFactura;
import com.carmotors.modelo.Factura;
import com.carmotors.modelo.RepuestoUsado;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Datos que necesita la factura de una orden y guardado de las facturas emitidas. */
public class FacturaDao {

    /** Devuelve los datos de la orden, o null si la orden no existe. Abre su propia conexión. */
    public DatosFactura buscar(int servicioId) throws SQLException {
        try (Connection conexion = ConexionFactory.getInstancia().abrir()) {
            return buscar(conexion, servicioId);
        }
    }

    /** Igual que buscar(int), pero dentro de una conexión (transacción) que ya está abierta. */
    public DatosFactura buscar(Connection conexion, int servicioId) throws SQLException {
        // 1. Datos de la orden, el cliente y el vehículo
        String sqlOrden = "SELECT e.nombre AS estado, c.nombre AS cliente, c.documento_identidad, "
                + "v.placa, s.costo_mano_obra "
                + "FROM servicios s "
                + "LEFT JOIN servicio_estados e ON e.id = s.estado_id "
                + "LEFT JOIN vehiculos v ON v.id = s.vehiculo_id "
                + "LEFT JOIN clientes c ON c.id = v.cliente_id "
                + "WHERE s.id = ?";

        // 2. Repuestos que se usaron en esa orden
        String sqlRepuestos = "SELECT r.id, r.nombre, sr.cantidad_usada, sr.precio_unitario_venta "
                + "FROM servicios_repuestos sr "
                + "JOIN repuestos r ON r.id = sr.repuesto_id "
                + "WHERE sr.servicio_id = ? "
                + "ORDER BY r.nombre";

        String estado, cliente, documento, placa;
        BigDecimal manoObra;

        try (PreparedStatement ps = conexion.prepareStatement(sqlOrden)) {
            ps.setInt(1, servicioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null; // la orden no existe
                }
                estado = rs.getString("estado");
                cliente = rs.getString("cliente");
                documento = rs.getString("documento_identidad");
                placa = rs.getString("placa");
                manoObra = rs.getBigDecimal("costo_mano_obra");
                if (manoObra == null) {
                    manoObra = BigDecimal.ZERO;
                }
            }
        }

        List<RepuestoUsado> repuestos = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(sqlRepuestos)) {
            ps.setInt(1, servicioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    repuestos.add(new RepuestoUsado(servicioId, rs.getInt("id"), rs.getString("nombre"),
                            rs.getInt("cantidad_usada"), rs.getBigDecimal("precio_unitario_venta")));
                }
            }
        }

        return new DatosFactura(servicioId, estado, cliente, documento, placa, manoObra, repuestos);
    }

    /** Guarda la factura en la tabla facturas y devuelve su número (id). */
    public int insertar(Connection conexion, Factura factura) throws SQLException {
        String sql = "INSERT INTO facturas (servicio_id, fecha_emision, total_repuestos, total_mano_obra, "
                + "impuestos, total_final) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, factura.getOrdenId());
            ps.setObject(2, factura.getFechaEmision());
            ps.setBigDecimal(3, factura.getTotalRepuestos());
            ps.setBigDecimal(4, factura.getManoObra());
            ps.setBigDecimal(5, factura.getImpuestos());
            ps.setBigDecimal(6, factura.getTotal());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("MySQL no devolvió el número de la factura.");
                }
                return rs.getInt(1);
            }
        }
    }

    /** La factura ya emitida de una orden (para volver a generar el .txt), o null si no tiene. */
    public Factura buscarEmitida(Connection conexion, int servicioId) throws SQLException {
        String sql = "SELECT id, fecha_emision, impuestos, total_final FROM facturas "
                + "WHERE servicio_id = ? ORDER BY id DESC LIMIT 1";
        int numero;
        LocalDateTime fecha;
        BigDecimal impuestos, total;
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, servicioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                numero = rs.getInt("id");
                fecha = rs.getObject("fecha_emision", LocalDateTime.class);
                impuestos = rs.getBigDecimal("impuestos");
                total = rs.getBigDecimal("total_final");
            }
        }
        return new Factura(numero, fecha, buscar(conexion, servicioId), impuestos, total);
    }
    public List<com.carmotors.servicio.ReporteTexto.LineaVenta> listarVentasEnPeriodo(
            java.time.LocalDate desde, java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT f.id, f.fecha_emision, f.total_repuestos, f.total_mano_obra, f.impuestos, f.total_final, "
                + "c.nombre AS cliente, v.placa "
                + "FROM facturas f "
                + "JOIN servicios s ON s.id = f.servicio_id "
                + "LEFT JOIN vehiculos v ON v.id = s.vehiculo_id "
                + "LEFT JOIN clientes c ON c.id = v.cliente_id "
                + "WHERE DATE(f.fecha_emision) BETWEEN ? AND ? "
                + "ORDER BY f.fecha_emision, f.id";
        List<com.carmotors.servicio.ReporteTexto.LineaVenta> lista = new ArrayList<>();
        try (Connection conexion = ConexionFactory.getInstancia().abrir();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setObject(1, desde);
            ps.setObject(2, hasta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal repuestos = rs.getBigDecimal("total_repuestos");
                    BigDecimal manoObra = rs.getBigDecimal("total_mano_obra");
                    if (repuestos == null) repuestos = BigDecimal.ZERO;
                    if (manoObra == null) manoObra = BigDecimal.ZERO;
                    BigDecimal subtotal = repuestos.add(manoObra);
                    lista.add(new com.carmotors.servicio.ReporteTexto.LineaVenta(
                            rs.getInt("id"),
                            rs.getObject("fecha_emision", LocalDateTime.class),
                            rs.getString("cliente"),
                            rs.getString("placa"),
                            subtotal,
                            rs.getBigDecimal("impuestos"),
                            rs.getBigDecimal("total_final")));
                }
            }
        }
        return lista;
    }
}
