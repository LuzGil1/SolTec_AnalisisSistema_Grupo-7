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
public class AdjuntoDTO {

    private Integer id;
    private String nombreArchivo;
    private String tipoMime;
    private Long tamanoBytes;
    private OffsetDateTime fechaCarga;
}
