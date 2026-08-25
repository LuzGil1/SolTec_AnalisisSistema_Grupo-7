package com.example.soltec.service;

import com.example.soltec.dto.TipoSolicitudDTO;
import com.example.soltec.repository.TipoSolicitudRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogoServiceImpl implements CatalogoService {

    // Unico tipo que, segun el CU2, no pide servicio recibido ni evidencia
    private static final String CODIGO_SUGERENCIA = "SUGERENCIA";

    private final TipoSolicitudRepository tipoSolicitudRepository;

    @Override
    public List<TipoSolicitudDTO> listarTiposSolicitud() {
        return tipoSolicitudRepository.findByActivoTrue().stream()
                .map(tipo -> {
                    boolean esSugerencia = CODIGO_SUGERENCIA.equals(tipo.getCodigo());
                    return TipoSolicitudDTO.builder()
                            .id(tipo.getId())
                            .codigo(tipo.getCodigo())
                            .nombre(tipo.getNombre())
                            .requiereCasoPrevio(Boolean.TRUE.equals(tipo.getRequiereCasoPrevio()))
                            .requiereServicio(!esSugerencia)
                            .permiteEvidencia(!esSugerencia)
                            .build();
                })
                .toList();
    }
}
