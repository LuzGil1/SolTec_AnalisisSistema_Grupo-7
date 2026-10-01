package com.example.soltec.dto;

import java.math.BigDecimal;
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
public class SolicitudSinAsignarDTO {

    private Integer id;
    private String numeroBoleta;
    private String tipo;
    private String asunto;
    private String cliente;
    private String prioridad;
    private OffsetDateTime fechaRegistro;
    private BigDecimal horasEnCola;
    private OffsetDateTime fechaLimite;
}
