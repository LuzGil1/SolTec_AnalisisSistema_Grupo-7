package com.example.soltec.repository;

import java.time.Instant;

// Proyeccion de vw_casos_general: listado general del supervisor.
public interface CasoGeneralProyeccion {

    Integer getCasoId();

    String getNumeroBoleta();

    String getTipoCodigo();

    String getTipo();

    String getAsunto();

    String getCliente();

    Integer getTecnicoId();

    String getTecnico();

    String getEstadoCodigo();

    String getEstado();

    Boolean getEsFinal();

    String getPrioridad();

    Instant getFechaRegistro();

    Instant getFechaLimite();
}
