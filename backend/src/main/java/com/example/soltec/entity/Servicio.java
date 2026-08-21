package com.example.soltec.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "servicio", schema = "soltec")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 120, unique = true)
    private String nombre;

    @Column(length = 300)
    private String descripcion;

    // especialidad esta fuera del alcance de esta etapa; se guarda el id crudo
    @Column(name = "especialidad_id")
    private Integer especialidadId;

    @Column(nullable = false)
    private Boolean activo;
}
