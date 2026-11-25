package com.carmotors.controlador;

import com.carmotors.dao.FacturaDao;
import com.carmotors.dao.FuenteReportes;
import com.carmotors.dao.JdbcRepuestoDao;
import com.carmotors.dao.JdbcProveedorDao;
import com.carmotors.modelo.AsignacionTecnico;
import com.carmotors.modelo.ConsumoRepuesto;
import com.carmotors.modelo.DatosFactura;
import com.carmotors.modelo.ProductividadTecnico;
import com.carmotors.modelo.Proveedor;
import com.carmotors.modelo.Repuesto;
import com.carmotors.modelo.ServicioReporte;
import com.carmotors.servicio.ReporteServicio;
import com.carmotors.servicio.ReporteTexto;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * La vista (el menú) le pasa lo que escribió el usuario y el controlador llama al servicio.
 */
public class ReporteControlador {
    private final ReporteServicio reportes;
    private final FacturaDao facturas;
    private final FuenteReportes fuente;
    private final JdbcRepuestoDao repuestosDao;
    private final JdbcProveedorDao proveedoresDao;
    private final Path carpetaReportes;

    public ReporteControlador(ReporteServicio reportes, FacturaDao facturas) {
        this(reportes, facturas, null, new JdbcRepuestoDao(), new JdbcProveedorDao(), Path.of("reportes"));
    }

    public ReporteControlador(ReporteServicio reportes, FacturaDao facturas, FuenteReportes fuente,
                              JdbcRepuestoDao repuestosDao, JdbcProveedorDao proveedoresDao,
                              Path carpetaReportes) {
        this.reportes = reportes;
        this.facturas = facturas;
        this.fuente = fuente;
        this.repuestosDao = repuestosDao;
        this.proveedoresDao = proveedoresDao;
        this.carpetaReportes = carpetaReportes == null ? Path.of("reportes") : carpetaReportes;
    }

    public Map<String, Long> serviciosPorNombre(String desde, String hasta) throws SQLException {
        return reportes.serviciosPorNombre(convertirFecha(desde), convertirFecha(hasta));
    }

    public Map<String, Long> serviciosPorTipo(String desde, String hasta) throws SQLException {
        return reportes.serviciosPorTipo(convertirFecha(desde), convertirFecha(hasta));
    }

    public Map<String, Long> serviciosPorEstado(String desde, String hasta) throws SQLException {
        return reportes.serviciosPorEstado(convertirFecha(desde), convertirFecha(hasta));
    }

    public Map<YearMonth, Long> serviciosPorMes(String desde, String hasta) throws SQLException {
        return reportes.serviciosPorMes(convertirFecha(desde), convertirFecha(hasta));
    }

    public List<ProductividadTecnico> productividadPorTecnico(String desde, String hasta) throws SQLException {
        return reportes.productividadPorTecnico(convertirFecha(desde), convertirFecha(hasta));
    }

    public List<ConsumoRepuesto> consumoRepuestos(String desde, String hasta) throws SQLException {
        return reportes.consumoRepuestos(convertirFecha(desde), convertirFecha(hasta));
    }

    public List<ServicioReporte> serviciosCompletados(String desde, String hasta) throws SQLException {
        return reportes.serviciosCompletados(convertirFecha(desde), convertirFecha(hasta));
    }

    /** Datos para la factura de una orden. Solo si la orden existe y está "Completado". */
    public DatosFactura datosFactura(int servicioId) throws SQLException {
        DatosFactura datos = facturas.buscar(servicioId);
        if (datos == null) {
            throw new IllegalArgumentException("La orden " + servicioId + " no existe.");
        }
        if (!datos.sePuedeFacturar()) {
            throw new IllegalArgumentException("La orden " + servicioId + " está en estado '"
                    + datos.getEstado() + "'. Solo se factura cuando está 'Completado'.");
        }
        return datos;
    }

