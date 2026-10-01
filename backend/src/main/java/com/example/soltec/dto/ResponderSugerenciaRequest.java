package com.example.soltec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResponderSugerenciaRequest {

    @NotBlank(message = "Escriba la respuesta antes de continuar.")
    private String respuesta;

    // Solo queda registrado en el avance: el estado final es CERRADO en
    // ambos casos (RN04 del CU Revision de Sugerencias)
    @NotNull(message = "Indique si la sugerencia fue acogida o no")
    private Boolean acogida;
}
