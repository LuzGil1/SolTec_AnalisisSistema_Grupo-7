package com.example.soltec.repository;

// Proyeccion de vw_carga_tecnicos: situacion de cada tecnico activo.
public interface CargaTecnicoProyeccion {

    Integer getTecnicoId();

    String getNombre();

    String getCorreo();

    String getEspecialidades();

    Integer getCasosAbiertos();

    Integer getCapacidadMaxima();

    Boolean getDisponible();

    String getMotivoNoDisponible();
}
