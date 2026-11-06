package com.example.soltec.controller;

import com.example.soltec.dto.CatalogoDashboardDTO;
import com.example.soltec.dto.ConsultaDashboardRequest;
import com.example.soltec.dto.LoteDashboardRequest;
import com.example.soltec.dto.OpcionesDashboardDTO;
import com.example.soltec.dto.ResultadoConsultaDTO;
import com.example.soltec.dto.ResultadoLoteDTO;
import com.example.soltec.dto.TrazabilidadDTO;
import com.example.soltec.service.DashboardService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Un solo motor de consultas: no hay un endpoint por grafica.
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
public class DashboardController {

    private static final MediaType TIPO_EXCEL =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DashboardService dashboardService;

    @GetMapping("/catalogo")
    public ResponseEntity<CatalogoDashboardDTO> catalogo() {
        return ResponseEntity.ok(dashboardService.obtenerCatalogo());
    }

    // Combos encadenados: servicio -> especialidad -> tecnico
    @GetMapping("/opciones")
    public ResponseEntity<OpcionesDashboardDTO> opciones(
            @RequestParam(required = false) Integer servicioId,
            @RequestParam(required = false) Integer especialidadId) {
        return ResponseEntity.ok(dashboardService.obtenerOpciones(servicioId, especialidadId));
    }

    @PostMapping("/consulta")
    public ResponseEntity<ResultadoConsultaDTO> consulta(@RequestBody ConsultaDashboardRequest request) {
        return ResponseEntity.ok(dashboardService.consultar(request));
    }

    // Todas las graficas de la pantalla con unos mismos filtros
    @PostMapping("/consulta/lote")
    public ResponseEntity<ResultadoLoteDTO> lote(@RequestBody LoteDashboardRequest request) {
        return ResponseEntity.ok(dashboardService.consultarLote(request));
    }

    @GetMapping("/trazabilidad/{numeroBoleta}")
    public ResponseEntity<TrazabilidadDTO> trazabilidad(@PathVariable String numeroBoleta) {
        return ResponseEntity.ok(dashboardService.obtenerTrazabilidad(numeroBoleta));
    }

    @GetMapping("/exportar/excel")
    public ResponseEntity<byte[]> exportarExcel(@ModelAttribute ConsultaDashboardRequest request) throws IOException {
        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename("dashboard_soltec.xlsx", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(TIPO_EXCEL)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(dashboardService.exportarExcel(request));
    }
}
