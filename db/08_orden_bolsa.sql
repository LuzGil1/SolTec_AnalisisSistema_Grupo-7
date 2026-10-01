-- =====================================================================
-- 08_orden_bolsa.sql
-- CU-04 v3 — Orden de recorrido de la bolsa por puntuacion del CASO
--
-- Hallazgo de la auditoria CU4 (docs/auditoria_CU4.txt, seccion 3.1):
-- fn_procesar_bolsa recorria la bolsa por fecha_registro ASC. Prioridad
-- y cercania al vencimiento del SLA no influian en nada: no desempatan
-- entre tecnicos (son iguales para todos los candidatos de un mismo
-- caso, ver fn_calcular_score) y tampoco ordenaban la cola (se barria
-- por antiguedad). Resultado: un reclamo Alta recien registrado podia
-- quedar detras de una queja Media de ayer.
--
-- Este script cambia UNICAMENTE el orden en que fn_procesar_bolsa
-- recorre los casos pendientes. No cambia a quien se le asigna cada
-- caso una vez que le toca su turno: eso lo sigue decidiendo
-- fn_calcular_score/fn_asignar_solicitud sin ningun cambio.
--
-- Correr DESPUES de 01-07, sobre una base ya instalada. No modifica ni
-- reemplaza ningun script anterior.
-- =====================================================================

SET search_path TO soltec, public;

-- =====================================================================
-- 1. fn_score_solicitud — puntuacion de la SOLICITUD (no del par
--    solicitud-tecnico), para ordenar la bolsa.
--
-- Usa exactamente los mismos tres componentes de fn_calcular_score que
-- dependen solo del caso (prioridad, aging por espera, urgencia frente
-- al SLA), con los mismos pesos de la tabla parametro. No incluye
-- especialidad ni carga: esos dependen del tecnico y no tiene sentido
-- usarlos para ordenar una cola que todavia no sabe a quien le va a
-- tocar cada caso.
--
-- fn_calcular_score y fn_asignar_solicitud NO se tocan (instruccion
-- explicita de la auditoria): sus funciones no fueron reemplazadas por
-- este script, siguen siendo las mismas de 03 y 06. Por eso el calculo
-- de prioridad/aging/urgencia esta repetido aqui en vez de extraido a
-- una funcion comun: no se podia factorizar sin tocar fn_calcular_score.
-- Se mantuvo la formula identica, componente por componente, con las
-- mismas claves de parametro, para que no se desalinee con el score
-- que usa fn_asignar_solicitud al elegir tecnico.
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_score_solicitud(
    p_caso_id       INTEGER
) RETURNS NUMERIC AS $$
DECLARE
    v_caso          RECORD;
    v_horas_espera  NUMERIC;
    v_aging         NUMERIC;
    v_urgencia      NUMERIC;
    w_prio          NUMERIC;
    w_espera        NUMERIC;
    w_sla           NUMERIC;
    f_aging         NUMERIC;
BEGIN
    SELECT c.*, p.peso AS peso_prioridad
    INTO v_caso
    FROM caso c
    JOIN prioridad p ON p.id = c.prioridad_id
    WHERE c.id = p_caso_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT valor INTO w_prio   FROM parametro WHERE clave = 'PESO_PRIORIDAD';
    SELECT valor INTO w_espera FROM parametro WHERE clave = 'PESO_ESPERA';
    SELECT valor INTO w_sla    FROM parametro WHERE clave = 'PESO_SLA';
    SELECT valor INTO f_aging  FROM parametro WHERE clave = 'FACTOR_AGING';

    -- 1. Prioridad (0-100) — identico a fn_calcular_score
    -- 2. Antiguedad en cola (aging) — identico a fn_calcular_score
    v_horas_espera := EXTRACT(EPOCH FROM (NOW() - v_caso.fecha_registro)) / 3600;
    v_aging := LEAST(100, v_horas_espera * f_aging);

    -- 3. Urgencia frente al SLA — identico a fn_calcular_score
    v_urgencia := CASE
        WHEN v_caso.fecha_limite_resolucion IS NULL THEN 0
        WHEN NOW() >= v_caso.fecha_limite_resolucion THEN 100
        ELSE 100 * (1 - (EXTRACT(EPOCH FROM (v_caso.fecha_limite_resolucion - NOW()))
                       / NULLIF(EXTRACT(EPOCH FROM (v_caso.fecha_limite_resolucion - v_caso.fecha_registro)), 0)))
    END;

    RETURN ROUND(
          (w_prio   * v_caso.peso_prioridad)
        + (w_espera * v_aging)
        + (w_sla    * v_urgencia)
    , 3);
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON FUNCTION fn_score_solicitud(INTEGER) IS
  'CU-04 v3: puntuacion de una solicitud (prioridad + aging + urgencia SLA, sin especialidad ni carga) usada para ordenar la bolsa de demanda. No decide tecnico: eso lo hace fn_calcular_score dentro de fn_asignar_solicitud.';

-- =====================================================================
-- 2. fn_procesar_bolsa — ahora recorre por fn_score_solicitud DESC,
--    con fecha_registro ASC como desempate (el mas antiguo primero
--    entre solicitudes con la misma puntuacion).
--
-- El resto de la funcion no cambia: sigue llamando a
-- fn_asignar_solicitud por cada caso, que es quien decide el tecnico y
-- quien puede devolver NULL si nadie esta disponible (el caso se queda
-- en la bolsa sin lanzar excepcion, igual que antes).
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
        ORDER BY fn_score_solicitud(c.id) DESC, c.fecha_registro ASC
    LOOP
        IF fn_asignar_solicitud(v_caso.id) IS NOT NULL THEN
            v_asignados := v_asignados + 1;
        END IF;
    END LOOP;

    RETURN v_asignados;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_procesar_bolsa() IS
  'CU-04 v3: barre la bolsa de demanda por fn_score_solicitud DESC (desempate por antiguedad) e intenta asignar cada caso pendiente. Devuelve cuantos asigno.';

-- =====================================================================
-- 3. Alcance de este cambio — lo que NO se tocó
--
-- El trigger tg_caso_after_insert (06) sigue intentando asignar cada
-- caso apenas entra a la bolsa, de forma individual e inmediata: eso no
-- pasa por fn_procesar_bolsa y no compite en orden contra otros casos.
--
-- El trigger tg_caso_after_update_liberar (07, version vigente) sigue
-- escogiendo "el caso mas antiguo de la bolsa" (ORDER BY fecha_registro
-- ASC LIMIT 1) para ocupar un cupo que se acaba de liberar. Ese trigger
-- vive en 07_atencion_casos.sql y no se modifico aqui porque el pedido
-- de esta auditoria fue puntualmente sobre fn_procesar_bolsa. Efecto
-- practico: el barrido periodico (cada 5 minutos, o al ejecutar
-- fn_procesar_bolsa manualmente) SI respeta prioridad/SLA; la
-- asignacion instantanea al liberarse un cupo TODAVIA elige por
-- antiguedad. Si se quiere consistencia total, ese trigger necesitaria
-- el mismo ORDER BY fn_score_solicitud(c.id) DESC, c.fecha_registro ASC
-- — pendiente de decision, no incluido en este script.
-- =====================================================================
