package com.example.soltec.repository;

import com.example.soltec.entity.Servicio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicioRepository extends JpaRepository<Servicio, Integer> {

    List<Servicio> findByActivoTrue();
}
