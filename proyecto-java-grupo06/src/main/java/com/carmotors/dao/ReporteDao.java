package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.AsignacionTecnico;
import com.carmotors.modelo.RepuestoUsado;
import com.carmotors.modelo.ServicioReporte;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Lee de MySQL las filas que usan los reportes. Solo consultas, sin agrupar: eso lo hace el servicio. */
public class ReporteDao implements FuenteReportes {

    @Override
    public List<ServicioReporte> servicios() throws SQLException {
        String sql = "SELECT s.id, t.nombre AS tipo, s.nombre_servicio, e.nombre AS estado, "
                + "s.fecha_recepcion, s.fecha_entrega "
                + "FROM servicios s "
                + "LEFT JOIN mantenimiento_tipos t ON t.id = s.mantenimiento_tipo_id "
                + "LEFT JOIN servicio_estados e ON e.id = s.estado_id "
                + "ORDER BY s.id";
        List<ServicioReporte> lista = new ArrayList<>();
        try (Connection conexion = ConexionFactory.getInstancia().abrir();
             PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new ServicioReporte(
                        rs.getInt("id"),
                        rs.getString("tipo"),
                        rs.getString("nombre_servicio"),
                        rs.getString("estado"),
                        rs.getObject("fecha_recepcion", LocalDateTime.class),
                        rs.getObject("fecha_entrega", LocalDateTime.class)));
            }
        }
        return lista;
    }

    @Override
    public List<AsignacionTecnico> asignaciones() throws SQLException {
        String sql = "SELECT st.servicio_id, tc.id AS tecnico_id, tc.nombre "
                + "FROM servicios_tecnicos st "
                + "JOIN tecnicos tc ON tc.id = st.tecnico_id";
        List<AsignacionTecnico> lista = new ArrayList<>();
        try (Connection conexion = ConexionFactory.getInstancia().abrir();
             PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new AsignacionTecnico(rs.getInt("servicio_id"), rs.getInt("tecnico_id"),
                        rs.getString("nombre")));
            }
        }
        return lista;
    }

    @Override
    public List<RepuestoUsado> repuestosUsados() throws SQLException {
        String sql = "SELECT sr.servicio_id, r.id AS repuesto_id, r.nombre, sr.cantidad_usada, "
                + "sr.precio_unitario_venta "
                + "FROM servicios_repuestos sr "
                + "JOIN repuestos r ON r.id = sr.repuesto_id";
        List<RepuestoUsado> lista = new ArrayList<>();
        try (Connection conexion = ConexionFactory.getInstancia().abrir();
             PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new RepuestoUsado(rs.getInt("servicio_id"), rs.getInt("repuesto_id"),
                        rs.getString("nombre"), rs.getInt("cantidad_usada"),
                        rs.getBigDecimal("precio_unitario_venta")));
            }
        }
        return lista;
    }
}