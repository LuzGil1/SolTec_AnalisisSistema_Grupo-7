package com.example.soltec.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "adjunto", schema = "soltec")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Adjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "caso_id", nullable = false)
    private Integer casoId;

    // evidencia asociada a un seguimiento posterior; fuera de alcance de CU2
    @Column(name = "seguimiento_id")
    private Integer seguimientoId;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "tipo_mime", nullable = false, length = 100)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @Column(nullable = false, length = 500)
    private String ruta;

    // columna CHAR(64) en la BD, no VARCHAR: hay que fijar el tipo JDBC
    // esperado para que la validacion de esquema de Hibernate no falle
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash_sha256", length = 64)
    private String hashSha256;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "fecha_carga", insertable = false, updatable = false)
    private OffsetDateTime fechaCarga;
}
