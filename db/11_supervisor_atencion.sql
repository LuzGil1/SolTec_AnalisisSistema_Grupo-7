-- =====================================================================
-- 11_supervisor_atencion.sql
-- Módulo del Supervisor de Soporte — segunda etapa
--   Atención de denuncias
--   Revisión de escalamientos
--   Revisión de sugerencias
--
-- Correr DESPUÉS de 01-10, sobre una base ya instalada. No modifica ni
-- reemplaza ningún script anterior. Se puede volver a correr: todo es
-- IF NOT EXISTS / CREATE OR REPLACE.
--
-- Las transiciones de estado (denuncias, escalamientos atendidos por el
-- supervisor, sugerencias) las valida y ejecuta el backend en Java, igual
-- que la atención del técnico (CU-06). Aquí solo va lo que necesita vivir
-- en la base: la columna del responsable, las vistas de los listados y la
-- devolución a la bolsa, que tiene que excluir al técnico y reasignar en
-- la misma transacción que usa fn_asignar_solicitud.
-- =====================================================================

SET search_path TO soltec, public;

-- =====================================================================
-- 1. Supervisor responsable de un caso escalado
--
-- CU Revisión de Escalamientos, FA02/RN05: cuando el supervisor atiende
-- directamente una solicitud escalada queda registrado como responsable.
-- No se puede usar tecnico_asignado_id: referencia a tecnico(usuario_id)
-- y el supervisor no es técnico. Además, dejar tecnico_asignado_id en
-- NULL es justamente lo que hace que el caso no cuente en la capacidad de
-- ningún técnico (fn_calcular_score y fn_asignar_solicitud cuentan la
-- carga como tecnico_asignado_id = X AND NOT es_final).
-- =====================================================================
ALTER TABLE caso
    ADD COLUMN IF NOT EXISTS supervisor_responsable_id INTEGER REFERENCES usuario(id);

COMMENT ON COLUMN caso.supervisor_responsable_id IS
  'Supervisor que tomó la atención directa de una solicitud escalada. No computa en la capacidad de ningún técnico.';

-- =====================================================================
-- 2. vw_denuncias — listado de denuncias del supervisor
--
-- Todas las denuncias con el personal involucrado ya agregado en texto.
-- El backend filtra las no concluidas (NOT es_final).
-- =====================================================================
CREATE OR REPLACE VIEW vw_denuncias AS
SELECT  c.id                                    AS caso_id,
        c.numero_boleta,
        c.asunto,
        uc.nombres || ' ' || uc.apellidos       AS cliente,
        COALESCE((SELECT string_agg(ui.nombres || ' ' || ui.apellidos, ', ' ORDER BY ci.id)
                  FROM caso_involucrado ci
                  JOIN usuario ui ON ui.id = ci.usuario_id
                  WHERE ci.caso_id = c.id), '') AS involucrados,
        e.codigo                                AS estado_codigo,
        e.nombre                                AS estado,
        e.es_final,
        c.fecha_registro,
        c.fecha_limite_resolucion               AS fecha_limite
FROM caso c
JOIN tipo_solicitud t ON t.id = c.tipo_solicitud_id
JOIN estado_caso e    ON e.id = c.estado_id
JOIN usuario uc       ON uc.id = c.cliente_id
WHERE t.codigo = 'DENUNCIA';

COMMENT ON VIEW vw_denuncias IS
  'CU Atención de Denuncias: denuncias con cliente, personal involucrado, estado y fecha límite.';

