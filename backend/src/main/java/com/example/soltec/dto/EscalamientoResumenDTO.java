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
public class EscalamientoResumenDTO {

    private Integer id;
    private String numeroBoleta;
    private String tipo;
    private String asunto;
    private String cliente;
    private String escaladaPor;
    private String motivoEscalamiento;
    private String estadoCodigo;
    private String estado;
    // true cuando el supervisor ya tomo la atencion directa (FA02)
    private Boolean enAtencionSupervisor;
    private String supervisorResponsable;
    private OffsetDateTime fechaLimite;
}
