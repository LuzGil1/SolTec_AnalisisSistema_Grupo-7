package com.example.soltec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NuevaSolicitudRequest {

    @NotNull(message = "Debe seleccionar el tipo de solicitud")
    private Integer tipoSolicitudId;

    private Integer ordenServicioId;

    @NotBlank(message = "El asunto es obligatorio")
    @Size(max = 150, message = "El asunto no puede superar 150 caracteres")
    private String asunto;

    @NotBlank(message = "La descripcion es obligatoria")
    private String descripcion;
}
