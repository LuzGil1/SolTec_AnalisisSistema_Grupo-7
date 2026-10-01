package com.example.soltec.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SituacionTecnicoDTO {

    private Integer id;
    private String nombre;
    private String correo;
    private String especialidades;
    private Integer casosAbiertos;
    private Integer capacidadMaxima;
    private Boolean disponible;
    private String motivoNoDisponible;
}
