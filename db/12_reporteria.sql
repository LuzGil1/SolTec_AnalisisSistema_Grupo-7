-- =====================================================================
-- 12_reporteria.sql
-- Dashboard de graficas y reporteria
--
-- Crea soltec.vw_reporte_solicitudes: UNA FILA POR SOLICITUD, para que
-- cualquier reporte sea un GROUP BY sobre esta sola vista. Es de solo
-- lectura y no se mapea con JPA.
--
-- Correr DESPUES de 01-11. No modifica tablas ni funciones existentes.
-- Se puede volver a correr.
-- =====================================================================

SET search_path TO soltec, public;

-- =====================================================================
-- 1. Zona horaria de la base
--
-- HALLAZGO: fn_bitacora_encadenar y fn_verificar_integridad_bitacora
-- arman el hash con fecha_hora::TEXT, y ese texto depende de la zona
-- horaria de la sesion. El mismo instante se escribe "10:00:00-06" en
-- Guatemala y "16:00:00+00" en UTC. Si una sesion escribe la bitacora en
-- una zona y otra la verifica en una zona distinta, el hash no coincide
-- y la verificacion reporta ALTERADO aunque nadie haya tocado el dato.
--
-- NO se corrige en las funciones: cambiarlas invalidaria todos los
-- hashes ya guardados. Se fija una sola zona para las sesiones de la
-- base. El ALTER aplica a las conexiones nuevas; el SET cubre esta.
--
-- Prueba: la bitacora de soltec_db da INTEGRA en America/Guatemala y
-- ALTERADO en UTC y en America/New_York, sin que nadie la haya tocado.
-- Sus filas son consistentes porque el backend siempre corrio en
-- Guatemala: el riesgo es a futuro, en un servidor con otra zona.
--
-- Este ALTER no alcanza por si solo para una aplicacion Java: el driver
-- JDBC envia la zona de la JVM al conectarse y esa reemplaza la de la
-- base. Medido con pgjdbc 42.7.4 en una JVM en UTC: la sesion queda en
-- UTC y la fila que escribe sale ALTERADO. El backend esta cubierto dos
-- veces: SoltecApplication.main() fija TimeZone.setDefault a Guatemala, y
-- application.properties repite la zona en connection-init-sql.
-- =====================================================================
DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET TimeZone = %L',
                   current_database(), 'America/Guatemala');
END;
$$;

SET TIME ZONE 'America/Guatemala';

-- =====================================================================
-- 2. Metas del dashboard: gauge, bullet, termometro y semaforo
-- =====================================================================
INSERT INTO parametro (clave, valor, descripcion)
SELECT v.clave, v.valor, v.descripcion
FROM (VALUES
    ('META_SLA',        90.0000::NUMERIC, 'Meta de cumplimiento de SLA, en porcentaje'),
    ('META_RESOLUCION', 85.0000::NUMERIC, 'Meta de solicitudes resueltas, en porcentaje')
) AS v(clave, valor, descripcion)
WHERE NOT EXISTS (SELECT 1 FROM parametro p WHERE p.clave = v.clave);

-- =====================================================================
-- 3. vw_reporte_solicitudes
--
-- Criterios:
--   - especialidad sale de servicio.especialidad_id. tecnico_especialidad
--     es N a M y un join directo duplicaria filas.
--   - escalamientos se cuenta desde seguimiento: es el unico lugar donde
--     cada escalamiento queda como dato propio (estado_nuevo = ESCALADO).
--     En soltec_db hay 13 en seguimiento y 10 en bitacora. Los 3 sin
--     bitacora (SOL-2026-000052, 053 y 058) tienen la misma hora exacta,
--     2026-10-08 20:15:57, asi que parecen cargados por fuera de la
--     aplicacion. Su texto no aparece en ningun script de db/.
--   - resolucion_vigente ignora la fecha_resolucion de una solicitud
--     reabierta (RESUELTO -> EN_PROCESO conserva la fecha anterior).
--   - primera_respuesta excluye los seguimientos automaticos, que quedan
--     a nombre del tecnico: los que pasan a ASIGNADO o EN_COLA.
--   - Los derivados de fecha usan America/Guatemala de forma explicita,
--     para no depender de la zona de la sesion.
-- =====================================================================
DROP VIEW IF EXISTS vw_reporte_solicitudes;

