package com.example.soltec.repository;

import java.math.BigDecimal;
import java.time.Instant;

// Proyeccion de vw_solicitudes_sin_asignar, mas el nombre del cliente.
public interface SolicitudSinAsignarProyeccion {

    Integer getCasoId();

    String getNumeroBoleta();

    String getTipo();

    String getAsunto();

    String getCliente();

    String getPrioridad();

    Instant getFechaRegistro();

    BigDecimal getHorasEnCola();

    Instant getFechaLimite();
}
