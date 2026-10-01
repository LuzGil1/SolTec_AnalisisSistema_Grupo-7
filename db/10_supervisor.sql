-- =====================================================================
-- 10_supervisor.sql
-- Módulo del Supervisor de Soporte — primera etapa
--   Dashboard del supervisor (listado general)
--   Solicitudes sin asignar
--   Administración de técnicos (disponibilidad y capacidad)
--
-- Correr DESPUÉS de 01-09, sobre una base ya instalada. No modifica ni
-- reemplaza ningún script anterior. Se puede volver a correr: todo es
-- IF NOT EXISTS / CREATE OR REPLACE.
-- =====================================================================

SET search_path TO soltec, public;

-- =====================================================================
-- 1. Motivo de la no disponibilidad del técnico
--
-- CU Administración de Técnicos, RN02: marcar a un técnico como no
-- disponible exige indicar el motivo. Se llena al marcarlo no disponible
-- y se limpia al marcarlo disponible otra vez (fn_cambiar_disponibilidad).
-- El histórico de los cambios queda en la bitácora, no en esta columna.
-- =====================================================================
ALTER TABLE tecnico
    ADD COLUMN IF NOT EXISTS motivo_no_disponible VARCHAR(200);

COMMENT ON COLUMN tecnico.motivo_no_disponible IS
  'Motivo vigente de la no disponibilidad. NULL cuando el técnico está disponible.';

-- =====================================================================
-- 2. vw_casos_general — listado general del supervisor
--
-- Todas las solicitudes, de todos los clientes y todos los técnicos: el
-- supervisor no tiene filtro de propiedad. Trae el código además del
-- nombre de tipo/estado/prioridad para que el backend filtre por código
-- y la pantalla muestre el nombre. fecha_limite es la de resolución
-- (la misma que ve el técnico en su dashboard).
-- =====================================================================
CREATE OR REPLACE VIEW vw_casos_general AS
SELECT  c.id                                    AS caso_id,
        c.numero_boleta,
        t.codigo                                AS tipo_codigo,
        t.nombre                                AS tipo,
        c.asunto,
        c.cliente_id,
        uc.nombres || ' ' || uc.apellidos       AS cliente,
        c.tecnico_asignado_id                   AS tecnico_id,
        ut.nombres || ' ' || ut.apellidos       AS tecnico,
        e.codigo                                AS estado_codigo,
        e.nombre                                AS estado,
        e.es_final,
        p.codigo                                AS prioridad_codigo,
        p.nombre                                AS prioridad,
        c.fecha_registro,
        c.fecha_limite_resolucion               AS fecha_limite
FROM caso c
JOIN tipo_solicitud t ON t.id = c.tipo_solicitud_id
JOIN estado_caso e    ON e.id = c.estado_id
JOIN prioridad p      ON p.id = c.prioridad_id
JOIN usuario uc       ON uc.id = c.cliente_id
LEFT JOIN usuario ut  ON ut.id = c.tecnico_asignado_id;

COMMENT ON VIEW vw_casos_general IS
  'Dashboard del supervisor: todas las solicitudes con cliente, técnico, estado, prioridad y fecha límite de resolución.';

-- =====================================================================
-- 3. vw_carga_tecnicos — situación de cada técnico
--
-- "Casos abiertos" usa la misma definición que fn_calcular_score y
-- fn_asignar_solicitud (tecnico_asignado_id = X AND NOT es_final), para
-- que la carga que ve el supervisor sea exactamente la que el algoritmo
-- tiene en cuenta al decidir si el técnico tiene cupo.
-- Solo técnicos con usuario activo: un usuario dado de baja no participa
-- en la distribución (fn_asignar_solicitud filtra u.activo).
-- =====================================================================
CREATE OR REPLACE VIEW vw_carga_tecnicos AS
SELECT  t.usuario_id                            AS tecnico_id,
        u.nombres || ' ' || u.apellidos         AS nombre,
        u.correo,
        COALESCE((SELECT string_agg(es.nombre, ', ' ORDER BY es.nombre)
                  FROM tecnico_especialidad te
                  JOIN especialidad es ON es.id = te.especialidad_id
                  WHERE te.tecnico_id = t.usuario_id), '') AS especialidades,
        (SELECT COUNT(*)
         FROM caso c
         JOIN estado_caso e ON e.id = c.estado_id
         WHERE c.tecnico_asignado_id = t.usuario_id
           AND NOT e.es_final)::INTEGER         AS casos_abiertos,
        t.capacidad_maxima,
        t.disponible,
        t.motivo_no_disponible
FROM tecnico t
JOIN usuario u ON u.id = t.usuario_id
WHERE u.activo;

COMMENT ON VIEW vw_carga_tecnicos IS
  'Administración de técnicos / Sin asignar: carga actual frente a capacidad, disponibilidad y motivo de cada técnico activo.';

