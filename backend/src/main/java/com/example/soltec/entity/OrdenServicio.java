package com.example.soltec.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Historico de servicios prestados. Precargado, de solo lectura: la
// aplicacion nunca crea ni modifica ordenes de servicio.
@Entity
@Table(name = "orden_servicio", schema = "soltec")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "numero_orden", nullable = false, length = 20, unique = true)
    private String numeroOrden;

    @Column(name = "cliente_id", nullable = false)
    private Integer clienteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    // tecnico que presto el servicio; es lo que activa el conflicto de interes
    // en una denuncia, pero la aplicacion no lo expone en las respuestas
    @Column(name = "tecnico_id")
    private Integer tecnicoId;

    @Column(name = "fecha_servicio", nullable = false)
    private LocalDate fechaServicio;

    @Column(length = 300)
    private String descripcion;
}
