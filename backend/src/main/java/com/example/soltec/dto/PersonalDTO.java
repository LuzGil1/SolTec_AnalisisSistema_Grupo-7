package com.example.soltec.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Personal de SolTec (cualquier usuario activo que no sea cliente) para el
// selector de "Agregar involucrado".
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalDTO {

    private Integer id;
    private String nombre;
    private String rol;
}
