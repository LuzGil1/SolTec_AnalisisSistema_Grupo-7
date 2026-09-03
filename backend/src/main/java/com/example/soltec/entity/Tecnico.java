package com.example.soltec.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tecnico", schema = "soltec")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tecnico {

    // tecnico.usuario_id es a la vez PK y FK a usuario.id (relacion 1 a 1)
    @Id
    @Column(name = "usuario_id")
    private Integer usuarioId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "capacidad_maxima", nullable = false)
    private Short capacidadMaxima;

    @Column(nullable = false)
    private Boolean disponible;
}
