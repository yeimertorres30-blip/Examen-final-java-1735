package com.carmotors.servicio;

import com.carmotors.modelo.ConsumoRepuesto;
import com.carmotors.modelo.ProductividadTecnico;
import com.carmotors.modelo.Repuesto;
import com.carmotors.modelo.ServicioReporte;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;


public final class ReporteTexto {

    private static final int ANCHO = 96;
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ReporteTexto() {
    }


    public static Path escribir(Path carpeta, String nombreArchivo, String contenido) {
        try {
            Files.createDirectories(carpeta);
            Path archivo = carpeta.resolve(nombreArchivo);
            Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
            return archivo.toAbsolutePath();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir el reporte en " + carpeta + "/" + nombreArchivo, e);
        }
    }

    public static String ventas(LocalDate desde, LocalDate hasta, List<LineaVenta> lineas) {
        StringBuilder t = new StringBuilder();
        encabezado(t, "REPORTE DETALLADO DE VENTAS / FACTURACIÓN", desde, hasta);
        t.append(String.format("%-8s %-19s %-22s %-10s %14s %14s %14s%n",
                "N° Fac.", "Fecha emisión", "Cliente", "Placa", "Subtotal", "IVA", "Total"));
        t.append(lineaSimple());

        BigDecimal sumaSub = BigDecimal.ZERO;
        BigDecimal sumaIva = BigDecimal.ZERO;
        BigDecimal sumaTot = BigDecimal.ZERO;

        if (lineas.isEmpty()) {
            t.append("(sin facturas en el periodo)").append('\n');
        } else {
            for (LineaVenta l : lineas) {
                t.append(String.format("%-8d %-19s %-22s %-10s %14s %14s %14s%n",
                        l.numero(),
                        l.fechaEmision() == null ? "-" : l.fechaEmision().format(FECHA_HORA),
                        recortar(l.cliente(), 22),
                        recortar(l.placa(), 10),
                        dinero(l.subtotal()),
                        dinero(l.iva()),
                        dinero(l.total())));
                sumaSub = sumaSub.add(nvl(l.subtotal()));
                sumaIva = sumaIva.add(nvl(l.iva()));
                sumaTot = sumaTot.add(nvl(l.total()));
            }
        }
        t.append(lineaSimple());
        t.append(String.format("%-62s %14s %14s %14s%n",
                "TOTALES (" + lineas.size() + " factura(s))",
                dinero(sumaSub), dinero(sumaIva), dinero(sumaTot)));
        t.append(lineaDoble());
        return t.toString();
    }

    public static String ordenes(LocalDate desde, LocalDate hasta,
                                 List<ServicioReporte> ordenes,
                                 Map<Integer, String> tecnicosPorOrden) {
        StringBuilder t = new StringBuilder();
        encabezado(t, "REPORTE DETALLADO DE ÓRDENES DE SERVICIO", desde, hasta);
        t.append(String.format("%-6s %-14s %-28s %-12s %-20s %-19s%n",
                "ID", "Tipo", "Servicio", "Estado", "Técnico(s)", "Recepción"));
        t.append(lineaSimple());

        if (ordenes.isEmpty()) {
            t.append("(sin órdenes en el periodo)").append('\n');
        } else {
            for (ServicioReporte s : ordenes) {
                String nombre = (s.getNombreServicio() == null || s.getNombreServicio().isBlank())
                        ? "(sin nombre)" : s.getNombreServicio();
                String tecnicos = tecnicosPorOrden.getOrDefault(s.getId(), "-");
                t.append(String.format("%-6d %-14s %-28s %-12s %-20s %-19s%n",
                        s.getId(),
                        recortar(s.getTipo(), 14),
                        recortar(nombre, 28),
                        recortar(s.getEstado(), 12),
                        recortar(tecnicos, 20),
                        s.getFechaRecepcion() == null ? "-" : s.getFechaRecepcion().format(FECHA_HORA)));
            }
        }
        t.append(lineaSimple());
        t.append("Total de órdenes: ").append(ordenes.size()).append('\n');
        t.append(lineaDoble());
        return t.toString();
    }

    public static String inventario(List<Repuesto> repuestos) {
        StringBuilder t = new StringBuilder();
        encabezadoSinPeriodo(t, "REPORTE DETALLADO DE INVENTARIO / REPUESTOS");
        t.append(String.format("%-5s %-26s %-8s %-8s %14s %-18s %s%n",
                "ID", "Repuesto", "Stock", "Mínimo", "P. venta", "Proveedor", "Alerta"));
        t.append(lineaSimple());

        int bajos = 0;
        if (repuestos.isEmpty()) {
            t.append("(catálogo vacío)").append('\n');
        } else {
            for (Repuesto r : repuestos) {
                boolean bajo = r.estaBajoStockMinimo();
                if (bajo) {
                    bajos++;
                }
                String proveedor = r.getProveedor() != null && r.getProveedor().getNombre() != null
                        ? r.getProveedor().getNombre()
                        : ("#" + r.getProveedorId());
                t.append(String.format("%-5d %-26s %-8d %-8d %14s %-18s %s%n",
                        r.getId(),
                        recortar(r.getNombre(), 26),
                        r.getCantidadStock(),
                        r.getStockMinimo(),
                        dinero(r.getPrecioVenta()),
                        recortar(proveedor, 18),
                        bajo ? "[BAJO STOCK]" : ""));
            }
        }
        t.append(lineaSimple());
        t.append("Total repuestos: ").append(repuestos.size())
                .append(" | Con stock bajo: ").append(bajos).append('\n');
        t.append(lineaDoble());
        return t.toString();
    }

