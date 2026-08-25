package com.example.soltec.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tipo_solicitud", schema = "soltec")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipoSolicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 20, unique = true)
    private String codigo;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(name = "ingresa_bolsa", nullable = false)
    private Boolean ingresaBolsa;

    @Column(name = "requiere_sla", nullable = false)
    private Boolean requiereSla;

    @Column(name = "requiere_caso_previo", nullable = false)
    private Boolean requiereCasoPrevio;

    @Column(name = "solo_supervisor", nullable = false)
    private Boolean soloSupervisor;

    // usados por los triggers de ruteo/SLA; la aplicacion no los interpreta
    @Column(name = "estado_inicial_id", nullable = false)
    private Integer estadoInicialId;

    @Column(name = "prioridad_default_id", nullable = false)
    private Integer prioridadDefaultId;

    @Column(nullable = false)
    private Boolean activo;
}
