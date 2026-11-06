package com.example.soltec.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Lo que el frontend necesita para deshabilitar opciones sin adivinar.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogoDashboardDTO {

    private List<Map<String, Object>> metricas;
    private List<Map<String, Object>> agrupaciones;
    private List<String> formas;
    private List<Map<String, Object>> tiposGrafica;
    private List<Map<String, Object>> periodos;
    private List<String> reglas;
}
