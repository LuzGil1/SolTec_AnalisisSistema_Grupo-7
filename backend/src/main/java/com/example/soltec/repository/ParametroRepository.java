package com.example.soltec.repository;

import com.example.soltec.entity.Parametro;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametroRepository extends JpaRepository<Parametro, String> {

    Optional<Parametro> findByClave(String clave);
}
