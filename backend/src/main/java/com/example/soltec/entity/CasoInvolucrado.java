package com.example.soltec.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// La llena automaticamente el trigger tg_caso_marcar_involucrado cuando una
// denuncia referencia una orden de servicio. La aplicacion nunca inserta aqui.
@Entity
@Table(name = "caso_involucrado", schema = "soltec")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CasoInvolucrado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "caso_id", nullable = false)
    private Integer casoId;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(length = 200)
    private String motivo;
}
