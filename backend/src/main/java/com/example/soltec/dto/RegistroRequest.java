package com.example.soltec.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Registro de Usuario Externo (CU-02): siempre crea un usuario con rol CLIENTE
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistroRequest {

    @NotBlank(message = "Los nombres son obligatorios")
    @Size(max = 100, message = "Los nombres no pueden superar 100 caracteres")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 100, message = "Los apellidos no pueden superar 100 caracteres")
    private String apellidos;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 150, message = "El correo no puede superar 150 caracteres")
    private String correo;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
    private String contrasena;

    @Size(max = 20, message = "El telefono no puede superar 20 caracteres")
    private String telefono;

    @Size(max = 20, message = "El NIT no puede superar 20 caracteres")
    private String nit;

    @Size(max = 250, message = "La direccion no puede superar 250 caracteres")
    private String direccion;
}
