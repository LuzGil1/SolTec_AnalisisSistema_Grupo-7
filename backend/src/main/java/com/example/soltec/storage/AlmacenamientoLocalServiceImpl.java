package com.example.soltec.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AlmacenamientoLocalServiceImpl implements AlmacenamientoService {

    private final Path directorioBase;

    public AlmacenamientoLocalServiceImpl(@Value("${app.almacenamiento.directorio-local}") String directorioBase) {
        this.directorioBase = Path.of(directorioBase).toAbsolutePath().normalize();
    }

    @Override
    public ArchivoGuardado guardar(MultipartFile archivo, Integer casoId) throws IOException {
        Path carpetaCaso = directorioBase.resolve(String.valueOf(casoId));
        Files.createDirectories(carpetaCaso);

        String nombreSaneado = StringUtils.cleanPath(archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "adjunto");
        String nombreArchivo = UUID.randomUUID() + "-" + nombreSaneado;
        Path destino = carpetaCaso.resolve(nombreArchivo).normalize();

        if (!destino.startsWith(carpetaCaso)) {
            throw new IOException("Nombre de archivo invalido");
        }

        try (InputStream entrada = archivo.getInputStream()) {
            Files.copy(entrada, destino);
        }

        String ruta = casoId + "/" + nombreArchivo;
        return new ArchivoGuardado(ruta, calcularSha256(destino));
    }

    private String calcularSha256(Path archivo) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream entrada = Files.newInputStream(archivo)) {
                byte[] buffer = new byte[8192];
                int leidos;
                while ((leidos = entrada.read(buffer)) != -1) {
                    digest.update(buffer, 0, leidos);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