    public Path exportarVentas(String desde, String hasta) throws SQLException {
        LocalDate d = convertirFecha(desde);
        LocalDate h = convertirFecha(hasta);
        List<ReporteTexto.LineaVenta> lineas = facturas.listarVentasEnPeriodo(d, h);
        String texto = ReporteTexto.ventas(d, h, lineas);
        String nombre = "reporte_ventas_" + d + "_" + h + ".txt";
        return ReporteTexto.escribir(carpetaReportes, nombre, texto);
    }

    /** Reporte detallado de órdenes de servicio del periodo. */
    public Path exportarOrdenes(String desde, String hasta) throws SQLException {
        LocalDate d = convertirFecha(desde);
        LocalDate h = convertirFecha(hasta);
        List<ServicioReporte> ordenes = reportes.ordenesDelPeriodo(d, h);
        Map<Integer, String> tecnicos = mapaTecnicosPorOrden();
        String texto = ReporteTexto.ordenes(d, h, ordenes, tecnicos);
        String nombre = "reporte_ordenes_" + d + "_" + h + ".txt";
        return ReporteTexto.escribir(carpetaReportes, nombre, texto);
    }

    /** Reporte detallado de inventario (stock actual + alerta). */
    public Path exportarInventario() throws SQLException {
        List<Repuesto> lista = repuestosDao.listarTodos();
        for (Repuesto r : lista) {
            Optional<Proveedor> p = proveedoresDao.buscarPorId(r.getProveedorId());
            p.ifPresent(r::setProveedor);
        }
        String texto = ReporteTexto.inventario(lista);
        String nombre = "reporte_inventario_" + LocalDate.now() + ".txt";
        return ReporteTexto.escribir(carpetaReportes, nombre, texto);
    }

    // Exportación de los reportes que ya se muestran en consola (opciones 1-4)

    public Path exportarServiciosMasSolicitados(String desde, String hasta) throws SQLException {
        LocalDate d = convertirFecha(desde);
        LocalDate h = convertirFecha(hasta);
        String texto = ReporteTexto.serviciosMasSolicitados(d, h, reportes.serviciosPorNombre(d, h));
        return ReporteTexto.escribir(carpetaReportes, "servicios_solicitados_" + d + "_" + h + ".txt", texto);
    }

    public Path exportarProductividad(String desde, String hasta) throws SQLException {
        LocalDate d = convertirFecha(desde);
        LocalDate h = convertirFecha(hasta);
        String texto = ReporteTexto.productividad(d, h, reportes.productividadPorTecnico(d, h));
        return ReporteTexto.escribir(carpetaReportes, "productividad_" + d + "_" + h + ".txt", texto);
    }

    public Path exportarConsumoRepuestos(String desde, String hasta) throws SQLException {
        LocalDate d = convertirFecha(desde);
        LocalDate h = convertirFecha(hasta);
        String texto = ReporteTexto.consumoRepuestos(d, h, reportes.consumoRepuestos(d, h));
        return ReporteTexto.escribir(carpetaReportes, "consumo_repuestos_" + d + "_" + h + ".txt", texto);
    }

    public Path exportarServiciosCompletados(String desde, String hasta) throws SQLException {
        LocalDate d = convertirFecha(desde);
        LocalDate h = convertirFecha(hasta);
        String texto = ReporteTexto.serviciosCompletados(d, h, reportes.serviciosCompletados(d, h));
        return ReporteTexto.escribir(carpetaReportes, "servicios_completados_" + d + "_" + h + ".txt", texto);
    }

    private Map<Integer, String> mapaTecnicosPorOrden() throws SQLException {
        if (fuente == null) {
            return Map.of();
        }
        Map<Integer, List<String>> agrupa = new HashMap<>();
        for (AsignacionTecnico a : fuente.asignaciones()) {
            agrupa.computeIfAbsent(a.getServicioId(), k -> new java.util.ArrayList<>()).add(a.getTecnico());
        }
        return agrupa.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(", ", e.getValue())));
    }

    /**
     * Si el usuario escribe mal la fecha, lanza IllegalArgumentException con un mensaje claro,
     * para que el menú muestre el error en vez de cerrarse.
     */
    private LocalDate convertirFecha(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Escribe una fecha, por ejemplo 2026-10-01");
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha inválida: '" + texto + "'. Escríbela así: 2026-10-01");
        }
    }
}