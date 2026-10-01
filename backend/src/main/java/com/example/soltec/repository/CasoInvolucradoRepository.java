package com.example.soltec.repository;

import com.example.soltec.entity.CasoInvolucrado;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Solo lectura e insercion: el personal involucrado no se retira (RN04 del
// CU Atencion de Denuncias), asi que no hay metodo de borrado en uso.
public interface CasoInvolucradoRepository extends JpaRepository<CasoInvolucrado, Integer> {

    boolean existsByCasoIdAndUsuarioId(Integer casoId, Integer usuarioId);

    @Query(value = """
            SELECT ci.usuario_id AS usuarioId, u.nombres || ' ' || u.apellidos AS nombre,
                   r.nombre AS rol, ci.motivo
            FROM soltec.caso_involucrado ci
            JOIN soltec.usuario u ON u.id = ci.usuario_id
            JOIN soltec.rol r     ON r.id = u.rol_id
            WHERE ci.caso_id = :casoId
            ORDER BY ci.id
            """, nativeQuery = true)
    List<InvolucradoProyeccion> listarPorCaso(@Param("casoId") Integer casoId);
}
