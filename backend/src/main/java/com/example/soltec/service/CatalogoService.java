package com.example.soltec.service;

import com.example.soltec.dto.ParametrosDTO;
import com.example.soltec.dto.TipoSolicitudDTO;
import java.util.List;

public interface CatalogoService {

    List<TipoSolicitudDTO> listarTiposSolicitud();

    ParametrosDTO obtenerParametros();
}
