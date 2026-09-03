package com.example.soltec.repository;

import com.example.soltec.entity.Caso;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CasoRepository extends JpaRepository<Caso, Integer> {

    List<Caso> findByClienteIdOrderByFechaRegistroDesc(Integer clienteId);

    Optional<Caso> findByIdAndClienteId(Integer id, Integer clienteId);

    // CU-05: casos activos del tecnico, del mas urgente al menos urgente.
    // "Activo" es literal a lo pedido (excluir CERRADO e IMPROCEDENTE), no
    // NOT es_final: DUPLICADO tambien es final pero nunca aplica a un caso
    // ya asignado a un tecnico.
    @Query("""
            SELECT c FROM Caso c
            JOIN FETCH c.tipoSolicitud
            JOIN FETCH c.prioridad
            JOIN FETCH c.estado
            WHERE c.tecnicoAsignadoId = :tecnicoId
              AND c.estado.codigo NOT IN ('CERRADO', 'IMPROCEDENTE')
            ORDER BY c.fechaLimiteResolucion ASC NULLS LAST
            """)
    List<Caso> findAsignadosActivos(@Param("tecnicoId") Integer tecnicoId);

    // CU-05: carga actual = misma definicion de "abierto" que usa
    // fn_calcular_score (NOT estado.es_final), para que el numero que ve el
    // tecnico coincida con lo que el algoritmo de asignacion tiene en cuenta.
    long countByTecnicoAsignadoIdAndEstado_EsFinalFalse(Integer tecnicoId);
}
