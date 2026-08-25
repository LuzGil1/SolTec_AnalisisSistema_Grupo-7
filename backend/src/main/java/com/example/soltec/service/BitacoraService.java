package com.example.soltec.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// La bitacora es de solo insercion: nunca se actualiza ni se borra desde la
// aplicacion. Todo pasa por fn_registrar_bitacora, que ademas encadena el hash.
@Service
@RequiredArgsConstructor
public class BitacoraService {

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper;

    public void registrar(Integer usuarioId, String ip, String modulo, String accion,
                           String entidad, String entidadId, String descripcion, Object datos) {
        String json = datos == null ? null : serializar(datos);

        entityManager.createNativeQuery(
                        "SELECT soltec.fn_registrar_bitacora(?1, ?2, ?3, ?4, ?5, ?6, ?7, CAST(?8 AS jsonb))")
                .setParameter(1, usuarioId)
                .setParameter(2, ip)
                .setParameter(3, modulo)
                .setParameter(4, accion)
                .setParameter(5, entidad)
                .setParameter(6, entidadId)
                .setParameter(7, descripcion)
                .setParameter(8, json)
                .getSingleResult();
    }

    private String serializar(Object datos) {
        try {
            return objectMapper.writeValueAsString(datos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el detalle de bitacora", e);
        }
    }
}
