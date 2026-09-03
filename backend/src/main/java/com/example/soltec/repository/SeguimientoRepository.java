package com.example.soltec.repository;

import com.example.soltec.entity.Seguimiento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeguimientoRepository extends JpaRepository<Seguimiento, Integer> {

    @Query("""
            SELECT s FROM Seguimiento s
            JOIN FETCH s.usuario
            LEFT JOIN FETCH s.estadoAnterior
            LEFT JOIN FETCH s.estadoNuevo
            WHERE s.casoId = :casoId
            ORDER BY s.fecha DESC
            """)
    List<Seguimiento> findByCasoIdOrderByFechaDesc(@Param("casoId") Integer casoId);
}
