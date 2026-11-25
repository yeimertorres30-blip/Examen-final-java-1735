# Examen-final-java-1735
Acceso: com.carmotors.simulacion.MainReservaConcurrente (clase ejecutable independiente del menú del taller)
Clases del examen:
- src/main/java/com/carmotors/simulacion/MainReservaConcurrente.java
- src/main/java/com/carmotors/controlador/ControladorSimulacionReserva.java
- src/main/java/com/carmotors/vista/VistaSimulacionReserva.java
- src/main/java/com/carmotors/simulacion/TecnicoReservador.java
- src/main/java/com/carmotors/simulacion/PreparadorEscenario.java
- src/main/java/com/carmotors/servicio/OrdenServicioServicio.java (método agregarRepuesto thread-safe con synchronized)
- src/main/java/com/carmotors/dao/RepuestoOrdenDao.java (reutilizado para disponibilidad y reserva)

#### Problema
Cuando dos técnicos intentan reservar al mismo tiempo la última unidad de un repuesto, puede producirse una condición de carrera (race condition): ambos leen stock disponible y ambos intentan reservar, aunque solo exista una unidad.

#### Solución
- La operación de reserva sigue el MVC del proyecto: la simulación y el menú del taller usan el mismo método `OrdenServicioServicio.agregarRepuesto`, que a su vez utiliza `RepuestoOrdenDao`.
- La sección crítica de lectura-validación-escritura del stock se protege con `synchronized`, de modo que solo un hilo puede reservar a la vez.
- La simulación crea dos hilos (`TecnicoReservador` implementa `Runnable`); uno obtiene la reserva y el otro es rechazado. La consola muestra quién reservó, a quién se le rechazó y la disponibilidad final.
