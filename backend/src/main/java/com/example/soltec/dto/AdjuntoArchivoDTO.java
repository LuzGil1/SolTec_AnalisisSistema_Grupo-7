package com.example.soltec.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.core.io.Resource;

// Transporta el recurso de un adjunto ya validado (propiedad del cliente
// verificada) desde el servicio hacia el controller que arma la respuesta HTTP.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjuntoArchivoDTO {

    private String nombreArchivo;
    private String tipoMime;
    private Resource recurso;
}
