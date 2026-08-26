package com.example.soltec.dto;

import java.time.OffsetDateTime;
import java.util.List;
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
public class CasoDetalleDTO {

    private String numeroBoleta;
    private String tipo;
    private String estado;
    private OffsetDateTime fechaRegistro;
    private String asunto;
    private String descripcion;
    private ServicioRecibidoDTO servicioRecibido;
    private CasoRelacionadoDTO casoRelacionado;
    private OffsetDateTime fechaLimiteResolucion;
    private List<AdjuntoDTO> adjuntos;
}
