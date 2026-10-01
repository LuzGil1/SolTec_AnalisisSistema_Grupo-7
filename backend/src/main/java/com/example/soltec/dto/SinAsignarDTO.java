package com.example.soltec.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// CU Solicitudes sin asignar: el listado va junto con la situacion de los
// tecnicos (RN03), que es lo que explica por que no se pudieron distribuir.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SinAsignarDTO {

    private BigDecimal horasUmbral;
    private List<SolicitudSinAsignarDTO> solicitudes;
    private List<SituacionTecnicoDTO> tecnicos;
}
