package com.example.soltec.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seguimiento", schema = "soltec")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seguimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "caso_id", nullable = false)
    private Integer casoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_anterior_id")
    private EstadoCaso estadoAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_nuevo_id")
    private EstadoCaso estadoNuevo;

    @Column(nullable = false, columnDefinition = "text")
    private String comentario;

    @Column(name = "visible_cliente", nullable = false)
    private Boolean visibleCliente;

    // la BD la llena con now() por defecto; la aplicacion nunca la escribe
    @Column(insertable = false, updatable = false)
    private OffsetDateTime fecha;
}