CREATE VIEW vw_reporte_solicitudes AS
SELECT  c.id                                    AS caso_id,
        c.numero_boleta,
        c.asunto,
        c.descripcion,

        t.nombre                                AS tipo,
        p.nombre                                AS prioridad,
        e.nombre                                AS situacion,
        ut.nombres || ' ' || ut.apellidos       AS tecnico,
        uc.nombres || ' ' || uc.apellidos       AS cliente,
        cl.nit                                  AS cliente_nit,
        s.nombre                                AS servicio,
        es.nombre                               AS especialidad,

        -- Ids y codigos para filtrar y ordenar sin joins nuevos
        c.tipo_solicitud_id,
        c.prioridad_id,
        c.estado_id,
        c.servicio_id,
        s.especialidad_id,
        c.tecnico_asignado_id                   AS tecnico_id,
        c.cliente_id,
        c.supervisor_responsable_id,
        t.codigo                                AS tipo_codigo,
        p.codigo                                AS prioridad_codigo,
        e.codigo                                AS situacion_codigo,
        p.orden                                 AS prioridad_orden,
        e.orden                                 AS situacion_orden,

        CASE
            WHEN e.codigo IN ('RECIBIDO', 'EN_COLA') THEN 'Sin asignar'
            WHEN e.codigo IN ('ASIGNADO', 'EN_PROCESO') THEN
                CASE WHEN c.tecnico_asignado_id IS NULL THEN 'Supervisor' ELSE 'Técnico' END
            WHEN e.codigo = 'ESPERA_CLIENTE' THEN 'Cliente'
            WHEN e.codigo IN ('EN_REVISION', 'ESCALADO', 'EN_BACKLOG') THEN 'Supervisor'
            WHEN e.codigo IN ('RESUELTO', 'CERRADO', 'IMPROCEDENTE', 'DUPLICADO') THEN 'Ya cerrada'
        END                                     AS rol_responsable,

        c.fecha_registro,
        c.fecha_asignacion,
        c.fecha_resolucion,
        c.fecha_cierre,
        c.fecha_limite_respuesta,
        c.fecha_limite_resolucion,

        (fl.registro)::DATE                  AS dia,
        CASE EXTRACT(ISODOW FROM fl.registro)::INTEGER
            WHEN 1 THEN 'Lunes'
            WHEN 2 THEN 'Martes'
            WHEN 3 THEN 'Miércoles'
            WHEN 4 THEN 'Jueves'
            WHEN 5 THEN 'Viernes'
            WHEN 6 THEN 'Sábado'
            WHEN 7 THEN 'Domingo'
        END                                     AS dia_semana,
        EXTRACT(ISODOW FROM fl.registro)::INTEGER AS dia_semana_num,
        EXTRACT(HOUR FROM fl.registro)::INTEGER   AS hora,
        TO_CHAR(fl.registro, 'IYYY-"S"IW')   AS semana,
        TO_CHAR(fl.registro, 'YYYY-MM')      AS mes,
        EXTRACT(YEAR FROM fl.registro)::INTEGER   AS anio,

        ROUND((EXTRACT(EPOCH FROM (c.fecha_asignacion - c.fecha_registro)) / 3600)::NUMERIC, 1)
                                                AS horas_asignacion,
        ROUND((EXTRACT(EPOCH FROM (res.vigente - c.fecha_registro)) / 3600)::NUMERIC, 1)
                                                AS horas_resolucion,

        -- TRUE: resuelta en plazo. FALSE: resuelta tarde, o abierta y
        -- vencida. NULL: abierta en plazo, sin SLA, o concluida sin
        -- resolucion (improcedente o duplicado). Solo NULL queda fuera
        -- del porcentaje de cumplimiento.
        CASE
            WHEN c.fecha_limite_resolucion IS NULL THEN NULL
            WHEN res.vigente IS NOT NULL THEN res.vigente <= c.fecha_limite_resolucion
            WHEN e.es_final THEN NULL
            WHEN NOW() > c.fecha_limite_resolucion THEN FALSE
        END                                     AS cumplio_sla,

        COALESCE(NOT e.es_final
                 AND res.vigente IS NULL
                 AND NOW() > c.fecha_limite_resolucion, FALSE) AS vencida,

        NOT e.es_final                          AS abierta,

        res.vigente IS NOT NULL                 AS resuelta,

        hist.escalamientos,
        GREATEST(asig.registros - 1, 0)         AS reasignaciones,
        vig.score                               AS score_asignacion,

        resp.primera_respuesta,
        -- Mismo criterio que cumplio_sla: una solicitud desestimada no
        -- tenia nada que responder.
        CASE WHEN e.codigo IN ('IMPROCEDENTE', 'DUPLICADO') THEN NULL
             ELSE COALESCE(resp.primera_respuesta, NOW()) > c.fecha_limite_respuesta
        END                                     AS incumplio_respuesta,

        -- Las dos razones por las que alguien queda en caso_involucrado.
        -- 'Escaló la solicitud' es el texto fijo de fn_devolver_escalamiento;
        -- en una denuncia el motivo lo redacta el supervisor. DEBILIDAD: la
        -- distincion depende de ese texto. Si un supervisor escribiera
        -- exactamente lo mismo en una denuncia, contaria como tecnico
        -- excluido y no como personal senalado.
        EXISTS (SELECT 1 FROM caso_involucrado ci
                WHERE ci.caso_id = c.id
                  AND ci.motivo IS DISTINCT FROM 'Escaló la solicitud')
                                                AS con_personal_senalado,
        EXISTS (SELECT 1 FROM caso_involucrado ci
                WHERE ci.caso_id = c.id
                  AND ci.motivo = 'Escaló la solicitud')
                                                AS con_tecnico_excluido,

        EXISTS (SELECT 1 FROM adjunto a WHERE a.caso_id = c.id) AS con_adjuntos,
        c.orden_servicio_id IS NOT NULL         AS con_orden_servicio,
        c.caso_relacionado_id IS NOT NULL       AS con_caso_relacionado

