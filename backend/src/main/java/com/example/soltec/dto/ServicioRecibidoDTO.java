package com.example.soltec.dto;

import java.time.LocalDate;
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
public class ServicioRecibidoDTO {

    private String numeroOrden;
    private String servicio;
    private LocalDate fechaServicio;
}
