package com.example.soltec.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarAvanceRequest {

    @NotBlank(message = "Escriba el avance antes de continuar.")
    private String comentario;

    // Codigo de estado_caso (p. ej. "EN_PROCESO"). Null: registrar avance sin mover el caso.
    private String nuevoEstado;
}
