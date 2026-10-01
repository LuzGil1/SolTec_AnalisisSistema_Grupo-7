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
public class CasoGeneralDTO {

    private Integer id;
    private String numeroBoleta;
    private String tipoCodigo;
    private String tipo;
    private String asunto;
    private String cliente;
    private Integer tecnicoId;
    private String tecnico;
    private String estadoCodigo;
    private String estado;
    private Boolean esFinal;
    private String prioridad;
    private OffsetDateTime fechaRegistro;
    private OffsetDateTime fechaLimite;
}
