package com.example.soltec.repository;

import com.example.soltec.entity.EstadoCaso;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadoCasoRepository extends JpaRepository<EstadoCaso, Integer> {

    Optional<EstadoCaso> findByCodigo(String codigo);
}
