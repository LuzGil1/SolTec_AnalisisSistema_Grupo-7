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
public class ResumenSupervisorDTO {

    private Long denunciasPendientes;
    private Long escalamientosPendientes;
    private Long solicitudesSinAsignar;
}
