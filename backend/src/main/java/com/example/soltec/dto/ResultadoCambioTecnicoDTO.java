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
public class ResultadoCambioTecnicoDTO {

    private SituacionTecnicoDTO tecnico;
    private Integer casosLiberados;
    private Integer casosAsignados;
}
