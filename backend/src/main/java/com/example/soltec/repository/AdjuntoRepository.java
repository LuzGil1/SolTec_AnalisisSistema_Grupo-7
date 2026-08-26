package com.example.soltec.repository;

import com.example.soltec.entity.Adjunto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdjuntoRepository extends JpaRepository<Adjunto, Integer> {

    List<Adjunto> findByCasoIdOrderByIdAsc(Integer casoId);

    Optional<Adjunto> findByIdAndCasoId(Integer id, Integer casoId);
}
