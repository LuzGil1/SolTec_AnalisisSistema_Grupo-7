package com.example.soltec.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// La bitacora es de solo insercion: nunca se actualiza ni se borra desde la
// aplicacion. Todo pasa por fn_registrar_bitacora, que ademas encadena el hash.
@Service
@RequiredArgsConstructor
public class BitacoraService {

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper;

    // Caso comun: el registro forma parte de la misma transaccion que la
    // operacion que audita (p. ej. registrar una solicitud nueva). Si la
    // operacion falla, es correcto que el registro tambien se revierta: no
    // tiene sentido una entrada de bitacora sobre un caso que nunca se creo.
    // Al no abrir una transaccion/conexion aparte, no hay riesgo de que quede
    // una segunda conexion esperando el candado de fn_bitacora_encadenar.
    public void registrar(Integer usuarioId, String ip, String modulo, String accion,
                           String entidad, String entidadId, String descripcion, Object datos) {
        insertar(usuarioId, ip, modulo, accion, entidad, entidadId, descripcion, datos);
    }

    // Caso de seguridad: el registro debe quedar aunque la transaccion que lo
    // origina termine en rollback (p. ej. un intento de acceso no autorizado
    // que se rechaza con una excepcion despues de auditar el intento). Usar
    // esto solo cuando ese requisito aplique; para el resto, registrar().
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAudita(Integer usuarioId, String ip, String modulo, String accion,
                                 String entidad, String entidadId, String descripcion, Object datos) {
        insertar(usuarioId, ip, modulo, accion, entidad, entidadId, descripcion, datos);
    }

    private void insertar(Integer usuarioId, String ip, String modulo, String accion,
                           String entidad, String entidadId, String descripcion, Object datos) {
        String json = datos == null ? null : serializar(datos);

        // fn_bitacora_encadenar() serializa las inserciones con
        // pg_advisory_xact_lock (para no romper la cadena de hash). Sin limite,
        // una transaccion que se queda pegada en otro lado deja el lock tomado
        // y CUALQUIER escritura de bitacora futura en TODA la aplicacion espera
        // para siempre. lock_timeout hace que, si no se puede tomar en unos
        // segundos, falle rapido con error en vez de colgar el hilo (y la
        // conexion) indefinidamente.
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '5s'").executeUpdate();

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
