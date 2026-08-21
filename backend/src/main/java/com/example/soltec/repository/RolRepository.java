package com.example.soltec.repository;

import com.example.soltec.entity.Rol;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolRepository extends JpaRepository<Rol, Integer> {

    Optional<Rol> findByCodigo(String codigo);
}
