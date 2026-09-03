-- =====================================================================
-- 06_asignacion_automatica.sql
-- CU-04 v2 — Asignación automática de solicitudes
--
-- Invierte fn_solicitar_siguiente_caso: ya no es el técnico quien pide
-- un caso, es el sistema el que reparte los casos apenas entran a la
-- bolsa (o apenas se libera capacidad). fn_solicitar_siguiente_caso se
-- deja en la base marcada como obsoleta (paso 8), no se borra.
--
-- Correr DESPUÉS de 01-05, sobre una base ya instalada. No modifica ni
-- reemplaza ningún script anterior.
-- =====================================================================

SET search_path TO soltec, public;

-- =====================================================================
-- 1. Parámetro nuevo — umbral de alerta para el supervisor
-- =====================================================================
INSERT INTO parametro (clave, valor, descripcion) VALUES
 ('HORAS_ALERTA_SIN_ASIGNAR', 4.0000,
  'Horas que una solicitud puede permanecer sin asignar antes de reportarse al supervisor')
ON CONFLICT (clave) DO NOTHING;

-- =====================================================================
-- 2. fn_asignar_solicitud — dado un caso, elige al mejor técnico
--
-- Reutiliza fn_calcular_score tal cual está (mismos pesos de parametro,
-- misma fórmula: prioridad, espera, urgencia SLA, especialidad, carga).
-- La especialidad ya es preferencia y no filtro dentro de esa fórmula
-- (solo suma puntos si coincide), así que no hace falta duplicar esa
-- regla aquí: el filtro de este WHERE es únicamente activo/disponible/
-- capacidad/conflicto de interés.
--
-- FOR UPDATE sobre el propio caso evita que el trigger de INSERT y una
-- corrida de fn_procesar_bolsa lo procesen dos veces a la vez.
-- FOR UPDATE OF t ... SKIP LOCKED sobre tecnico evita que dos llamadas
-- concurrentes (dos casos entrando casi al mismo tiempo) le asignen el
-- mismo técnico dos veces por encima de su capacidad.
--
-- Devuelve el id del técnico asignado, o NULL si no había nadie
-- disponible (el caso se queda en la bolsa, sin lanzar excepción).
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_asignar_solicitud(
    p_caso_id   INTEGER,
    p_ip        VARCHAR DEFAULT NULL
) RETURNS INTEGER AS $$
DECLARE
    v_id_en_cola        INTEGER;
    v_id_asignado        INTEGER;
    v_estado_actual      INTEGER;
    v_tecnico_actual     INTEGER;
    v_tecnico_id         INTEGER;
    v_score              NUMERIC;
BEGIN
    -- Evita quedar colgado indefinidamente si otra transacción tiene
    -- tomado el candado de la bitácora o de algún técnico por mucho
    -- tiempo: falla rápido en vez de bloquear el hilo para siempre.
    SET LOCAL lock_timeout = '5s';

    SELECT id INTO v_id_en_cola  FROM estado_caso WHERE codigo = 'EN_COLA';
    SELECT id INTO v_id_asignado FROM estado_caso WHERE codigo = 'ASIGNADO';

    SELECT estado_id, tecnico_asignado_id
      INTO v_estado_actual, v_tecnico_actual
    FROM caso
    WHERE id = p_caso_id
    FOR UPDATE;

    -- El caso ya no existe, ya tiene técnico o ya no está en la bolsa
    -- (p. ej. lo procesó otra llamada concurrente)
    IF v_estado_actual IS DISTINCT FROM v_id_en_cola OR v_tecnico_actual IS NOT NULL THEN
        RETURN NULL;
    END IF;

    -- Mejor técnico disponible según fn_calcular_score. La especialidad
    -- es preferencia (ya está dentro de la fórmula del score): si nadie
    -- tiene la del caso, igual gana el mejor score entre los que sí
    -- cumplen el filtro duro de activo/disponible/capacidad/conflicto.
    SELECT t.usuario_id INTO v_tecnico_id
    FROM tecnico t
    JOIN usuario u ON u.id = t.usuario_id
    WHERE t.disponible
      AND u.activo
      -- RN: conflicto de interés
      AND NOT EXISTS (
            SELECT 1 FROM caso_involucrado ci
            WHERE ci.caso_id = p_caso_id AND ci.usuario_id = t.usuario_id)
      -- Por debajo de su capacidad máxima
      AND (
            SELECT COUNT(*) FROM caso c2
            JOIN estado_caso e2 ON e2.id = c2.estado_id
            WHERE c2.tecnico_asignado_id = t.usuario_id AND NOT e2.es_final
          ) < t.capacidad_maxima
    ORDER BY fn_calcular_score(p_caso_id, t.usuario_id) DESC, t.usuario_id ASC
    LIMIT 1
    FOR UPDATE OF t SKIP LOCKED;

    IF v_tecnico_id IS NULL THEN
        RETURN NULL; -- nadie disponible; el caso se queda en la bolsa
    END IF;

    v_score := fn_calcular_score(p_caso_id, v_tecnico_id);

    UPDATE caso
       SET estado_id           = v_id_asignado,
           tecnico_asignado_id = v_tecnico_id,
           fecha_asignacion    = NOW()
     WHERE id = p_caso_id;

    INSERT INTO asignacion (caso_id, tecnico_id, score, motivo)
    VALUES (p_caso_id, v_tecnico_id, v_score, 'Asignación automática del sistema');

    INSERT INTO seguimiento (caso_id, usuario_id, estado_anterior_id, estado_nuevo_id,
                             comentario, visible_cliente)
    VALUES (p_caso_id, v_tecnico_id, v_estado_actual, v_id_asignado,
            'Caso asignado por el sistema para su atención', TRUE);

    -- Se audita a nombre del propio técnico asignado: no hay usuario
    -- "sistema" en la base, y el destinatario de la asignación es el
    -- dato relevante para la bitácora.
    PERFORM fn_registrar_bitacora(
        v_tecnico_id, p_ip, 'CASOS', 'ASIGNAR', 'caso', p_caso_id::VARCHAR,
        'Asignación automática del caso ' || p_caso_id || ' al técnico ' || v_tecnico_id,
        jsonb_build_object('score', v_score, 'tecnico_id', v_tecnico_id, 'automatica', true)
    );

    RETURN v_tecnico_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_asignar_solicitud(INTEGER, VARCHAR) IS
  'CU-04 v2: dado un caso, asigna al mejor técnico disponible según fn_calcular_score. Devuelve NULL sin error si no hay técnico disponible.';

