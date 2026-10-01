package com.example.soltec.repository;

import com.example.soltec.entity.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    // Personal de SolTec: usuarios activos que no son clientes. Alimenta el
    // selector de "Agregar involucrado" de las denuncias.
    @Query(value = """
            SELECT u.id, u.nombres || ' ' || u.apellidos AS nombre, r.nombre AS rol
            FROM soltec.usuario u
            JOIN soltec.rol r ON r.id = u.rol_id
            WHERE u.activo AND r.codigo <> 'CLIENTE'
            ORDER BY r.nombre, nombre
            """, nativeQuery = true)
    List<PersonalProyeccion> listarPersonal();
}
