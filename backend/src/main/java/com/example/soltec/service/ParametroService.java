package com.example.soltec.service;

import com.example.soltec.entity.Parametro;
import com.example.soltec.repository.ParametroRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Punto unico de lectura de soltec.parametro: el limite de adjuntos (y
// cualquier otro parametro configurable por el Administrador) se lee siempre
// de aqui, nunca queda quemado en el codigo.
@Service
@RequiredArgsConstructor
public class ParametroService {

    private static final String CLAVE_MAX_MB_ADJUNTO = "MAX_MB_ADJUNTO";
    private static final BigDecimal DEFAULT_MAX_MB_ADJUNTO = BigDecimal.TEN;

    private final ParametroRepository parametroRepository;

    public BigDecimal obtenerMaxMbAdjunto() {
        return parametroRepository.findByClave(CLAVE_MAX_MB_ADJUNTO)
                .map(Parametro::getValor)
                .orElse(DEFAULT_MAX_MB_ADJUNTO);
    }

    public long obtenerMaxBytesAdjunto() {
        return obtenerMaxMbAdjunto().multiply(BigDecimal.valueOf(1024L * 1024L)).longValue();
    }

    public String mensajeArchivoExcedeTamano() {
        return "El archivo excede el tamaño máximo permitido de " + formatearMb(obtenerMaxMbAdjunto()) + " MB.";
    }

    // BigDecimal en la BD tiene escala 4 (2.0000): se formatea sin ceros de
    // relleno para que el mensaje diga "2 MB" y no "2.0000 MB".
    private String formatearMb(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }
}