FROM caso c
JOIN tipo_solicitud t ON t.id = c.tipo_solicitud_id
JOIN prioridad p      ON p.id = c.prioridad_id
JOIN estado_caso e    ON e.id = c.estado_id
JOIN usuario uc       ON uc.id = c.cliente_id
JOIN cliente cl       ON cl.usuario_id = c.cliente_id
LEFT JOIN usuario ut      ON ut.id = c.tecnico_asignado_id
LEFT JOIN servicio s      ON s.id = c.servicio_id
LEFT JOIN especialidad es ON es.id = s.especialidad_id
CROSS JOIN LATERAL (
        SELECT c.fecha_registro AT TIME ZONE 'America/Guatemala' AS registro
) fl
CROSS JOIN LATERAL (
        SELECT CASE WHEN e.codigo IN ('RESUELTO', 'CERRADO')
                    THEN c.fecha_resolucion END AS vigente
) res
CROSS JOIN LATERAL (
        SELECT COUNT(*)::INTEGER AS escalamientos
        FROM seguimiento sg
        JOIN estado_caso en ON en.id = sg.estado_nuevo_id
        WHERE sg.caso_id = c.id AND en.codigo = 'ESCALADO'
) hist
CROSS JOIN LATERAL (
        SELECT COUNT(*)::INTEGER AS registros
        FROM asignacion a
        WHERE a.caso_id = c.id
) asig
CROSS JOIN LATERAL (
        SELECT MIN(sg.fecha) AS primera_respuesta
        FROM seguimiento sg
        LEFT JOIN estado_caso en ON en.id = sg.estado_nuevo_id
        WHERE sg.caso_id = c.id
          AND (en.codigo IS NULL OR en.codigo NOT IN ('ASIGNADO', 'EN_COLA'))
) resp
LEFT JOIN LATERAL (
        SELECT a.score
        FROM asignacion a
        WHERE a.caso_id = c.id
          AND a.tecnico_id = c.tecnico_asignado_id
          AND a.fecha_liberacion IS NULL
        ORDER BY a.fecha_asignacion DESC, a.id DESC
        LIMIT 1
) vig ON TRUE;

COMMENT ON VIEW vw_reporte_solicitudes IS
  'Dashboard y reporteria: una fila por solicitud con nombres, tiempos, SLA y contadores ya resueltos. Solo lectura.';

-- =====================================================================
-- Comprobaciones sugeridas
-- =====================================================================
-- SELECT COUNT(*), COUNT(DISTINCT caso_id) FROM vw_reporte_solicitudes;
-- SELECT rol_responsable, COUNT(*) FROM vw_reporte_solicitudes GROUP BY 1;
-- SELECT * FROM fn_verificar_integridad_bitacora();
