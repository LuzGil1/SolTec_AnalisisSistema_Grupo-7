package com.example.soltec.config;

import com.example.soltec.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Genera y valida el JWT. Es la unica clase que conoce el secreto de firma.
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey obtenerLlave() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getCorreo())
                .claim("rol", usuario.getRol().getCodigo())
                .claim("nombre", usuario.getNombres() + " " + usuario.getApellidos())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusMillis(expirationMs)))
                .signWith(obtenerLlave())
                .compact();
    }

    public boolean esValido(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String obtenerCorreo(String token) {
        return parseClaims(token).getSubject();
    }

    public String obtenerRol(String token) {
        return parseClaims(token).get("rol", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(obtenerLlave())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
