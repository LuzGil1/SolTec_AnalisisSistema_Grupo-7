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
public class ResultadoConsultaDTO {

    private String clave;
    private String forma;
    private String titulo;
    private String periodo;
    private Object datos;
    private Double total;
    private String error;
}
