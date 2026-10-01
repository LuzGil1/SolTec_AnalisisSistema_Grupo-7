package com.example.soltec.repository;

import java.time.Instant;

// Proyeccion de vw_denuncias.
public interface DenunciaProyeccion {

    Integer getCasoId();

    String getNumeroBoleta();

    String getAsunto();

    String getCliente();

    String getInvolucrados();

    String getEstadoCodigo();

    String getEstado();

    Instant getFechaRegistro();

    Instant getFechaLimite();
}
