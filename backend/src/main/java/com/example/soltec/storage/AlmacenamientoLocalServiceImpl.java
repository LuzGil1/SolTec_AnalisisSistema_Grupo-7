package com.example.soltec.storage;

import com.example.soltec.exception.SolicitudInvalidaException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AlmacenamientoLocalServiceImpl implements AlmacenamientoService {

    private static final long TAMANO_MINIMO_BYTES = 1024L;
    private static final String MENSAJE_ARCHIVO_VACIO = "El archivo está vacío o dañado.";
    private static final String MENSAJE_TIPO_NO_COINCIDE = "El tipo de archivo no coincide con su contenido.";

    private static final Tika TIKA = new Tika();

    // Extension declarada por el nombre de archivo -> validador del tipo MIME
    // real detectado por contenido (magic bytes), no del Content-Type que
    // manda el cliente. Asi se evita subir un ejecutable renombrado como .pdf.
    private static final Map<String, Predicate<String>> TIPOS_POR_EXTENSION = Map.ofEntries(
            Map.entry("pdf", tipo -> tipo.equals("application/pdf")),
            Map.entry("jpg", tipo -> tipo.startsWith("image/")),
            Map.entry("jpeg", tipo -> tipo.startsWith("image/")),
            Map.entry("png", tipo -> tipo.startsWith("image/")),
            Map.entry("gif", tipo -> tipo.startsWith("image/")),
            Map.entry("webp", tipo -> tipo.startsWith("image/")),
            Map.entry("bmp", tipo -> tipo.startsWith("image/")),
            Map.entry("mp3", tipo -> tipo.startsWith("audio/")),
            Map.entry("wav", tipo -> tipo.startsWith("audio/")),
            Map.entry("ogg", tipo -> tipo.startsWith("audio/") || tipo.startsWith("video/")),
            Map.entry("mp4", tipo -> tipo.startsWith("video/")),
            Map.entry("mov", tipo -> tipo.startsWith("video/")),
            Map.entry("avi", tipo -> tipo.startsWith("video/")));

    private final Path directorioBase;

    public AlmacenamientoLocalServiceImpl(@Value("${app.almacenamiento.directorio-local}") String directorioBase) {
        this.directorioBase = Path.of(directorioBase).toAbsolutePath().normalize();
    }

    @Override
    public ArchivoGuardado guardar(MultipartFile archivo, Integer casoId) throws IOException {
        if (archivo.getSize() < TAMANO_MINIMO_BYTES) {
            throw new SolicitudInvalidaException(MENSAJE_ARCHIVO_VACIO);
        }

        String tipoMimeReal = detectarYValidarTipoReal(archivo);

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

        // La escritura a disco ocurre fuera de la transaccion de base de datos:
        // se verifica aqui, antes de volver al servicio que crea el registro
        // en adjunto, para que un fallo de escritura nunca deje un registro
        // huerfano apuntando a un archivo inexistente o incompleto.
        if (!Files.isRegularFile(destino) || Files.size(destino) != archivo.getSize()) {
            Files.deleteIfExists(destino);
            throw new IOException("El archivo no se pudo guardar correctamente en el almacenamiento.");
        }

        String ruta = casoId + "/" + nombreArchivo;
        return new ArchivoGuardado(ruta, calcularSha256(destino), tipoMimeReal);
    }

    @Override
    public Resource recuperar(String ruta) throws IOException {
        Path destino = directorioBase.resolve(ruta).normalize();
        if (!destino.startsWith(directorioBase) || !Files.isRegularFile(destino)) {
            throw new IOException("El archivo solicitado no existe");
        }
        return new UrlResource(destino.toUri());
    }

    private String detectarYValidarTipoReal(MultipartFile archivo) throws IOException {
        String extension = extraerExtension(archivo.getOriginalFilename());
        Predicate<String> tipoEsperado = TIPOS_POR_EXTENSION.get(extension);
        if (tipoEsperado == null) {
            throw new SolicitudInvalidaException(MENSAJE_TIPO_NO_COINCIDE);
        }

        String tipoReal;
        try (InputStream entrada = archivo.getInputStream()) {
            tipoReal = TIKA.detect(entrada);
        }

        if (!tipoEsperado.test(tipoReal)) {
            throw new SolicitudInvalidaException(MENSAJE_TIPO_NO_COINCIDE);
        }
        return tipoReal;
    }

    private String extraerExtension(String nombreArchivo) {
        if (nombreArchivo == null) {
            return "";
        }
        int punto = nombreArchivo.lastIndexOf('.');
        if (punto < 0 || punto == nombreArchivo.length() - 1) {
            return "";
        }
        return nombreArchivo.substring(punto + 1).toLowerCase(Locale.ROOT);
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
