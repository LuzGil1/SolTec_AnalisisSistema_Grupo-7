package com.example.soltec.service;

import com.example.soltec.dto.AdjuntoArchivoDTO;
import com.example.soltec.dto.CargaTecnicoDTO;
import com.example.soltec.dto.CasoTecnicoDetalleDTO;
import com.example.soltec.dto.CasoTecnicoResumenDTO;
import com.example.soltec.dto.RegistrarAvanceRequest;
import java.io.IOException;
import java.util.List;

public interface TecnicoCasoService {

    List<CasoTecnicoResumenDTO> listarAsignadosActuales();

    CargaTecnicoDTO obtenerCarga();

    CasoTecnicoDetalleDTO obtenerDetalle(Integer casoId, String direccionIp);

    AdjuntoArchivoDTO descargarAdjunto(Integer casoId, Integer adjuntoId, String direccionIp) throws IOException;

    void registrarAvance(Integer casoId, RegistrarAvanceRequest request, String direccionIp);
}
