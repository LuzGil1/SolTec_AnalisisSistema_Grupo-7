package com.example.soltec.service;

import com.example.soltec.dto.CatalogoDashboardDTO;
import com.example.soltec.dto.ConsultaDashboardRequest;
import com.example.soltec.dto.LoteDashboardRequest;
import com.example.soltec.dto.OpcionesDashboardDTO;
import com.example.soltec.dto.ResultadoConsultaDTO;
import com.example.soltec.dto.ResultadoLoteDTO;
import com.example.soltec.dto.TrazabilidadDTO;
import java.io.IOException;

public interface DashboardService {

    CatalogoDashboardDTO obtenerCatalogo();

    OpcionesDashboardDTO obtenerOpciones(Integer servicioId, Integer especialidadId);

    ResultadoConsultaDTO consultar(ConsultaDashboardRequest request);

    ResultadoLoteDTO consultarLote(LoteDashboardRequest request);

    TrazabilidadDTO obtenerTrazabilidad(String numeroBoleta);

    byte[] exportarExcel(ConsultaDashboardRequest request) throws IOException;
}
