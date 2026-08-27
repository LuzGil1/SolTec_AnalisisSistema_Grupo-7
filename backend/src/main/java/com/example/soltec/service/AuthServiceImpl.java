package com.example.soltec.service;

import com.example.soltec.config.JwtUtil;
import com.example.soltec.dto.LoginRequest;
import com.example.soltec.dto.LoginResponse;
import com.example.soltec.entity.Usuario;
import com.example.soltec.exception.CredencialesInvalidasException;
import com.example.soltec.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByCorreo(request.getCorreo())
                .orElseThrow(() -> new CredencialesInvalidasException("Correo o contraseña incorrectos"));

        if (!usuario.getActivo() || !passwordEncoder.matches(request.getContrasena(), usuario.getContrasenaHash())) {
            throw new CredencialesInvalidasException("Correo o contraseña incorrectos");
        }

        String token = jwtUtil.generarToken(usuario);
        return LoginResponse.builder()
                .token(token)
                .rol(usuario.getRol().getCodigo())
                .nombre(usuario.getNombres() + " " + usuario.getApellidos())
                .build();
    }
}
