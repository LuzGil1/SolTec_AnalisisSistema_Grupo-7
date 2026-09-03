package com.example.soltec.controller;

import com.example.soltec.dto.AdjuntoArchivoDTO;
import com.example.soltec.dto.CargaTecnicoDTO;
import com.example.soltec.dto.CasoTecnicoDetalleDTO;
import com.example.soltec.dto.CasoTecnicoResumenDTO;
import com.example.soltec.dto.RegistrarAvanceRequest;
import com.example.soltec.service.TecnicoCasoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tecnico")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TECNICO')")
public class TecnicoCasoController {

    private final TecnicoCasoService tecnicoCasoService;

    @GetMapping("/casos")
    public ResponseEntity<List<CasoTecnicoResumenDTO>> misCasos() {
        return ResponseEntity.ok(tecnicoCasoService.listarAsignadosActuales());
    }

    @GetMapping("/carga")
    public ResponseEntity<CargaTecnicoDTO> carga() {
        return ResponseEntity.ok(tecnicoCasoService.obtenerCarga());
    }

    @GetMapping("/casos/{id}")
    public ResponseEntity<CasoTecnicoDetalleDTO> detalle(@PathVariable Integer id, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(tecnicoCasoService.obtenerDetalle(id, httpRequest.getRemoteAddr()));
    }

    @GetMapping("/casos/{casoId}/adjuntos/{adjuntoId}")
    public ResponseEntity<Resource> descargarAdjunto(@PathVariable Integer casoId,
                                                       @PathVariable Integer adjuntoId,
                                                       HttpServletRequest httpRequest) throws IOException {
        AdjuntoArchivoDTO archivo = tecnicoCasoService.descargarAdjunto(casoId, adjuntoId, httpRequest.getRemoteAddr());
        ContentDisposition disposicion = ContentDisposition.inline()
                .filename(archivo.getNombreArchivo(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.getTipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(archivo.getRecurso());
    }

    @PostMapping("/casos/{id}/avance")
    public ResponseEntity<Void> registrarAvance(@PathVariable Integer id,
                                                 @Valid @RequestBody RegistrarAvanceRequest request,
                                                 HttpServletRequest httpRequest) {
        tecnicoCasoService.registrarAvance(id, request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
