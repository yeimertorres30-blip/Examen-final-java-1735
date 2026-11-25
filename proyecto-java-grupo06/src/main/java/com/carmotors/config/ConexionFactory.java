package com.carmotors.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ConexionFactory {
    private static final ConexionFactory INSTANCIA = new ConexionFactory();

    private ConexionFactory() {}

    public static ConexionFactory getInstancia() {
        return INSTANCIA;
    }

    public Connection abrir() throws SQLException {
        return DriverManager.getConnection(
                entorno("CARMOTORS_DB_URL", "jdbc:mysql://localhost:3306/carmotors"),
                entorno("CARMOTORS_DB_USER", "root"),
                entorno("CARMOTORS_DB_PASSWORD", "Cucuta202601"));
    }

    private static String entorno(String clave, String valorPredeterminado) {
        return System.getenv().getOrDefault(clave, valorPredeterminado);
    }
}
