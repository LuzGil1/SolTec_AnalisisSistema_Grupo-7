package com.example.soltec.repository;

import com.example.soltec.entity.TipoSolicitud;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoSolicitudRepository extends JpaRepository<TipoSolicitud, Integer> {

    List<TipoSolicitud> findByActivoTrue();
}