-- =====================================================================
-- 3. fn_procesar_bolsa — recorre EN_COLA sin técnico por antigüedad
--
-- Cada llamada a fn_asignar_solicitud dentro del loop corre en la misma
-- transacción: los UPDATE de un caso ya son visibles para el filtro de
-- capacidad del siguiente, así que la carga se reparte bien aun dentro
-- de una sola corrida.
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_procesar_bolsa()
RETURNS INTEGER AS $$
DECLARE
    v_caso      RECORD;
    v_asignados INTEGER := 0;
BEGIN
    FOR v_caso IN
        SELECT c.id
        FROM caso c
        JOIN estado_caso e    ON e.id = c.estado_id
        JOIN tipo_solicitud t ON t.id = c.tipo_solicitud_id
        WHERE e.codigo = 'EN_COLA'
          AND t.ingresa_bolsa
          AND c.tecnico_asignado_id IS NULL
        ORDER BY c.fecha_registro ASC
    LOOP
        IF fn_asignar_solicitud(v_caso.id) IS NOT NULL THEN
            v_asignados := v_asignados + 1;
        END IF;
    END LOOP;

    RETURN v_asignados;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_procesar_bolsa() IS
  'CU-04 v2: barre la bolsa de demanda por antigüedad e intenta asignar cada caso pendiente. Devuelve cuántos asignó.';

-- =====================================================================
-- 4. Trigger — al registrar un caso, intenta asignarlo de inmediato
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_caso_after_insert()
RETURNS TRIGGER AS $$
DECLARE
    v_id_en_cola INTEGER;
BEGIN
    SELECT id INTO v_id_en_cola FROM estado_caso WHERE codigo = 'EN_COLA';

    IF NEW.estado_id = v_id_en_cola THEN
        PERFORM fn_asignar_solicitud(NEW.id);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_caso_after_insert
    AFTER INSERT ON caso
    FOR EACH ROW EXECUTE FUNCTION fn_caso_after_insert();

-- =====================================================================
-- 5. Trigger — al cerrar un caso, se liberó capacidad: intenta asignar
--    el caso más antiguo de la bolsa
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_caso_after_update_liberar()
RETURNS TRIGGER AS $$
DECLARE
    v_es_final  BOOLEAN;
    v_caso_id   INTEGER;
BEGIN
    SELECT es_final INTO v_es_final FROM estado_caso WHERE id = NEW.estado_id;

    IF v_es_final THEN
        SELECT c.id INTO v_caso_id
        FROM caso c
        JOIN estado_caso e    ON e.id = c.estado_id
        JOIN tipo_solicitud t ON t.id = c.tipo_solicitud_id
        WHERE e.codigo = 'EN_COLA'
          AND t.ingresa_bolsa
          AND c.tecnico_asignado_id IS NULL
        ORDER BY c.fecha_registro ASC
        LIMIT 1;

        IF v_caso_id IS NOT NULL THEN
            PERFORM fn_asignar_solicitud(v_caso_id);
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_caso_after_update_liberar
    AFTER UPDATE ON caso
    FOR EACH ROW
    WHEN (NEW.estado_id IS DISTINCT FROM OLD.estado_id)
    EXECUTE FUNCTION fn_caso_after_update_liberar();

