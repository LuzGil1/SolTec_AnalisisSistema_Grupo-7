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
public class EventoTrazabilidadDTO {

    private OffsetDateTime fecha;
    private String origen;
    private String usuario;
    private String operacion;
    private String detalle;
    private String situacion;
}