    public static String serviciosMasSolicitados(LocalDate desde, LocalDate hasta, Map<String, Long> conteo) {
        StringBuilder t = new StringBuilder();
        encabezado(t, "SERVICIOS MÁS SOLICITADOS", desde, hasta);
        long total = 0;
        for (Map.Entry<String, Long> e : conteo.entrySet()) {
            t.append(String.format("%-40s %6d%n", e.getKey(), e.getValue()));
            total += e.getValue();
        }
        t.append(lineaSimple());
        t.append("Total de órdenes: ").append(total).append('\n');
        t.append(lineaDoble());
        return t.toString();
    }

    public static String productividad(LocalDate desde, LocalDate hasta, List<ProductividadTecnico> lista) {
        StringBuilder t = new StringBuilder();
        encabezado(t, "PRODUCTIVIDAD POR TÉCNICO", desde, hasta);
        t.append(String.format("%-30s %12s %12s %10s%n", "Técnico", "Asignadas", "Completadas", "%"));
        t.append(lineaSimple());
        for (ProductividadTecnico p : lista) {
            t.append(String.format("%-30s %12d %12d %9d%%%n",
                    recortar(p.getTecnico(), 30),
                    p.getAsignadas(),
                    p.getCompletadas(),
                    p.getPorcentajeCompletadas()));
        }
        t.append(lineaDoble());
        return t.toString();
    }

    public static String consumoRepuestos(LocalDate desde, LocalDate hasta, List<ConsumoRepuesto> lista) {
        StringBuilder t = new StringBuilder();
        encabezado(t, "CONSUMO DE REPUESTOS", desde, hasta);
        t.append(String.format("%-30s %10s %12s %14s%n", "Repuesto", "Unidades", "Órdenes", "Valor"));
        t.append(lineaSimple());
        BigDecimal total = BigDecimal.ZERO;
        for (ConsumoRepuesto c : lista) {
            t.append(String.format("%-30s %10d %12d %14s%n",
                    recortar(c.getRepuesto(), 30),
                    c.getUnidades(),
                    c.getOrdenes(),
                    dinero(c.getValorTotal())));
            total = total.add(nvl(c.getValorTotal()));
        }
        t.append(lineaSimple());
        t.append(String.format("%-54s %14s%n", "Valor total consumido", dinero(total)));
        t.append(lineaDoble());
        return t.toString();
    }

    public static String serviciosCompletados(LocalDate desde, LocalDate hasta, List<ServicioReporte> lista) {
        StringBuilder t = new StringBuilder();
        encabezado(t, "SERVICIOS COMPLETADOS", desde, hasta);
        for (ServicioReporte s : lista) {
            String nombre = (s.getNombreServicio() == null || s.getNombreServicio().isBlank())
                    ? "(sin nombre)" : s.getNombreServicio();
            String entrega = s.getFechaEntrega() != null
                    ? s.getFechaEntrega().format(FECHA_HORA) : "Sin fecha de entrega";
            t.append(String.format("#%d | %-28s | %-12s | Recepción: %s | Entrega: %s%n",
                    s.getId(),
                    recortar(nombre, 28),
                    s.getEstado(),
                    s.getFechaRecepcion().format(FECHA_HORA),
                    entrega));
        }
        t.append(lineaSimple());
        t.append("Total: ").append(lista.size()).append('\n');
        t.append(lineaDoble());
        return t.toString();
    }

    public record LineaVenta(int numero, LocalDateTime fechaEmision, String cliente, String placa,
                             BigDecimal subtotal, BigDecimal iva, BigDecimal total) {
    }

    private static void encabezado(StringBuilder t, String titulo, LocalDate desde, LocalDate hasta) {
        t.append(lineaDoble());
        t.append(centrar("CAR MOTORS - TALLER AUTOMOTRIZ")).append('\n');
        t.append(centrar(titulo)).append('\n');
        t.append(centrar("Periodo: " + desde + " a " + hasta)).append('\n');
        t.append(centrar("Generado: " + LocalDateTime.now().format(FECHA_HORA))).append('\n');
        t.append(lineaDoble());
    }

    private static void encabezadoSinPeriodo(StringBuilder t, String titulo) {
        t.append(lineaDoble());
        t.append(centrar("CAR MOTORS - TALLER AUTOMOTRIZ")).append('\n');
        t.append(centrar(titulo)).append('\n');
        t.append(centrar("Generado: " + LocalDateTime.now().format(FECHA_HORA))).append('\n');
        t.append(lineaDoble());
    }

    private static String lineaDoble() {
        return "=".repeat(ANCHO) + "\n";
    }

    private static String lineaSimple() {
        return "-".repeat(ANCHO) + "\n";
    }

    private static String centrar(String texto) {
        int margen = Math.max(0, (ANCHO - texto.length()) / 2);
        return " ".repeat(margen) + texto;
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null || texto.isBlank()) {
            return "-";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }

    static String dinero(BigDecimal valor) {
        if (valor == null) {
            valor = BigDecimal.ZERO;
        }
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        DecimalFormat formato = new DecimalFormat("#,##0.00", simbolos);
        return "$ " + formato.format(valor.setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}