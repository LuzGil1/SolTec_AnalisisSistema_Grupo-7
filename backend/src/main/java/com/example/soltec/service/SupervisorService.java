package com.example.soltec.service;

import com.example.soltec.dto.AdjuntoArchivoDTO;
import com.example.soltec.dto.CambiarCapacidadRequest;
import com.example.soltec.dto.CambiarDisponibilidadRequest;
import com.example.soltec.dto.CasoGeneralDTO;
import com.example.soltec.dto.CasoSupervisorDetalleDTO;
import com.example.soltec.dto.FiltrosSupervisorDTO;
import com.example.soltec.dto.ResultadoCambioTecnicoDTO;
import com.example.soltec.dto.ResumenSupervisorDTO;
import com.example.soltec.dto.SinAsignarDTO;
import com.example.soltec.dto.SituacionTecnicoDTO;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface SupervisorService {

    ResumenSupervisorDTO obtenerResumen();

    FiltrosSupervisorDTO obtenerFiltros();

    List<CasoGeneralDTO> listarCasos(String tipo, String estado, Integer tecnicoId,
                                     LocalDate fechaDesde, LocalDate fechaHasta, String boleta);

    CasoSupervisorDetalleDTO obtenerDetalle(Integer casoId, String direccionIp);

    AdjuntoArchivoDTO descargarAdjunto(Integer casoId, Integer adjuntoId) throws IOException;

    SinAsignarDTO listarSinAsignar(String direccionIp);

    List<SituacionTecnicoDTO> listarTecnicos();

    ResultadoCambioTecnicoDTO cambiarDisponibilidad(Integer tecnicoId, CambiarDisponibilidadRequest request,
                                                    String direccionIp);

    ResultadoCambioTecnicoDTO cambiarCapacidad(Integer tecnicoId, CambiarCapacidadRequest request,
                                               String direccionIp);
}
