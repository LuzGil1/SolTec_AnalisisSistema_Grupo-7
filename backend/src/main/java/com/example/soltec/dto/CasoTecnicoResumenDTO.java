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
public class CasoTecnicoResumenDTO {

    private Integer id;
    private String numeroBoleta;
    private String tipo;
    private String asunto;
    private String prioridad;
    private String estado;
    private String cliente;
    private OffsetDateTime fechaRegistro;
    private OffsetDateTime fechaLimiteResolucion;
}
