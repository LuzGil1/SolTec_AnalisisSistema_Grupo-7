package com.example.soltec.dto;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Nunca incluye los hash de la bitacora: la integridad va resumida en un
// bloque aparte.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrazabilidadDTO {

    private String numeroBoleta;
    private String asunto;
    private String tipo;
    private String prioridad;
    private String situacion;
    private String cliente;
    private String tecnico;
    private OffsetDateTime fechaRegistro;
    private OffsetDateTime fechaLimiteResolucion;
    private Boolean cumplioSla;
    private List<EventoTrazabilidadDTO> eventos;
    private IntegridadBitacoraDTO integridad;
}
