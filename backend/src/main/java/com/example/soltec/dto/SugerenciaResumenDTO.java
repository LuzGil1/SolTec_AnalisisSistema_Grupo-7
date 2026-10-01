package com.example.soltec.dto;

import java.time.OffsetDateTime;
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
public class SugerenciaResumenDTO {

    private Integer id;
    private String numeroBoleta;
    private String asunto;
    private String cliente;
    private String estadoCodigo;
    private String estado;
    private Boolean respondida;
    private OffsetDateTime fechaRegistro;
}
