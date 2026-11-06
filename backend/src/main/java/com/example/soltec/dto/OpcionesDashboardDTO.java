package com.example.soltec.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Contenido de los combos del panel de filtros. especialidades y tecnicos
// dependen del servicio y de la especialidad elegidos.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpcionesDashboardDTO {

    private List<OpcionFiltroDTO> tipos;
    private List<OpcionFiltroDTO> prioridades;
    private List<OpcionFiltroDTO> situaciones;
    private List<OpcionFiltroDTO> servicios;
    private List<OpcionFiltroDTO> especialidades;
    private List<OpcionFiltroDTO> tecnicos;
    private List<OpcionFiltroDTO> clientes;
    private List<OpcionFiltroDTO> supervisores;
    private List<String> roles;
    private String boletaEjemplo;
}
