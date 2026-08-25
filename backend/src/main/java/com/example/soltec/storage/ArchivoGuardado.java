package com.example.soltec.storage;

// Resultado de guardar un archivo: la ruta/clave con la que se recupera
// despues (en Azure Blob Storage sera el nombre del blob) y su hash de integridad.
public record ArchivoGuardado(String ruta, String hashSha256) {
}
