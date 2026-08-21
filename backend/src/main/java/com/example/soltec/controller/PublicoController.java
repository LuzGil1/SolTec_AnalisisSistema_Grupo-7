package com.example.soltec.controller;

import com.example.soltec.dto.ServicioDTO;
import com.example.soltec.service.ServicioService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/publico")
@RequiredArgsConstructor
public class PublicoController {

    private final ServicioService servicioService;

    @GetMapping("/servicios")
    public ResponseEntity<List<ServicioDTO>> servicios() {
        return ResponseEntity.ok(servicioService.listarActivos());
    }
}
