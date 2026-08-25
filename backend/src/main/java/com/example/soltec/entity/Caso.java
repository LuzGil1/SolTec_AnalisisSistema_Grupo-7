package com.example.soltec.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "caso", schema = "soltec")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Caso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // la llena el trigger tg_caso_before_insert con el formato SOL-AAAA-NNNNNN
    @Column(name = "numero_boleta", insertable = false, updatable = false, length = 20)
    private String numeroBoleta;

    @Column(name = "cliente_id", nullable = false)
    private Integer clienteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_solicitud_id", nullable = false)
    private TipoSolicitud tipoSolicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id")
    private Servicio servicio;

    // la llena el trigger a partir de tipo_solicitud.prioridad_default_id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prioridad_id", insertable = false, updatable = false)
    private Prioridad prioridad;

    // la llena el trigger segun el ruteo por tipo de solicitud
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_id", insertable = false, updatable = false)
    private EstadoCaso estado;

    @Column(name = "caso_relacionado_id")
    private Integer casoRelacionadoId;

    @Column(name = "tecnico_asignado_id")
    private Integer tecnicoAsignadoId;

    @Column(nullable = false, length = 150)
    private String asunto;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private OffsetDateTime fechaRegistro;

    // SLA: la llena el trigger, solo para los tipos que lo requieren
    @Column(name = "fecha_limite_respuesta", insertable = false, updatable = false)
    private OffsetDateTime fechaLimiteRespuesta;

    @Column(name = "fecha_limite_resolucion", insertable = false, updatable = false)
    private OffsetDateTime fechaLimiteResolucion;

    @Column(name = "fecha_asignacion", insertable = false, updatable = false)
    private OffsetDateTime fechaAsignacion;

    @Column(name = "fecha_resolucion", insertable = false, updatable = false)
    private OffsetDateTime fechaResolucion;

    @Column(name = "fecha_cierre", insertable = false, updatable = false)
    private OffsetDateTime fechaCierre;

    @Column(columnDefinition = "text")
    private String solucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_servicio_id")
    private OrdenServicio ordenServicio;
}