-- =====================================================================
-- 4. fn_cambiar_disponibilidad — el supervisor marca a un técnico como
--    disponible o no disponible
--
-- NO libera casos por su cuenta: el UPDATE sobre tecnico.disponible
-- dispara tg_tecnico_after_update_disponible (06), que llama a
-- fn_liberar_casos_tecnico. Cada caso liberado dispara a su vez
-- tg_caso_after_update_liberar (07/09), que intenta reasignar desde la
-- bolsa a quien tenga cupo.
--
-- Al volver a marcarlo disponible sí se llama a fn_procesar_bolsa: no hay
-- ningún trigger que reparta la bolsa en ese momento y, sin esto, el
-- técnico no recibiría casos hasta la siguiente corrida del scheduler
-- (hasta 5 minutos). FA02: "el sistema le asigna solicitudes conforme a
-- su capacidad disponible".
--
-- Devuelve cuántos casos se devolvieron a la bolsa y cuántos se
-- asignaron desde la bolsa como resultado del cambio.
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_cambiar_disponibilidad(
    p_tecnico_id    INTEGER,
    p_disponible    BOOLEAN,
    p_motivo        VARCHAR,
    p_usuario_id    INTEGER,
    p_ip            VARCHAR DEFAULT NULL
) RETURNS TABLE (
    casos_liberados INTEGER,
    casos_asignados INTEGER
) AS $$
DECLARE
    v_tecnico       RECORD;
    v_nombre        VARCHAR;
    v_motivo        VARCHAR(200);
    v_liberados     INTEGER := 0;
    v_asignados     INTEGER := 0;
BEGIN
    SET LOCAL lock_timeout = '5s';

    IF p_disponible IS NULL THEN
        RAISE EXCEPTION 'Debe indicar si el técnico queda disponible o no disponible';
    END IF;

    SELECT t.*, u.nombres || ' ' || u.apellidos AS nombre
      INTO v_tecnico
    FROM tecnico t
    JOIN usuario u ON u.id = t.usuario_id
    WHERE t.usuario_id = p_tecnico_id
    FOR UPDATE OF t;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El técnico indicado no existe';
    END IF;

    v_nombre := v_tecnico.nombre;
    v_motivo := NULLIF(BTRIM(p_motivo), '');

    -- RN02: la no disponibilidad exige motivo
    IF NOT p_disponible AND v_motivo IS NULL THEN
        RAISE EXCEPTION 'Debe indicar el motivo por el cual el técnico no estará disponible';
    END IF;

    IF v_tecnico.disponible = p_disponible THEN
        RAISE EXCEPTION 'El técnico ya se encuentra %',
            CASE WHEN p_disponible THEN 'disponible' ELSE 'no disponible' END;
    END IF;

    -- Mismo criterio que fn_liberar_casos_tecnico, solo para informar
    -- cuántos casos van a volver a la bolsa (RN03: no vuelven los
    -- RESUELTO ni los que están en un estado final).
    IF NOT p_disponible THEN
        SELECT COUNT(*) INTO v_liberados
        FROM caso c
        JOIN estado_caso e ON e.id = c.estado_id
        WHERE c.tecnico_asignado_id = p_tecnico_id
          AND NOT e.es_final
          AND e.codigo <> 'RESUELTO';
    END IF;

    -- Este UPDATE es el que dispara la liberación de casos (trigger de 06)
    UPDATE tecnico
       SET disponible           = p_disponible,
           motivo_no_disponible = CASE WHEN p_disponible THEN NULL ELSE v_motivo END
     WHERE usuario_id = p_tecnico_id;

    IF p_disponible THEN
        v_asignados := fn_procesar_bolsa();
    END IF;

    PERFORM fn_registrar_bitacora(
        p_usuario_id, p_ip, 'TECNICOS', 'CAMBIAR_DISPONIBILIDAD', 'tecnico', p_tecnico_id::VARCHAR,
        CASE WHEN p_disponible
             THEN 'Técnico ' || v_nombre || ' marcado como disponible'
             ELSE 'Técnico ' || v_nombre || ' marcado como no disponible: ' || v_motivo
        END,
        jsonb_build_object(
            'tecnico_id',      p_tecnico_id,
            'disponible',      p_disponible,
            'motivo',          v_motivo,
            -- al reactivarlo, deja constancia de qué motivo se levantó
            'motivo_anterior', v_tecnico.motivo_no_disponible,
            'casos_liberados', v_liberados,
            'casos_asignados', v_asignados)
    );

    RETURN QUERY SELECT v_liberados, v_asignados;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_cambiar_disponibilidad(INTEGER, BOOLEAN, VARCHAR, INTEGER, VARCHAR) IS
  'Administración de técnicos: cambia la disponibilidad y el motivo, registra en bitácora. La liberación de casos la hace el trigger existente sobre tecnico.disponible; al volver a disponible reparte la bolsa.';

-- =====================================================================
-- Comprobaciones sugeridas
-- =====================================================================
-- SELECT * FROM vw_carga_tecnicos;
-- SELECT * FROM vw_casos_general ORDER BY fecha_limite ASC NULLS LAST;
--
-- SELECT * FROM fn_cambiar_disponibilidad(
--     (SELECT id FROM usuario WHERE correo = 'jperez@soltec.com.gt'), FALSE,
--     'Vacaciones', (SELECT id FROM usuario WHERE correo = 'supervisor@soltec.com.gt'), '127.0.0.1');
