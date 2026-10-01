package com.example.soltec.repository;

import com.example.soltec.entity.Tecnico;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TecnicoRepository extends JpaRepository<Tecnico, Integer> {

    @Query(value = """
            SELECT tecnico_id AS tecnicoId, nombre, correo, especialidades,
                   casos_abiertos AS casosAbiertos, capacidad_maxima AS capacidadMaxima,
                   disponible, motivo_no_disponible AS motivoNoDisponible
            FROM soltec.vw_carga_tecnicos
            ORDER BY nombre
            """, nativeQuery = true)
    List<CargaTecnicoProyeccion> listarCarga();

    @Query(value = """
            SELECT tecnico_id AS tecnicoId, nombre, correo, especialidades,
                   casos_abiertos AS casosAbiertos, capacidad_maxima AS capacidadMaxima,
                   disponible, motivo_no_disponible AS motivoNoDisponible
            FROM soltec.vw_carga_tecnicos
            WHERE tecnico_id = :tecnicoId
            """, nativeQuery = true)
    Optional<CargaTecnicoProyeccion> obtenerCarga(@Param("tecnicoId") Integer tecnicoId);

    // La liberacion de casos NO se hace aqui: la dispara el trigger existente
    // sobre tecnico.disponible (db/06). Los RAISE de la funcion (motivo
    // vacio, tecnico ya en ese estado) llegan como DataAccessException y el
    // GlobalExceptionHandler los devuelve como 400 con el mensaje.
    @Query(value = """
            SELECT casos_liberados AS casosLiberados, casos_asignados AS casosAsignados
            FROM soltec.fn_cambiar_disponibilidad(:tecnicoId, :disponible, CAST(:motivo AS varchar),
                                                  :usuarioId, CAST(:ip AS varchar))
            """, nativeQuery = true)
    CambioDisponibilidadProyeccion cambiarDisponibilidad(@Param("tecnicoId") Integer tecnicoId,
                                                         @Param("disponible") Boolean disponible,
                                                         @Param("motivo") String motivo,
                                                         @Param("usuarioId") Integer usuarioId,
                                                         @Param("ip") String ip);

    @Modifying
    @Query(value = "UPDATE soltec.tecnico SET capacidad_maxima = :capacidad WHERE usuario_id = :tecnicoId",
            nativeQuery = true)
    int actualizarCapacidad(@Param("tecnicoId") Integer tecnicoId, @Param("capacidad") short capacidad);
}
