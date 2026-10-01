package com.example.soltec.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ComentarioRequest {

    @NotBlank(message = "Escriba el avance antes de continuar.")
    private String comentario;
}
