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
public class CasoTecnicoDetalleDTO {

    private String numeroBoleta;
    private String tipo;
    private String estado;
    private String prioridad;
    private String asunto;
    private String descripcion;
    private OffsetDateTime fechaRegistro;
    private OffsetDateTime fechaLimiteResolucion;
    private ClienteContactoDTO cliente;
    private ServicioRecibidoDTO servicioRecibido;
    private List<AdjuntoDTO> adjuntos;
    private List<AvanceDTO> avances;
    private List<TransicionDTO> transicionesPermitidas;
}