-- =====================================================================
-- 3. vw_escalamientos — solicitudes escaladas y las que el supervisor
--    está atendiendo
--
-- Al escalar, el técnico deja de ser tecnico_asignado_id (CU-06), así que
-- quién escaló y por qué se obtiene del último seguimiento que llevó el
-- caso a ESCALADO (RN02: el avance con el que el técnico justificó el
-- escalamiento).
--
-- Además de los ESCALADO, incluye los que el supervisor tomó y todavía no
-- concluyó: desde aquí continúa su atención (FA02).
-- =====================================================================
CREATE OR REPLACE VIEW vw_escalamientos AS
SELECT  c.id                                    AS caso_id,
        c.numero_boleta,
        t.nombre                                AS tipo,
        c.asunto,
        uc.nombres || ' ' || uc.apellidos       AS cliente,
        esc.usuario_id                          AS escalada_por_id,
        esc.nombre                              AS escalada_por,
        esc.comentario                          AS motivo_escalamiento,
        esc.fecha                               AS fecha_escalamiento,
        e.codigo                                AS estado_codigo,
        e.nombre                                AS estado,
        c.supervisor_responsable_id,
        us.nombres || ' ' || us.apellidos       AS supervisor_responsable,
        c.fecha_registro,
        c.fecha_limite_resolucion               AS fecha_limite
FROM caso c
JOIN tipo_solicitud t ON t.id = c.tipo_solicitud_id
JOIN estado_caso e    ON e.id = c.estado_id
JOIN usuario uc       ON uc.id = c.cliente_id
LEFT JOIN usuario us  ON us.id = c.supervisor_responsable_id
LEFT JOIN LATERAL (
        SELECT s.usuario_id, u.nombres || ' ' || u.apellidos AS nombre, s.comentario, s.fecha
        FROM seguimiento s
        JOIN usuario u      ON u.id = s.usuario_id
        JOIN estado_caso en ON en.id = s.estado_nuevo_id
        WHERE s.caso_id = c.id AND en.codigo = 'ESCALADO'
        ORDER BY s.fecha DESC, s.id DESC
        LIMIT 1
) esc ON TRUE
WHERE e.codigo = 'ESCALADO'
   OR (c.supervisor_responsable_id IS NOT NULL AND NOT e.es_final);

COMMENT ON VIEW vw_escalamientos IS
  'CU Revisión de Escalamientos: casos ESCALADO (con quién y por qué los escaló) y los que el supervisor atiende directamente sin concluir.';

-- =====================================================================
-- 4. vw_sugerencias — listado de sugerencias
-- =====================================================================
CREATE OR REPLACE VIEW vw_sugerencias AS
SELECT  c.id                                    AS caso_id,
        c.numero_boleta,
        c.asunto,
        uc.nombres || ' ' || uc.apellidos       AS cliente,
        e.codigo                                AS estado_codigo,
        e.nombre                                AS estado,
        e.es_final,
        c.fecha_registro
FROM caso c
JOIN tipo_solicitud t ON t.id = c.tipo_solicitud_id
JOIN estado_caso e    ON e.id = c.estado_id
JOIN usuario uc       ON uc.id = c.cliente_id
WHERE t.codigo = 'SUGERENCIA';

COMMENT ON VIEW vw_sugerencias IS
  'CU Revisión de Sugerencias: sugerencias con cliente, estado y fecha de registro (sin fecha límite, RN02).';

-- =====================================================================
-- 5. fn_devolver_escalamiento — el supervisor devuelve a la bolsa una
--    solicitud escalada (CU Revisión de Escalamientos, FA01 / RN04)
--
--   - La solicitud vuelve a EN_COLA sin técnico, conservando
--     fecha_registro (antigüedad) y todo el historial de seguimiento.
--   - El técnico que la escaló queda registrado en caso_involucrado con
--     el motivo "Escaló la solicitud". Con eso fn_asignar_solicitud ya lo
--     excluye (filtro de conflicto de interés existente): no se agrega
--     ningún filtro nuevo al algoritmo.
--   - Se limpian fecha_cierre (el CU-06 la llena al escalar) y
--     fecha_asignacion, que ya no aplican a un caso en la bolsa.
--   - Después se intenta la reasignación inmediata con
--     fn_asignar_solicitud. Si nadie tiene cupo, el caso se queda en la
--     bolsa y lo reparte el barrido periódico (fn_procesar_bolsa).
--
-- Devuelve el id del técnico al que se reasignó, o NULL si quedó en la
-- bolsa.
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_devolver_escalamiento(
    p_caso_id       INTEGER,
    p_supervisor_id INTEGER,
    p_comentario    TEXT,
    p_ip            VARCHAR DEFAULT NULL
) RETURNS INTEGER AS $$
DECLARE
    v_estado_actual INTEGER;
    v_codigo        VARCHAR(25);
    v_id_en_cola    INTEGER;
    v_escalador     INTEGER;
    v_comentario    TEXT;
    v_tecnico_nuevo INTEGER;
