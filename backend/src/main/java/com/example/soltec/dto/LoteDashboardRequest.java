package com.example.soltec.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Varias graficas con un mismo periodo y unos mismos filtros: se resuelven
// una sola vez y todas las consultas leen esa misma foto de los datos.
@Getter
@Setter
@NoArgsConstructor
public class LoteDashboardRequest {

    private String periodo;
    private FiltrosDashboardDTO filtros;
    private List<ConsultaDashboardRequest> consultas;
}
