package com.example.soltec.dto;

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
public class ClienteContactoDTO {

    private String nombre;
    private String correo;
    private String telefono;
    private String direccion;
}
