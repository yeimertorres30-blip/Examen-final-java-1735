package com.carmotors;

import com.carmotors.config.ConexionFactory;
import com.carmotors.controlador.FacturaControlador;
import com.carmotors.controlador.InventarioControlador;
import com.carmotors.controlador.ReporteControlador;
import com.carmotors.dao.JdbcProveedorDao;
import com.carmotors.dao.JdbcRepuestoDao;
import com.carmotors.dao.LoteDao;
import com.carmotors.dao.EvaluacionProveedorDao;
import com.carmotors.dao.ReservaRepuestoDao;
import com.carmotors.dao.ReporteDao;
import com.carmotors.dao.FacturaDao;
import com.carmotors.servicio.FacturaServicio;
import com.carmotors.servicio.ReporteServicio;
import com.carmotors.servicio.InventarioProveedorServicio;
import com.carmotors.vista.MenuFacturacion;
import com.carmotors.vista.MenuReportes;
import com.carmotors.vista.MenuInventario;
import com.carmotors.controlador.ClienteControlador;
import com.carmotors.controlador.OrdenServicioControlador;
import com.carmotors.servicio.OrdenServicioServicio;
import com.carmotors.dao.ClienteDao;
import com.carmotors.dao.HistorialClienteDao;
import com.carmotors.dao.VehiculoDao;
import com.carmotors.servicio.HistorialClienteServicio;
import com.carmotors.servicio.RegistroClienteServicio;
import com.carmotors.servicio.RegistroVehiculoServicio;
import com.carmotors.vista.MenuClientes;
import com.carmotors.vista.MenuPrincipal;
import com.carmotors.notificacion.GestorNotificaciones;
import com.carmotors.notificacion.NotificacionConsola;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        GestorNotificaciones gestor = new GestorNotificaciones();
        gestor.agregarObservador(new NotificacionConsola());

        RegistroClienteServicio registro = new RegistroClienteServicio(new ClienteDao());
        HistorialClienteServicio consultas = new HistorialClienteServicio(new HistorialClienteDao(), gestor);
        ClienteControlador clientes = new ClienteControlador(registro, new RegistroVehiculoServicio(new VehiculoDao()), consultas);
        OrdenServicioServicio ordenes = new OrdenServicioServicio(ConexionFactory.getInstancia()::abrir, gestor);
        InventarioProveedorServicio inventario = new InventarioProveedorServicio(new JdbcProveedorDao(),
                new JdbcRepuestoDao(), new LoteDao(), new EvaluacionProveedorDao(), new ReservaRepuestoDao());

        ReporteDao fuenteReportes = new ReporteDao();
        ReporteControlador reportes = new ReporteControlador(
                new ReporteServicio(fuenteReportes),
                new FacturaDao(),
                fuenteReportes,
                new JdbcRepuestoDao(),
                new JdbcProveedorDao(),
                Path.of("reportes"));

        FacturaServicio facturacion = new FacturaServicio(ConexionFactory.getInstancia()::abrir, new FacturaDao(),
                ordenes, Path.of("facturas"), gestor);
        new MenuPrincipal(new MenuClientes(clientes), new OrdenServicioControlador(ordenes),
                new MenuInventario(new InventarioControlador(inventario)),
                new MenuFacturacion(new FacturaControlador(facturacion)), new MenuReportes(reportes)).ejecutar();
    }
}