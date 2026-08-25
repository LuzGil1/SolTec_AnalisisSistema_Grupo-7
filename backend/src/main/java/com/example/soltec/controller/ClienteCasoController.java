package com.example.soltec.controller;

import com.example.soltec.dto.AdjuntoDTO;
import com.example.soltec.dto.CasoCreadoDTO;
import com.example.soltec.dto.CasoResumenDTO;
import com.example.soltec.dto.NuevaSolicitudRequest;
import com.example.soltec.service.CasoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cliente/casos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENTE')")
public class ClienteCasoController {

    private final CasoService casoService;

    @GetMapping
    public ResponseEntity<List<CasoResumenDTO>> misCasos() {
        return ResponseEntity.ok(casoService.listarDelClienteActual());
    }

    @PostMapping
    public ResponseEntity<CasoCreadoDTO> registrar(@Valid @RequestBody NuevaSolicitudRequest request,
                                                     HttpServletRequest httpRequest) {
        CasoCreadoDTO creado = casoService.registrar(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PostMapping(path = "/{id}/adjuntos")
    public ResponseEntity<AdjuntoDTO> adjuntar(@PathVariable Integer id,
                                                @RequestParam("archivo") MultipartFile archivo) throws IOException {
        AdjuntoDTO adjunto = casoService.adjuntarEvidencia(id, archivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(adjunto);
    }
}
