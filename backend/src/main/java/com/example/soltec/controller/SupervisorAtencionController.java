package com.example.soltec.controller;

import com.example.soltec.dto.AgregarInvolucradoRequest;
import com.example.soltec.dto.CasoSupervisorDetalleDTO;
import com.example.soltec.dto.ComentarioRequest;
import com.example.soltec.dto.DenunciaResumenDTO;
import com.example.soltec.dto.EscalamientoResumenDTO;
import com.example.soltec.dto.PersonalDTO;
import com.example.soltec.dto.RegistrarAvanceRequest;
import com.example.soltec.dto.ResponderSugerenciaRequest;
import com.example.soltec.dto.ResultadoDevolucionDTO;
import com.example.soltec.dto.SugerenciaResumenDTO;
import com.example.soltec.service.SupervisorAtencionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Segunda etapa del modulo del Supervisor: denuncias, escalamientos y
// sugerencias. Deliberadamente NO hay endpoint para retirar personal
// involucrado de una denuncia (RN04 del CU Atencion de Denuncias).
@RestController
@RequestMapping("/api/supervisor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISOR')")
public class SupervisorAtencionController {

    private final SupervisorAtencionService atencionService;

    // ----- Denuncias -----

    @GetMapping("/denuncias")
    public ResponseEntity<List<DenunciaResumenDTO>> denuncias() {
        return ResponseEntity.ok(atencionService.listarDenuncias());
    }

    @GetMapping("/denuncias/{id}")
    public ResponseEntity<CasoSupervisorDetalleDTO> denuncia(@PathVariable Integer id, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(atencionService.obtenerDenuncia(id, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/denuncias/{id}/avance")
    public ResponseEntity<Void> avanceDenuncia(@PathVariable Integer id,
                                               @Valid @RequestBody RegistrarAvanceRequest request,
                                               HttpServletRequest httpRequest) {
        atencionService.registrarAvanceDenuncia(id, request, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/denuncias/{id}/involucrados")
    public ResponseEntity<Void> agregarInvolucrado(@PathVariable Integer id,
                                                   @Valid @RequestBody AgregarInvolucradoRequest request,
                                                   HttpServletRequest httpRequest) {
        atencionService.agregarInvolucrado(id, request, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    // Opciones del selector de "Agregar involucrado"
    @GetMapping("/personal")
    public ResponseEntity<List<PersonalDTO>> personal() {
        return ResponseEntity.ok(atencionService.listarPersonal());
    }

    // ----- Escalamientos -----

    @GetMapping("/escalamientos")
    public ResponseEntity<List<EscalamientoResumenDTO>> escalamientos() {
        return ResponseEntity.ok(atencionService.listarEscalamientos());
    }

    @GetMapping("/escalamientos/{id}")
    public ResponseEntity<CasoSupervisorDetalleDTO> escalamiento(@PathVariable Integer id,
                                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(atencionService.obtenerEscalamiento(id, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/escalamientos/{id}/devolver")
    public ResponseEntity<ResultadoDevolucionDTO> devolver(@PathVariable Integer id,
                                                           @Valid @RequestBody ComentarioRequest request,
                                                           HttpServletRequest httpRequest) {
        return ResponseEntity.ok(atencionService.devolverEscalamiento(id, request.getComentario(),
                httpRequest.getRemoteAddr()));
    }

    @PostMapping("/escalamientos/{id}/atender")
    public ResponseEntity<Void> atender(@PathVariable Integer id,
                                        @Valid @RequestBody ComentarioRequest request,
                                        HttpServletRequest httpRequest) {
        atencionService.atenderEscalamiento(id, request.getComentario(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/escalamientos/{id}/avance")
    public ResponseEntity<Void> avanceEscalamiento(@PathVariable Integer id,
                                                   @Valid @RequestBody RegistrarAvanceRequest request,
                                                   HttpServletRequest httpRequest) {
        atencionService.registrarAvanceEscalamiento(id, request, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    // ----- Sugerencias -----

    @GetMapping("/sugerencias")
    public ResponseEntity<List<SugerenciaResumenDTO>> sugerencias() {
        return ResponseEntity.ok(atencionService.listarSugerencias());
    }

    @PostMapping("/sugerencias/{id}/responder")
    public ResponseEntity<Void> responder(@PathVariable Integer id,
                                          @Valid @RequestBody ResponderSugerenciaRequest request,
                                          HttpServletRequest httpRequest) {
        atencionService.responderSugerencia(id, request, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
