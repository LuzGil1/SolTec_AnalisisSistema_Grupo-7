package com.example.soltec.repository;

import java.time.LocalDate;

// Proyeccion de vw_ordenes_cliente para el desplegable "servicio recibido".
// Deliberadamente no incluye el tecnico: el cliente no debe verlo.
public interface OrdenClienteProyeccion {

    Integer getOrdenId();

    String getNumeroOrden();

    String getServicio();

    LocalDate getFechaServicio();

    String getDescripcion();
}
