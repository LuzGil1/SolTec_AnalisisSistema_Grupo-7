package com.example.soltec.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// JsonNode y no Integer a proposito: con Integer, Jackson convierte 2.5 en 2
// sin avisar y rechaza "abc" antes de llegar al servicio con un error
// generico. Asi cualquier valor llega a la validacion de RN04 y recibe el
// mensaje del caso de uso.
@Getter
@Setter
@NoArgsConstructor
public class CambiarCapacidadRequest {

    private JsonNode capacidadMaxima;
}
