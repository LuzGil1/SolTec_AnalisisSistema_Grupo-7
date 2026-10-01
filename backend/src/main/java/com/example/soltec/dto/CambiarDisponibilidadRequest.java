package com.example.soltec.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// La obligatoriedad del motivo cuando disponible = false la valida
// fn_cambiar_disponibilidad (RN02), no se repite aqui.
@Getter
@Setter
@NoArgsConstructor
public class CambiarDisponibilidadRequest {

    @NotNull(message = "Debe indicar si el técnico queda disponible o no disponible")
    private Boolean disponible;

    @Size(max = 200, message = "El motivo no puede exceder 200 caracteres")
    private String motivo;
}
