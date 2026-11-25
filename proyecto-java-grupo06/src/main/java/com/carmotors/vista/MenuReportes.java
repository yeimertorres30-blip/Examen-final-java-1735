package com.carmotors.vista;

import com.carmotors.controlador.ReporteControlador;
import com.carmotors.modelo.DatosFactura;
import com.carmotors.modelo.RepuestoUsado;
import com.carmotors.modelo.ServicioReporte;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class MenuReportes {
    private final ReporteControlador reportes;

    public MenuReportes(ReporteControlador reportes) {
        this.reportes = reportes;
    }

    public void ejecutar(Scanner entrada) {
        while (true) {
            System.out.println("Reportes gerenciales");
            System.out.println("1. Servicios más solicitados");
            System.out.println("2. Productividad por técnico");
            System.out.println("3. Consumo de repuestos");
            System.out.println("4. Servicios completados");
            System.out.println("5. Exportar reporte detallado (.txt)");
            System.out.println("0. Volver");
            try {
                String opcion = leer(entrada, "Opción");
                if (opcion.equals("0")) {
                    return;
                }
                if (!opcion.equals("1") && !opcion.equals("2") && !opcion.equals("3")
                        && !opcion.equals("4") && !opcion.equals("5")) {
                    System.out.println("Opción inválida.");
                    continue;
                }

                if (opcion.equals("5")) {
                    exportarDetallado(entrada);
                    continue;
                }

                java.time.LocalDate inicio = EntradaConsola.fecha(entrada, System.out, "Desde (AAAA-MM-DD): ", false);
                java.time.LocalDate fin = EntradaConsola.fecha(entrada, System.out, "Hasta (AAAA-MM-DD): ", false);
                while (fin.isBefore(inicio)) {
                    System.out.println("La fecha final debe ser igual o posterior a la inicial.");
                    fin = EntradaConsola.fecha(entrada, System.out, "Hasta (AAAA-MM-DD): ", false);
                }
                String desde = inicio.toString();
                String hasta = fin.toString();
                switch (opcion) {
                    case "1":
                        mostrarServicios(reportes.serviciosPorNombre(desde, hasta));
                        preguntarExportar(entrada, () -> reportes.exportarServiciosMasSolicitados(desde, hasta));
                        break;
                    case "2":
                        mostrar(reportes.productividadPorTecnico(desde, hasta));
                        preguntarExportar(entrada, () -> reportes.exportarProductividad(desde, hasta));
                        break;
                    case "3":
                        mostrar(reportes.consumoRepuestos(desde, hasta));
                        preguntarExportar(entrada, () -> reportes.exportarConsumoRepuestos(desde, hasta));
                        break;
                    case "4":
                        mostrarServiciosCompletados(reportes.serviciosCompletados(desde, hasta));
                        preguntarExportar(entrada, () -> reportes.exportarServiciosCompletados(desde, hasta));
                        break;
                }
            } catch (NoSuchElementException e) {
                return;
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println(e.getMessage());
            } catch (SQLException e) {
                System.out.println("Error de MySQL. Código: " + e.getErrorCode());
            }
        }
    }

    /** Submenú de reportes detallados exportables a .txt */
    private void exportarDetallado(Scanner entrada) throws SQLException {
        System.out.println("Exportar reporte detallado (.txt)");
        System.out.println("1. Ventas / Facturación");
        System.out.println("2. Órdenes de servicio");
        System.out.println("3. Inventario / Repuestos");
        System.out.println("0. Volver");
        String opcion = leer(entrada, "Opción");
        if (opcion.equals("0")) {
            return;
        }
        Path archivo;
        switch (opcion) {
            case "1" -> {
                String[] periodo = pedirPeriodo(entrada);
                archivo = reportes.exportarVentas(periodo[0], periodo[1]);
            }
            case "2" -> {
                String[] periodo = pedirPeriodo(entrada);
                archivo = reportes.exportarOrdenes(periodo[0], periodo[1]);
            }
            case "3" -> archivo = reportes.exportarInventario();
            default -> {
                System.out.println("Opción inválida.");
                return;
            }
        }
        System.out.println("Reporte exportado exitosamente en: " + archivo);
    }

    private String[] pedirPeriodo(Scanner entrada) {
        java.time.LocalDate inicio = EntradaConsola.fecha(entrada, System.out, "Desde (AAAA-MM-DD): ", false);
        java.time.LocalDate fin = EntradaConsola.fecha(entrada, System.out, "Hasta (AAAA-MM-DD): ", false);
        while (fin.isBefore(inicio)) {
            System.out.println("La fecha final debe ser igual o posterior a la inicial.");
            fin = EntradaConsola.fecha(entrada, System.out, "Hasta (AAAA-MM-DD): ", false);
        }
        return new String[]{inicio.toString(), fin.toString()};
    }

    @FunctionalInterface
    private interface Exportacion {
        Path ejecutar() throws SQLException;
    }

    private void preguntarExportar(Scanner entrada, Exportacion exportacion) {
        try {
            String respuesta = leer(entrada, "¿Desea exportar este reporte a .txt? (s/n)").trim().toLowerCase();
            if (respuesta.equals("s") || respuesta.equals("si") || respuesta.equals("sí")) {
                Path archivo = exportacion.ejecutar();
                System.out.println("Reporte exportado exitosamente en: " + archivo);
            }
        } catch (NoSuchElementException e) {
            // fin de entrada
        } catch (SQLException e) {
            System.out.println("Error de MySQL al exportar. Código: " + e.getErrorCode());
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }
    }

    public void consultarFactura(Scanner entrada) {
        try {
            int id = EntradaConsola.entero(entrada, System.out, "ID de la orden (0 para volver): ", 0, Integer.MAX_VALUE);
            if (id == 0) {
                return;
            }
            DatosFactura factura = reportes.datosFactura(id);
            System.out.println(factura);
            System.out.println("Documento: " + factura.getDocumento());
            for (RepuestoUsado repuesto : factura.getRepuestos()) {
                System.out.printf("%s | %d × %s = %s%n", repuesto.getRepuesto(), repuesto.getCantidad(),
                        repuesto.getPrecioUnitario(), repuesto.getSubtotal());
            }
        } catch (NoSuchElementException e) {
            return;
        } catch (NumberFormatException e) {
            System.out.println("Ingresa un ID válido.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println("Error de MySQL. Código: " + e.getErrorCode());
        }
    }

    private void mostrarServicios(Map<String, Long> conteo) {
        long total = conteo.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            System.out.println("No hay resultados para ese período.");
            return;
        }
        conteo.forEach((servicio, cantidad) -> System.out.printf("%-32s %d%n", servicio, cantidad));
        System.out.println("Total de órdenes: " + total);
    }

    private void mostrar(Iterable<?> resultados) {
        if (!resultados.iterator().hasNext()) {
            System.out.println("No hay resultados para ese período.");
        }
        for (Object resultado : resultados) {
            System.out.println(resultado);
        }
    }

    private void mostrarServiciosCompletados(List<ServicioReporte> lista) {
        if (lista.isEmpty()) {
            System.out.println("No hay servicios completados en ese período.");
            return;
        }
        for (ServicioReporte s : lista) {
            String nombre = (s.getNombreServicio() == null || s.getNombreServicio().isBlank())
                    ? "(sin nombre)"
                    : s.getNombreServicio();
            String entrega = s.getFechaEntrega() != null
                    ? s.getFechaEntrega().toString()
                    : "Sin fecha de entrega";
            System.out.printf("#%d | %-28s | %-12s | Recepción: %s | Entrega: %s%n",
                    s.getId(),
                    nombre,
                    s.getEstado(),
                    s.getFechaRecepcion(),
                    entrega);
        }
        System.out.println("Total: " + lista.size());
    }

    private String leer(Scanner entrada, String campo) {
        return EntradaConsola.texto(entrada, System.out, campo + ": ", 255, false, false);
    }
}