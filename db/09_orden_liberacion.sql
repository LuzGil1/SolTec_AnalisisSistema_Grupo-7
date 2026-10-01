-- =====================================================================
-- 09_orden_liberacion.sql
-- CU-04 v3 — Orden por puntuacion tambien al liberarse un cupo
--
-- db/08_orden_bolsa.sql cambio fn_procesar_bolsa (el barrido periodico)
-- para recorrer la bolsa por fn_score_solicitud DESC en vez de por
-- fecha_registro ASC. Pero tg_caso_after_update_liberar (definido en
-- 07_atencion_casos.sql), que es el que decide que caso ocupa un cupo
-- que se ACABA de liberar (tecnico cierra, resuelve->cierra o escala un
-- caso), seguia haciendo ORDER BY c.fecha_registro ASC LIMIT 1. Como en
-- operacion normal los cupos se liberan por cierre de casos -no por el
-- barrido periodico-, este trigger es el camino mas frecuente por el
-- que entra un caso nuevo a un tecnico, y hasta este script seguia
-- ignorando la prioridad y el vencimiento del SLA.
--
-- Este script reemplaza UNICAMENTE la funcion fn_caso_after_update_liberar
-- para que use el mismo criterio que fn_procesar_bolsa:
--     ORDER BY fn_score_solicitud(c.id) DESC, c.fecha_registro ASC
-- El trigger tg_caso_after_update_liberar no se toca (mismo nombre,
-- misma condicion WHEN definida en 07): al hacer CREATE OR REPLACE
-- sobre la funcion, el trigger existente pasa a ejecutar la version
-- nueva automaticamente.
--
-- fn_calcular_score, fn_asignar_solicitud y fn_procesar_bolsa NO se
-- tocan. fn_score_solicitud tampoco se toca: se reutiliza tal cual
-- quedo definida en 08_orden_bolsa.sql.
--
-- Correr DESPUES de 01-08, sobre una base ya instalada. No modifica ni
-- reemplaza ningun script anterior (07_atencion_casos.sql queda intacto;
-- esto solo actualiza el CREATE OR REPLACE que ya existia sobre la
-- misma funcion).
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
        -- CU-04 v3: antes elegia el caso mas antiguo (ORDER BY
        -- c.fecha_registro ASC). Ahora usa el mismo criterio que
        -- fn_procesar_bolsa: mayor puntuacion primero, antiguedad solo
        -- como desempate.
        SELECT c.id INTO v_caso_id
        FROM caso c
        JOIN estado_caso e    ON e.id = c.estado_id
        JOIN tipo_solicitud t ON t.id = c.tipo_solicitud_id
        WHERE e.codigo = 'EN_COLA'
          AND t.ingresa_bolsa
          AND c.tecnico_asignado_id IS NULL
        ORDER BY fn_score_solicitud(c.id) DESC, c.fecha_registro ASC
        LIMIT 1;

        IF v_caso_id IS NOT NULL THEN
            PERFORM fn_asignar_solicitud(v_caso_id);
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_caso_after_update_liberar() IS
  'CU-04 v3: si un UPDATE de caso libera capacidad (estado final o técnico desasignado), asigna el caso de mayor fn_score_solicitud de la bolsa (desempate por antigüedad), no el más antiguo.';
