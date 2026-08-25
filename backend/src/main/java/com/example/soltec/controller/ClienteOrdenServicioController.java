package com.example.soltec.controller;

import com.example.soltec.dto.OrdenServicioDTO;
import com.example.soltec.service.OrdenServicioService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cliente/ordenes-servicio")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENTE')")
public class ClienteOrdenServicioController {

    private final OrdenServicioService ordenServicioService;

    @GetMapping
    public ResponseEntity<List<OrdenServicioDTO>> misOrdenes() {
        return ResponseEntity.ok(ordenServicioService.listarDelClienteActual());
    }
}
