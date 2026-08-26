package com.example.soltec.storage;

// Resultado de guardar un archivo: la ruta/clave con la que se recupera
// despues (en Azure Blob Storage sera el nombre del blob), su hash de
// integridad y el tipo MIME real detectado a partir del contenido (no el
// declarado por el cliente).
public record ArchivoGuardado(String ruta, String hashSha256, String tipoMimeDetectado) {
}
