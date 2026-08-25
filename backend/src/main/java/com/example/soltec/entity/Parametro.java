package com.example.soltec.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Parametros configurables por el Administrador (ej. MAX_MB_ADJUNTO). Solo lectura desde CU2.
@Entity
@Table(name = "parametro", schema = "soltec")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parametro {

    @Id
    @Column(length = 40)
    private String clave;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal valor;

    @Column(length = 200)
    private String descripcion;
}
