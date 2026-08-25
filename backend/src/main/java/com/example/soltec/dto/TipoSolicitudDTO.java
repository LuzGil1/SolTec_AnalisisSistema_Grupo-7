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
public class TipoSolicitudDTO {

    private Integer id;
    private String codigo;
    private String nombre;
    private boolean requiereCasoPrevio;
    private boolean requiereServicio;
    private boolean permiteEvidencia;
}
