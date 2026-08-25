package com.example.soltec.repository;

import com.example.soltec.entity.Caso;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CasoRepository extends JpaRepository<Caso, Integer> {

    List<Caso> findByClienteIdOrderByFechaRegistroDesc(Integer clienteId);

    Optional<Caso> findByIdAndClienteId(Integer id, Integer clienteId);
}
