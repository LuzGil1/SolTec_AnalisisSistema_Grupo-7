-- =====================================================================
-- 07_atencion_casos.sql
-- CU-05 / CU-06 — Dashboard del técnico y atención de casos
--
-- La atención del caso (transición de estado, comentario, solución) la
-- valida y ejecuta el backend en Java (tabla de transiciones, permisos).
-- Esta base solo necesita un ajuste al trigger de liberación de capacidad
-- que trajo 06_asignacion_automatica.sql.
--
-- Por qué: tg_caso_after_update_liberar solo reacciona cuando el estado
-- nuevo es es_final (CERRADO, IMPROCEDENTE, DUPLICADO). Pero un caso
-- ESCALADO también deja de contar en la carga del técnico (así lo pide
-- CU-06): el backend, al escalar, además de cambiar el estado limpia
-- caso.tecnico_asignado_id (dejar a alguien "dueño" de un caso escalado
-- no tiene sentido: pasa a la bandeja del supervisor). fn_calcular_score
-- y fn_asignar_solicitud ya cuentan la carga como
-- "tecnico_asignado_id = X AND NOT es_final", así que ese UPDATE ya libera
-- la capacidad solo; lo único que falta es que el trigger también dispare
-- la asignación del siguiente caso de la bolsa en ese caso, no solo cuando
-- el estado nuevo es final.
--
-- Se REEMPLAZA la función y se recrea el trigger (el WHEN no se puede
-- alterar con CREATE OR REPLACE). No se toca fn_calcular_score ni
-- fn_asignar_solicitud: siguen siendo la misma fórmula de 03 y 06.
--
-- Correr DESPUÉS de 01-06, sobre una base ya instalada.
-- =====================================================================

SET search_path TO soltec, public;

CREATE OR REPLACE FUNCTION fn_caso_after_update_liberar()
RETURNS TRIGGER AS $$
DECLARE
    v_es_final          BOOLEAN;
    v_capacidad_liberada BOOLEAN;
    v_caso_id           INTEGER;
BEGIN
    SELECT es_final INTO v_es_final FROM estado_caso WHERE id = NEW.estado_id;

    -- Se liberó capacidad si el caso llegó a un estado final (CERRADO,
    -- IMPROCEDENTE, DUPLICADO) o si perdió técnico asignado (p. ej. lo
    -- escaló) sin importar si su estado es final o no.
    v_capacidad_liberada := v_es_final
        OR (OLD.tecnico_asignado_id IS NOT NULL AND NEW.tecnico_asignado_id IS NULL);

    IF v_capacidad_liberada THEN
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

DROP TRIGGER IF EXISTS tg_caso_after_update_liberar ON caso;

CREATE TRIGGER tg_caso_after_update_liberar
    AFTER UPDATE ON caso
    FOR EACH ROW
    WHEN (NEW.estado_id IS DISTINCT FROM OLD.estado_id
          OR NEW.tecnico_asignado_id IS DISTINCT FROM OLD.tecnico_asignado_id)
    EXECUTE FUNCTION fn_caso_after_update_liberar();

COMMENT ON FUNCTION fn_caso_after_update_liberar() IS
  'CU-04/05/06: si un UPDATE de caso libera capacidad (estado final o técnico desasignado), intenta asignar el caso más antiguo de la bolsa.';
