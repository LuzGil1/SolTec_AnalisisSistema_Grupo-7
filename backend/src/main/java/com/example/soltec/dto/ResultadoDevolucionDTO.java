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
public class ResultadoDevolucionDTO {

    // Tecnico al que el sistema reasigno la solicitud; null si quedo en la bolsa
    private String tecnicoAsignado;
}
