package com.example.soltec.controller;

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
import com.example.soltec.service.SupervisorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Modulo del Supervisor de Soporte. Deliberadamente NO hay endpoint para
// asignar solicitudes a mano (RN04 del CU Solicitudes sin asignar): la
// distribucion es exclusiva del sistema.
@RestController
@RequestMapping("/api/supervisor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISOR')")
public class SupervisorController {

    private final SupervisorService supervisorService;

    @GetMapping("/resumen")
    public ResponseEntity<ResumenSupervisorDTO> resumen() {
        return ResponseEntity.ok(supervisorService.obtenerResumen());
    }

    // Opciones de los filtros del listado general (tipos y estados)
    @GetMapping("/filtros")
    public ResponseEntity<FiltrosSupervisorDTO> filtros() {
        return ResponseEntity.ok(supervisorService.obtenerFiltros());
    }

    @GetMapping("/casos")
    public ResponseEntity<List<CasoGeneralDTO>> casos(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer tecnicoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String boleta) {
        return ResponseEntity.ok(supervisorService.listarCasos(tipo, estado, tecnicoId, fechaDesde, fechaHasta, boleta));
    }

    @GetMapping("/casos/{id}")
    public ResponseEntity<CasoSupervisorDetalleDTO> detalle(@PathVariable Integer id, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(supervisorService.obtenerDetalle(id, httpRequest.getRemoteAddr()));
    }

    @GetMapping("/casos/{casoId}/adjuntos/{adjuntoId}")
    public ResponseEntity<Resource> descargarAdjunto(@PathVariable Integer casoId,
                                                       @PathVariable Integer adjuntoId) throws IOException {
        AdjuntoArchivoDTO archivo = supervisorService.descargarAdjunto(casoId, adjuntoId);
        ContentDisposition disposicion = ContentDisposition.inline()
                .filename(archivo.getNombreArchivo(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.getTipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(archivo.getRecurso());
    }

    @GetMapping("/sin-asignar")
    public ResponseEntity<SinAsignarDTO> sinAsignar(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(supervisorService.listarSinAsignar(httpRequest.getRemoteAddr()));
    }

    @GetMapping("/tecnicos")
    public ResponseEntity<List<SituacionTecnicoDTO>> tecnicos() {
        return ResponseEntity.ok(supervisorService.listarTecnicos());
    }

    @PutMapping("/tecnicos/{id}/disponibilidad")
    public ResponseEntity<ResultadoCambioTecnicoDTO> cambiarDisponibilidad(
            @PathVariable Integer id,
            @Valid @RequestBody CambiarDisponibilidadRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(supervisorService.cambiarDisponibilidad(id, request, httpRequest.getRemoteAddr()));
    }

    @PutMapping("/tecnicos/{id}/capacidad")
    public ResponseEntity<ResultadoCambioTecnicoDTO> cambiarCapacidad(
            @PathVariable Integer id,
            @RequestBody CambiarCapacidadRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(supervisorService.cambiarCapacidad(id, request, httpRequest.getRemoteAddr()));
    }
}
