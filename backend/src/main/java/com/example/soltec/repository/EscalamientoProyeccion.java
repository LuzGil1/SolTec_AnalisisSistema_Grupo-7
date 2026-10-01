package com.example.soltec.repository;

import java.time.Instant;

// Proyeccion de vw_escalamientos.
public interface EscalamientoProyeccion {

    Integer getCasoId();

    String getNumeroBoleta();

    String getTipo();

    String getAsunto();

    String getCliente();

    String getEscaladaPor();

    String getMotivoEscalamiento();

    Instant getFechaEscalamiento();

    String getEstadoCodigo();

    String getEstado();

    Integer getSupervisorResponsableId();

    String getSupervisorResponsable();

    Instant getFechaLimite();
}