-- =====================================================================
-- 6. fn_liberar_casos_tecnico — el técnico se marca no disponible
--
-- Sus casos NO resueltos (ni RESUELTO ni un estado final) vuelven a
-- EN_COLA sin técnico, conservando fecha_registro (antigüedad) y el
-- historial de seguimiento ya escrito. Los casos ya RESUELTO o en un
-- estado final se quedan con él: ya no consumen su capacidad y no
-- tiene sentido reabrirlos.
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_liberar_casos_tecnico(
    p_tecnico_id  INTEGER,
    p_ip          VARCHAR DEFAULT NULL
) RETURNS INTEGER AS $$
DECLARE
    v_id_en_cola INTEGER;
    v_caso       RECORD;
    v_liberados  INTEGER := 0;
BEGIN
    SET LOCAL lock_timeout = '5s';

    SELECT id INTO v_id_en_cola FROM estado_caso WHERE codigo = 'EN_COLA';

    FOR v_caso IN
        SELECT c.id, c.estado_id
        FROM caso c
        JOIN estado_caso e ON e.id = c.estado_id
        WHERE c.tecnico_asignado_id = p_tecnico_id
          AND NOT e.es_final
          AND e.codigo <> 'RESUELTO'
        FOR UPDATE OF c
    LOOP
        UPDATE caso
           SET estado_id           = v_id_en_cola,
               tecnico_asignado_id = NULL,
               fecha_asignacion    = NULL
         WHERE id = v_caso.id;

        UPDATE asignacion
           SET fecha_liberacion = NOW()
         WHERE caso_id = v_caso.id
           AND tecnico_id = p_tecnico_id
           AND fecha_liberacion IS NULL;

        INSERT INTO seguimiento (caso_id, usuario_id, estado_anterior_id, estado_nuevo_id,
                                 comentario, visible_cliente)
        VALUES (v_caso.id, p_tecnico_id, v_caso.estado_id, v_id_en_cola,
                'Caso liberado: el técnico quedó no disponible, vuelve a la bolsa de demanda', TRUE);

        PERFORM fn_registrar_bitacora(
            p_tecnico_id, p_ip, 'CASOS', 'LIBERAR', 'caso', v_caso.id::VARCHAR,
            'Caso ' || v_caso.id || ' liberado por no disponibilidad del técnico ' || p_tecnico_id,
            jsonb_build_object('tecnico_id', p_tecnico_id)
        );

        v_liberados := v_liberados + 1;
    END LOOP;

    RETURN v_liberados;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_liberar_casos_tecnico(INTEGER, VARCHAR) IS
  'CU-04 v2: cuando un técnico queda no disponible, regresa sus casos abiertos (no resueltos) a la bolsa de demanda sin perder antigüedad ni historial.';

CREATE OR REPLACE FUNCTION fn_tecnico_after_update_disponible()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.disponible = FALSE AND OLD.disponible = TRUE THEN
        PERFORM fn_liberar_casos_tecnico(NEW.usuario_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_tecnico_after_update_disponible
    AFTER UPDATE ON tecnico
    FOR EACH ROW
    WHEN (NEW.disponible IS DISTINCT FROM OLD.disponible)
    EXECUTE FUNCTION fn_tecnico_after_update_disponible();

-- =====================================================================
-- 7. Vista — solicitudes sin asignar que ya superaron el umbral de
--    alerta (lo que verá el supervisor)
-- =====================================================================
CREATE OR REPLACE VIEW vw_solicitudes_sin_asignar AS
SELECT *
FROM vw_bolsa_demanda
WHERE horas_en_cola > (SELECT valor FROM parametro WHERE clave = 'HORAS_ALERTA_SIN_ASIGNAR');

COMMENT ON VIEW vw_solicitudes_sin_asignar IS
  'CU-04 v2: casos en EN_COLA sin técnico que llevan más de HORAS_ALERTA_SIN_ASIGNAR horas esperando. Bandeja de alerta del supervisor.';

-- =====================================================================
-- 8. fn_solicitar_siguiente_caso queda en la base pero obsoleta: el
--    técnico ya no pide caso, el sistema lo asigna solo. No se borra
--    todavía por si algo la sigue referenciando.
-- =====================================================================
COMMENT ON FUNCTION fn_solicitar_siguiente_caso(INTEGER, VARCHAR) IS
  'OBSOLETA (CU-04 v2): la asignación ahora es automática vía fn_asignar_solicitud (trigger de INSERT/UPDATE) y fn_procesar_bolsa (tarea programada). No usar en código nuevo.';
