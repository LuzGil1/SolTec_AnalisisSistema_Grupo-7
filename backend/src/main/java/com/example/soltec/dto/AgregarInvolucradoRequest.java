package com.example.soltec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgregarInvolucradoRequest {

    @NotNull(message = "Seleccione al personal involucrado")
    private Integer usuarioId;

    @NotBlank(message = "Indique el motivo por el cual se registra como involucrado")
    @Size(max = 200, message = "El motivo no puede exceder 200 caracteres")
    private String motivo;
}
