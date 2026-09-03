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
public class CargaTecnicoDTO {

    private Long casosAbiertos;
    private Short capacidadMaxima;
    private Boolean disponible;
}
