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
public class CasoResumenDTO {

    private Integer id;
    private String numeroBoleta;
    private String tipoSolicitud;
    private String asunto;
    private String estado;
    private OffsetDateTime fechaRegistro;
}
