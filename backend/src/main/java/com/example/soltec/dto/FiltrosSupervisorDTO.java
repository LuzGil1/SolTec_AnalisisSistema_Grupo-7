package com.example.soltec.dto;

import java.util.List;
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
public class FiltrosSupervisorDTO {

    private List<OpcionFiltroDTO> tipos;
    private List<OpcionFiltroDTO> estados;
}
