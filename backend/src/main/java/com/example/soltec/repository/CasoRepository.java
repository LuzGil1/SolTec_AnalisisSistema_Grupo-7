package com.example.soltec.repository;

import com.example.soltec.entity.Caso;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CasoRepository extends JpaRepository<Caso, Integer> {

    List<Caso> findByClienteIdOrderByFechaRegistroDesc(Integer clienteId);

    Optional<Caso> findByIdAndClienteId(Integer id, Integer clienteId);

    // CU-05: casos activos del tecnico, del mas urgente al menos urgente.
    // "Activo" es literal a lo pedido (excluir CERRADO e IMPROCEDENTE), no
    // NOT es_final: DUPLICADO tambien es final pero nunca aplica a un caso
    // ya asignado a un tecnico.
    @Query("""
            SELECT c FROM Caso c
            JOIN FETCH c.tipoSolicitud
            JOIN FETCH c.prioridad
            JOIN FETCH c.estado
            WHERE c.tecnicoAsignadoId = :tecnicoId
              AND c.estado.codigo NOT IN ('CERRADO', 'IMPROCEDENTE')
            ORDER BY c.fechaLimiteResolucion ASC NULLS LAST
            """)
    List<Caso> findAsignadosActivos(@Param("tecnicoId") Integer tecnicoId);

    // CU-05: carga actual = misma definicion de "abierto" que usa
    // fn_calcular_score (NOT estado.es_final), para que el numero que ve el
    // tecnico coincida con lo que el algoritmo de asignacion tiene en cuenta.
    long countByTecnicoAsignadoIdAndEstado_EsFinalFalse(Integer tecnicoId);

    // Dashboard del supervisor: listado general desde vw_casos_general, todos
    // los filtros opcionales (NULL = sin filtro; el CAST es para que
    // PostgreSQL sepa el tipo del parametro aunque llegue nulo). Las fechas
    // se comparan en la zona de Guatemala, no en UTC.
    // Orden: el vencimiento mas proximo primero; al final las que no tienen
    // fecha limite (sugerencias) y las ya finalizadas, cuyo vencimiento ya no
    // es accionable, por fecha de registro descendente.
    @Query(value = """
            SELECT caso_id AS casoId, numero_boleta AS numeroBoleta, tipo_codigo AS tipoCodigo, tipo,
                   asunto, cliente, tecnico_id AS tecnicoId, tecnico, estado_codigo AS estadoCodigo,
                   estado, es_final AS esFinal, prioridad, fecha_registro AS fechaRegistro,
                   fecha_limite AS fechaLimite
            FROM soltec.vw_casos_general
            WHERE (CAST(:tipo AS varchar) IS NULL OR tipo_codigo = CAST(:tipo AS varchar))
              AND (CAST(:estado AS varchar) IS NULL OR estado_codigo = CAST(:estado AS varchar))
              AND (CAST(:tecnicoId AS integer) IS NULL OR tecnico_id = CAST(:tecnicoId AS integer))
              AND (CAST(:fechaDesde AS date) IS NULL
                   OR CAST(fecha_registro AT TIME ZONE 'America/Guatemala' AS date) >= CAST(:fechaDesde AS date))
              AND (CAST(:fechaHasta AS date) IS NULL
                   OR CAST(fecha_registro AT TIME ZONE 'America/Guatemala' AS date) <= CAST(:fechaHasta AS date))
              AND (CAST(:boleta AS varchar) IS NULL
                   OR numero_boleta ILIKE '%' || CAST(:boleta AS varchar) || '%')
            ORDER BY CASE WHEN es_final THEN NULL ELSE fecha_limite END ASC NULLS LAST,
                     fecha_registro DESC
            """, nativeQuery = true)
    List<CasoGeneralProyeccion> buscarGeneral(@Param("tipo") String tipo,
                                              @Param("estado") String estado,
                                              @Param("tecnicoId") Integer tecnicoId,
                                              @Param("fechaDesde") LocalDate fechaDesde,
                                              @Param("fechaHasta") LocalDate fechaHasta,
                                              @Param("boleta") String boleta);

    // CU Solicitudes sin asignar: la vista ya aplica el umbral
    // HORAS_ALERTA_SIN_ASIGNAR; aqui solo se agrega el cliente y se ordena
    // con la que mas tiempo lleva esperando primero (flujo normal, paso 3).
    @Query(value = """
            SELECT s.caso_id AS casoId, s.numero_boleta AS numeroBoleta, s.tipo, s.asunto,
                   u.nombres || ' ' || u.apellidos AS cliente, s.prioridad,
                   s.fecha_registro AS fechaRegistro, s.horas_en_cola AS horasEnCola,
                   s.fecha_limite_resolucion AS fechaLimite
            FROM soltec.vw_solicitudes_sin_asignar s
            JOIN soltec.caso c    ON c.id = s.caso_id
            JOIN soltec.usuario u ON u.id = c.cliente_id
            ORDER BY s.fecha_registro ASC
            """, nativeQuery = true)
    List<SolicitudSinAsignarProyeccion> buscarSinAsignar();

    // Tarjetas del dashboard del supervisor (RN01 del CU Dashboard del
    // Supervisor). Cada contador coincide con el listado al que lleva:
    // denuncias pendientes = no concluidas (vw_denuncias sin es_final);
    // escalamientos pendientes = vw_escalamientos (escaladas y las que el
    // supervisor atiende sin concluir).
    @Query(value = """
            SELECT (SELECT COUNT(*) FROM soltec.vw_denuncias WHERE NOT es_final) AS denunciasPendientes,
                   (SELECT COUNT(*) FROM soltec.vw_escalamientos) AS escalamientosPendientes,
                   (SELECT COUNT(*) FROM soltec.vw_solicitudes_sin_asignar) AS solicitudesSinAsignar
            """, nativeQuery = true)
    ResumenSupervisorProyeccion resumenSupervisor();

    // CU-04: reparte la bolsa de demanda entre los tecnicos con cupo. Toda la
    // logica de asignacion vive en la base (fn_asignar_solicitud).
    @Query(value = "SELECT soltec.fn_procesar_bolsa()", nativeQuery = true)
    Integer procesarBolsa();

    // Bloquea la fila del caso durante una gestion del supervisor: dos
    // clics seguidos (o dos supervisores) no pueden aplicar dos traslados
    // sobre el mismo estado de partida.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Caso c WHERE c.id = :id")
    Optional<Caso> bloquearPorId(@Param("id") Integer id);

    // CU Atencion de Denuncias: no concluidas, la mas proxima a vencer primero
    @Query(value = """
            SELECT caso_id AS casoId, numero_boleta AS numeroBoleta, asunto, cliente, involucrados,
                   estado_codigo AS estadoCodigo, estado, fecha_registro AS fechaRegistro,
                   fecha_limite AS fechaLimite
            FROM soltec.vw_denuncias
            WHERE NOT es_final
            ORDER BY fecha_limite ASC NULLS LAST, fecha_registro ASC
            """, nativeQuery = true)
    List<DenunciaProyeccion> listarDenuncias();

    // CU Revision de Escalamientos: la mas proxima a vencer primero
    @Query(value = """
            SELECT caso_id AS casoId, numero_boleta AS numeroBoleta, tipo, asunto, cliente,
                   escalada_por AS escaladaPor, motivo_escalamiento AS motivoEscalamiento,
                   fecha_escalamiento AS fechaEscalamiento, estado_codigo AS estadoCodigo, estado,
                   supervisor_responsable_id AS supervisorResponsableId,
                   supervisor_responsable AS supervisorResponsable, fecha_limite AS fechaLimite
            FROM soltec.vw_escalamientos
            ORDER BY fecha_limite ASC NULLS LAST, fecha_registro ASC
            """, nativeQuery = true)
    List<EscalamientoProyeccion> listarEscalamientos();

    // Quien escalo y por que, para el detalle de cualquier caso (aunque ya
    // no este en vw_escalamientos, p. ej. uno devuelto o concluido)
    @Query(value = """
            SELECT c.id AS casoId, c.numero_boleta AS numeroBoleta, NULL AS tipo, c.asunto, NULL AS cliente,
                   u.nombres || ' ' || u.apellidos AS escaladaPor, s.comentario AS motivoEscalamiento,
                   s.fecha AS fechaEscalamiento, NULL AS estadoCodigo, NULL AS estado,
                   c.supervisor_responsable_id AS supervisorResponsableId,
                   us.nombres || ' ' || us.apellidos AS supervisorResponsable,
                   c.fecha_limite_resolucion AS fechaLimite
            FROM soltec.caso c
            LEFT JOIN LATERAL (
                   SELECT s2.usuario_id, s2.comentario, s2.fecha
                   FROM soltec.seguimiento s2
                   JOIN soltec.estado_caso en ON en.id = s2.estado_nuevo_id
                   WHERE s2.caso_id = c.id AND en.codigo = 'ESCALADO'
                   ORDER BY s2.fecha DESC, s2.id DESC
                   LIMIT 1) s ON TRUE
            LEFT JOIN soltec.usuario u  ON u.id = s.usuario_id
            LEFT JOIN soltec.usuario us ON us.id = c.supervisor_responsable_id
            WHERE c.id = :casoId
            """, nativeQuery = true)
    Optional<EscalamientoProyeccion> obtenerDatosEscalamiento(@Param("casoId") Integer casoId);

    // CU Revision de Sugerencias: de la mas reciente a la mas antigua (RN02)
    @Query(value = """
            SELECT caso_id AS casoId, numero_boleta AS numeroBoleta, asunto, cliente,
                   estado_codigo AS estadoCodigo, estado, es_final AS esFinal,
                   fecha_registro AS fechaRegistro
            FROM soltec.vw_sugerencias
            ORDER BY fecha_registro DESC
            """, nativeQuery = true)
    List<SugerenciaProyeccion> listarSugerencias();

    // Traslado de estado hecho por el supervisor. estado_id, solucion y las
    // fechas estan mapeados de solo lectura en Caso (los llenan triggers o
    // el dueno del cambio), por eso va con UPDATE nativo, igual que la
    // atencion del tecnico (TecnicoCasoServiceImpl). Este UPDATE dispara
    // tg_caso_after_update_liberar: si el caso llega a un estado final, la
    // base intenta asignar el siguiente de la bolsa (inofensivo aqui: un
    // caso del supervisor no ocupaba cupo de ningun tecnico).
    @Modifying
    @Query(value = """
            UPDATE soltec.caso
               SET estado_id = :estadoId,
                   solucion = CASE WHEN :guardarSolucion THEN CAST(:texto AS text) ELSE solucion END,
                   fecha_resolucion = CASE WHEN :marcarResolucion THEN NOW() ELSE fecha_resolucion END,
                   fecha_cierre = CASE WHEN :cerrar THEN NOW()
                                       WHEN :reabrir THEN NULL
                                       ELSE fecha_cierre END,
                   supervisor_responsable_id = CASE WHEN :tomarResponsable THEN CAST(:supervisorId AS integer)
                                                    ELSE supervisor_responsable_id END
             WHERE id = :casoId
            """, nativeQuery = true)
    int registrarTraslado(@Param("casoId") Integer casoId,
                          @Param("estadoId") Integer estadoId,
                          @Param("guardarSolucion") boolean guardarSolucion,
                          @Param("texto") String texto,
                          @Param("marcarResolucion") boolean marcarResolucion,
                          @Param("cerrar") boolean cerrar,
                          @Param("reabrir") boolean reabrir,
                          @Param("tomarResponsable") boolean tomarResponsable,
                          @Param("supervisorId") Integer supervisorId);

    // CU Revision de Escalamientos FA01: la exclusion del tecnico, la vuelta
    // a EN_COLA, el seguimiento, la bitacora y la reasignacion viven en
    // fn_devolver_escalamiento (db/11). Devuelve el tecnico reasignado o null.
    @Query(value = "SELECT soltec.fn_devolver_escalamiento(:casoId, :supervisorId, CAST(:comentario AS text), CAST(:ip AS varchar))",
            nativeQuery = true)
    Integer devolverEscalamiento(@Param("casoId") Integer casoId,
                                 @Param("supervisorId") Integer supervisorId,
                                 @Param("comentario") String comentario,
                                 @Param("ip") String ip);
}
