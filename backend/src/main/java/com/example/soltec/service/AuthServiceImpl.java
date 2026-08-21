package com.example.soltec.service;

import com.example.soltec.config.JwtUtil;
import com.example.soltec.dto.LoginRequest;
import com.example.soltec.dto.LoginResponse;
import com.example.soltec.dto.RegistroRequest;
import com.example.soltec.dto.RegistroResponse;
import com.example.soltec.entity.Cliente;
import com.example.soltec.entity.Rol;
import com.example.soltec.entity.Usuario;
import com.example.soltec.exception.CorreoDuplicadoException;
import com.example.soltec.exception.CredencialesInvalidasException;
import com.example.soltec.repository.ClienteRepository;
import com.example.soltec.repository.RolRepository;
import com.example.soltec.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String ROL_CLIENTE = "CLIENTE";

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByCorreo(request.getCorreo())
                .orElseThrow(() -> new CredencialesInvalidasException("Correo o contrasena incorrectos"));

        if (!usuario.getActivo() || !passwordEncoder.matches(request.getContrasena(), usuario.getContrasenaHash())) {
            throw new CredencialesInvalidasException("Correo o contrasena incorrectos");
        }

        String token = jwtUtil.generarToken(usuario);
        return LoginResponse.builder()
                .token(token)
                .rol(usuario.getRol().getCodigo())
                .nombre(usuario.getNombres() + " " + usuario.getApellidos())
                .build();
    }

    @Override
    @Transactional
    public RegistroResponse registrar(RegistroRequest request) {
        if (usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new CorreoDuplicadoException("Ya existe un usuario registrado con ese correo");
        }

        Rol rolCliente = rolRepository.findByCodigo(ROL_CLIENTE)
                .orElseThrow(() -> new IllegalStateException("No existe el rol CLIENTE en la base de datos"));

        Usuario usuario = Usuario.builder()
                .rol(rolCliente)
                .nombres(request.getNombres())
                .apellidos(request.getApellidos())
                .correo(request.getCorreo())
                .contrasenaHash(passwordEncoder.encode(request.getContrasena()))
                .telefono(request.getTelefono())
                .activo(true)
                .build();
        usuario = usuarioRepository.save(usuario);

        Cliente cliente = Cliente.builder()
                .usuario(usuario)
                .nit(request.getNit())
                .direccion(request.getDireccion())
                .build();
        clienteRepository.save(cliente);

        return RegistroResponse.builder()
                .id(usuario.getId())
                .nombres(usuario.getNombres())
                .apellidos(usuario.getApellidos())
                .correo(usuario.getCorreo())
                .rol(rolCliente.getCodigo())
                .build();
    }
}
