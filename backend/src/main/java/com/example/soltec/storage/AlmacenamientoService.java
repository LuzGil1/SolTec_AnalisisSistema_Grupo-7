package com.example.soltec.storage;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

// Local en desarrollo, Azure Blob Storage en produccion. El resto de la
// aplicacion solo conoce esta interfaz, nunca el mecanismo de guardado real.
public interface AlmacenamientoService {

    ArchivoGuardado guardar(MultipartFile archivo, Integer casoId) throws IOException;
}
