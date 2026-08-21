package com.example.soltec.service;

import com.example.soltec.dto.ServicioDTO;
import com.example.soltec.repository.ServicioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServicioServiceImpl implements ServicioService {

    private final ServicioRepository servicioRepository;

    @Override
    public List<ServicioDTO> listarActivos() {
        return servicioRepository.findByActivoTrue().stream()
                .map(s -> ServicioDTO.builder()
                        .id(s.getId())
                        .nombre(s.getNombre())
                        .descripcion(s.getDescripcion())
                        .build())
                .toList();
    }
}