BEGIN
    SET LOCAL lock_timeout = '5s';

    v_comentario := NULLIF(BTRIM(p_comentario), '');
    IF v_comentario IS NULL THEN
        RAISE EXCEPTION 'Escriba el avance antes de continuar.';
    END IF;

    SELECT c.estado_id, e.codigo
      INTO v_estado_actual, v_codigo
    FROM caso c
    JOIN estado_caso e ON e.id = c.estado_id
    WHERE c.id = p_caso_id
    FOR UPDATE OF c;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'La solicitud indicada no existe';
    END IF;

    IF v_codigo <> 'ESCALADO' THEN
        RAISE EXCEPTION 'La solicitud no se encuentra escalada: solo se puede devolver a la bolsa una solicitud escalada';
    END IF;

    SELECT id INTO v_id_en_cola FROM estado_caso WHERE codigo = 'EN_COLA';

    -- Técnico que escaló: autor del último seguimiento que llevó el caso a
    -- ESCALADO (al escalar, el CU-06 ya limpió tecnico_asignado_id)
    SELECT s.usuario_id INTO v_escalador
    FROM seguimiento s
    JOIN estado_caso en ON en.id = s.estado_nuevo_id
    WHERE s.caso_id = p_caso_id AND en.codigo = 'ESCALADO'
    ORDER BY s.fecha DESC, s.id DESC
    LIMIT 1;

    -- RN04: se excluye antes de volver a la bolsa, para que ninguna
    -- asignación (ni la inmediata de abajo ni el barrido periódico) se lo
    -- pueda entregar
    IF v_escalador IS NOT NULL THEN
        INSERT INTO caso_involucrado (caso_id, usuario_id, motivo)
        VALUES (p_caso_id, v_escalador, 'Escaló la solicitud')
        ON CONFLICT (caso_id, usuario_id) DO NOTHING;
    END IF;

    UPDATE caso
       SET estado_id                 = v_id_en_cola,
           tecnico_asignado_id       = NULL,
           supervisor_responsable_id = NULL,
           fecha_asignacion          = NULL,
           fecha_cierre              = NULL
     WHERE id = p_caso_id;

    INSERT INTO seguimiento (caso_id, usuario_id, estado_anterior_id, estado_nuevo_id,
                             comentario, visible_cliente)
    VALUES (p_caso_id, p_supervisor_id, v_estado_actual, v_id_en_cola, v_comentario, FALSE);

    PERFORM fn_registrar_bitacora(
        p_supervisor_id, p_ip, 'ESCALAMIENTOS', 'DEVOLVER_BOLSA', 'caso', p_caso_id::VARCHAR,
        'Solicitud ' || p_caso_id || ' devuelta a la bolsa de demanda',
        jsonb_build_object('tecnico_excluido', v_escalador)
    );

    v_tecnico_nuevo := fn_asignar_solicitud(p_caso_id, p_ip);

    RETURN v_tecnico_nuevo;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_devolver_escalamiento(INTEGER, INTEGER, TEXT, VARCHAR) IS
  'CU Revisión de Escalamientos FA01: devuelve a EN_COLA una solicitud escalada, excluye al técnico que la escaló (caso_involucrado) y la intenta reasignar.';
