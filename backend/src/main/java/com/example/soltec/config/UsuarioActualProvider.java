package com.example.soltec.config;

import com.example.soltec.entity.Usuario;
import com.example.soltec.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// JwtAuthFilter deja el correo como "name" de la autenticacion; este
// componente lo resuelve al Usuario real para que los servicios no repitan
// la busqueda por correo cada vez.
@Component
@RequiredArgsConstructor
public class UsuarioActualProvider {

    private final UsuarioRepository usuarioRepository;

    public Usuario obtener() {
        String correo = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado: " + correo));
    }
}
