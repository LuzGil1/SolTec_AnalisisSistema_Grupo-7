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
public class AvanceDTO {

    private OffsetDateTime fecha;
    private String autor;
    private String comentario;
    private String estadoAnterior;
    private String estadoNuevo;
}
