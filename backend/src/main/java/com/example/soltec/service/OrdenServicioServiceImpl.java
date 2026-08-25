package com.example.soltec.service;

import com.example.soltec.config.UsuarioActualProvider;
import com.example.soltec.dto.OrdenServicioDTO;
import com.example.soltec.repository.OrdenServicioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrdenServicioServiceImpl implements OrdenServicioService {

    private final OrdenServicioRepository ordenServicioRepository;
    private final UsuarioActualProvider usuarioActualProvider;

    @Override
    public List<OrdenServicioDTO> listarDelClienteActual() {
        Integer clienteId = usuarioActualProvider.obtener().getId();

        // Se consulta vw_ordenes_cliente, que ya filtra columnas seguras
        // (nunca el tecnico) y en Java se filtra ademas por el cliente del
        // token: un cliente jamas ve ordenes de un tercero.
        return ordenServicioRepository.buscarPorCliente(clienteId).stream()
                .map(o -> OrdenServicioDTO.builder()
                        .id(o.getOrdenId())
                        .numeroOrden(o.getNumeroOrden())
                        .servicio(o.getServicio())
                        .fechaServicio(o.getFechaServicio())
                        .descripcion(o.getDescripcion())
                        .build())
                .toList();
    }
}
