package com.example.soltec.repository;

import java.time.Instant;

// Proyeccion de vw_sugerencias.
public interface SugerenciaProyeccion {

    Integer getCasoId();

    String getNumeroBoleta();

    String getAsunto();

    String getCliente();

    String getEstadoCodigo();

    String getEstado();

    Boolean getEsFinal();

    Instant getFechaRegistro();
}
