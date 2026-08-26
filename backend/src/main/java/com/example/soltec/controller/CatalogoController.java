package com.example.soltec.controller;

import com.example.soltec.dto.ParametrosDTO;
import com.example.soltec.dto.TipoSolicitudDTO;
import com.example.soltec.service.CatalogoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENTE')")
public class CatalogoController {

    private final CatalogoService catalogoService;

    @GetMapping("/tipos-solicitud")
    public ResponseEntity<List<TipoSolicitudDTO>> tiposSolicitud() {
        return ResponseEntity.ok(catalogoService.listarTiposSolicitud());
    }

    @GetMapping("/parametros")
    public ResponseEntity<ParametrosDTO> parametros() {
        return ResponseEntity.ok(catalogoService.obtenerParametros());
    }
}
