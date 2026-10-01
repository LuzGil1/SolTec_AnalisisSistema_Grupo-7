package com.example.soltec.repository;

// Contadores de las tres tarjetas del dashboard del supervisor.
public interface ResumenSupervisorProyeccion {

    Long getDenunciasPendientes();

    Long getEscalamientosPendientes();

    Long getSolicitudesSinAsignar();
}
