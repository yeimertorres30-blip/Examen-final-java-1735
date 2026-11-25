## Concurrencia y reserva de repuestos (race condition)

### Problema
Cuando dos técnicos intentaban reservar al mismo tiempo el último repuesto disponible, el sistema podía permitir que ambos "reservaran" la misma unidad. Eso es una condición de carrera (*race condition*): la secuencia leer stock → validar → escribir no era atómica desde el punto de vista de los hilos de Java.

### Solución
1. **Sincronización en la capa de servicio:** el método `OrdenServicioServicio.agregarRepuesto` protege la sección crítica (lectura de stock, validación de disponibilidad y registro de la reserva) con un candado estático (`synchronized` sobre `CANDADO_RESERVA`). Solo un hilo a la vez puede ejecutar esa lógica.
2. **Bloqueo en base de datos:** se mantiene el `SELECT ... FOR UPDATE` sobre la fila del repuesto dentro de la misma transacción, de modo que también se evita la sobreventa entre procesos distintos.
3. **Simulación:** la clase `com.carmotors.simulacion.SimulacionReservaConcurrente` prepara un repuesto con stock = 1, lanza dos hilos concurrentes que intentan reservarlo para órdenes distintas y muestra en consola qué hilo tuvo éxito y cuál falló por falta de stock.

Ejecutar la simulación (con MySQL levantado y data.sql cargado):

```bash
mvn exec:java -Dexec.mainClass="com.carmotors.simulacion.SimulacionReservaConcurrente"
