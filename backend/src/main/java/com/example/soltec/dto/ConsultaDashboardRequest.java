package com.example.soltec.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Una consulta al motor. clave solo se usa dentro de un lote, para saber a
// que grafica pertenece cada respuesta.
@Getter
@Setter
@NoArgsConstructor
public class ConsultaDashboardRequest {

    private String clave;
    private String metrica;
    private String metricaSecundaria;
    private String agrupacion;
    private String agrupacionSecundaria;
    private String agrupacionTerciaria;
    private String forma;
    private String variante;
    private String periodo;
    private String boleta;
    private FiltrosDashboardDTO filtros;
}
