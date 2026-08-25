package com.example.soltec.controller;

import com.example.soltec.dto.ErrorResponse;
import com.example.soltec.exception.CredencialesInvalidasException;
import com.example.soltec.exception.SolicitudInvalidaException;
import java.util.stream.Collectors;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> credencialesInvalidas(CredencialesInvalidasException ex) {
        return construir(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalidaException ex) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accesoDenegado(AccessDeniedException ex) {
        return construir(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta accion");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> archivoDemasiadoGrande(MaxUploadSizeExceededException ex) {
        return construir(HttpStatus.BAD_REQUEST,
                "El archivo no pudo adjuntarse. Verifique que el formato sea valido y que no exceda el tamano maximo permitido.");
    }

    // Las reglas que ya valida la base de datos (reclamo sin caso previo, orden
    // de otro cliente, etc.) llegan aqui como una excepcion de acceso a datos:
    // se extrae el mensaje que puso el trigger con RAISE EXCEPTION y se
    // devuelve como 400 legible, en vez de un 500 generico.
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> errorBaseDeDatos(DataAccessException ex) {
        Throwable causa = ex.getMostSpecificCause();
        String mensaje = causa instanceof PSQLException
                ? causa.getMessage().replaceFirst("^ERROR:\\s*", "").split("\n")[0]
                : "No se pudo completar la operacion. Verifique los datos ingresados.";
        return construir(HttpStatus.BAD_REQUEST, mensaje);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(". "));
        return construir(HttpStatus.BAD_REQUEST, mensaje);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> general(Exception ex) {
        log.error("Error no controlado", ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrio un error inesperado");
    }

    private ResponseEntity<ErrorResponse> construir(HttpStatus status, String mensaje) {
        ErrorResponse error = ErrorResponse.builder()
                .mensaje(mensaje)
                .codigo(status.value())
                .build();
        return ResponseEntity.status(status).body(error);
    }
}
