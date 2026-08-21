-- =====================================================================
-- 03_reglas_y_algoritmo.sql
-- Reglas de negocio (ruteo, reclamo referenciado, boleta)
-- y el algoritmo de asignación por demanda (CU-04)
-- =====================================================================

SET search_path TO soltec, public;

-- =====================================================================
-- RN — Numeración de boleta, ruteo por tipo y cálculo de SLA
-- Se dispara al registrar el caso (CU-01)
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_caso_before_insert()
RETURNS TRIGGER AS $$
DECLARE
    v_tipo      RECORD;
    v_sla       RECORD;
BEGIN
    SELECT * INTO v_tipo FROM tipo_solicitud WHERE id = NEW.tipo_solicitud_id;

    IF NOT FOUND OR NOT v_tipo.activo THEN
        RAISE EXCEPTION 'El tipo de solicitud no existe o está inactivo';
    END IF;

    -- RN: Reclamo referenciado — todo reclamo apunta a un caso previo
    IF v_tipo.requiere_caso_previo AND NEW.caso_relacionado_id IS NULL THEN
        RAISE EXCEPTION 'Un % debe referenciar el caso previo relacionado', v_tipo.nombre;
    END IF;

    -- Número de boleta: SOL-AAAA-000001
    IF NEW.numero_boleta IS NULL THEN
        NEW.numero_boleta := 'SOL-' || TO_CHAR(NOW(), 'YYYY') || '-' ||
                             LPAD(NEXTVAL('seq_boleta')::TEXT, 6, '0');
    END IF;

    -- Prioridad por defecto del tipo
    IF NEW.prioridad_id IS NULL THEN
        NEW.prioridad_id := v_tipo.prioridad_default_id;
    END IF;

    -- RN: Ruteo por tipo de solicitud
    --   queja / reclamo -> EN_COLA (bolsa de demanda)
    --   denuncia        -> EN_REVISION (bandeja del supervisor)
    --   sugerencia      -> EN_BACKLOG (sin SLA)
    NEW.estado_id := v_tipo.estado_inicial_id;

    -- SLA solo para los tipos que lo requieren
    IF v_tipo.requiere_sla THEN
        SELECT * INTO v_sla
        FROM sla
        WHERE tipo_solicitud_id = NEW.tipo_solicitud_id
          AND prioridad_id      = NEW.prioridad_id;

        IF FOUND THEN
            NEW.fecha_limite_respuesta  := NOW() + (v_sla.horas_respuesta  || ' hours')::INTERVAL;
            NEW.fecha_limite_resolucion := NOW() + (v_sla.horas_resolucion || ' hours')::INTERVAL;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_caso_before_insert
    BEFORE INSERT ON caso
    FOR EACH ROW EXECUTE FUNCTION fn_caso_before_insert();

-- =====================================================================
-- CU-04 — Cálculo del score
--
--   score = w_prioridad     * peso de la prioridad
--         + w_espera        * antigüedad en cola (aging)
--         + w_sla           * urgencia frente al vencimiento
--         + w_especialidad  * coincidencia con el técnico
--         - w_carga         * carga actual del técnico
--
-- Todos los componentes se normalizan de 0 a 100.
-- Los pesos viven en la tabla parametro (configurables desde el CU-09).
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_calcular_score(
    p_caso_id       INTEGER,
    p_tecnico_id    INTEGER
) RETURNS NUMERIC AS $$
DECLARE
    v_caso          RECORD;
    v_peso_prio     NUMERIC;
    v_horas_espera  NUMERIC;
    v_aging         NUMERIC;
    v_urgencia      NUMERIC;
    v_especialidad  NUMERIC := 0;
    v_carga         NUMERIC;
    v_capacidad     SMALLINT;
    v_abiertos      INTEGER;
    w_prio          NUMERIC;
    w_espera        NUMERIC;
    w_sla           NUMERIC;
    w_esp           NUMERIC;
    w_carga         NUMERIC;
    f_aging         NUMERIC;
BEGIN
    SELECT c.*, p.peso AS peso_prioridad, s.especialidad_id
    INTO v_caso
    FROM caso c
    JOIN prioridad p  ON p.id = c.prioridad_id
    LEFT JOIN servicio s ON s.id = c.servicio_id
    WHERE c.id = p_caso_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT valor INTO w_prio   FROM parametro WHERE clave = 'PESO_PRIORIDAD';
    SELECT valor INTO w_espera FROM parametro WHERE clave = 'PESO_ESPERA';
    SELECT valor INTO w_sla    FROM parametro WHERE clave = 'PESO_SLA';
    SELECT valor INTO w_esp    FROM parametro WHERE clave = 'PESO_ESPECIALIDAD';
    SELECT valor INTO w_carga  FROM parametro WHERE clave = 'PESO_CARGA';
    SELECT valor INTO f_aging  FROM parametro WHERE clave = 'FACTOR_AGING';

    -- 1. Prioridad (0-100)
    v_peso_prio := v_caso.peso_prioridad;

    -- 2. Antigüedad en cola: evita que un caso de prioridad baja quede olvidado
    v_horas_espera := EXTRACT(EPOCH FROM (NOW() - v_caso.fecha_registro)) / 3600;
    v_aging := LEAST(100, v_horas_espera * f_aging);

    -- 3. Urgencia frente al SLA
    v_urgencia := CASE
        WHEN v_caso.fecha_limite_resolucion IS NULL THEN 0
        WHEN NOW() >= v_caso.fecha_limite_resolucion THEN 100
        ELSE 100 * (1 - (EXTRACT(EPOCH FROM (v_caso.fecha_limite_resolucion - NOW()))
                       / NULLIF(EXTRACT(EPOCH FROM (v_caso.fecha_limite_resolucion - v_caso.fecha_registro)), 0)))
    END;

    -- 4. Coincidencia de especialidad
    IF v_caso.especialidad_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM tecnico_especialidad
        WHERE tecnico_id = p_tecnico_id AND especialidad_id = v_caso.especialidad_id
    ) THEN
        v_especialidad := 100;
    END IF;

    -- 5. Carga actual del técnico
    SELECT capacidad_maxima INTO v_capacidad FROM tecnico WHERE usuario_id = p_tecnico_id;

    SELECT COUNT(*) INTO v_abiertos
    FROM caso c
    JOIN estado_caso e ON e.id = c.estado_id
    WHERE c.tecnico_asignado_id = p_tecnico_id AND NOT e.es_final;

    v_carga := LEAST(100, (v_abiertos::NUMERIC / NULLIF(v_capacidad, 0)) * 100);

    RETURN ROUND(
          (w_prio   * v_peso_prio)
        + (w_espera * v_aging)
        + (w_sla    * v_urgencia)
        + (w_esp    * v_especialidad)
        - (w_carga  * v_carga)
    , 3);
END;
$$ LANGUAGE plpgsql STABLE;

-- =====================================================================
-- Vista de la bolsa de demanda — casos esperando técnico
-- Solo entran los tipos con ingresa_bolsa = TRUE (queja y reclamo)
-- =====================================================================
CREATE OR REPLACE VIEW vw_bolsa_demanda AS
SELECT  c.id                AS caso_id,
        c.numero_boleta,
        t.nombre            AS tipo,
        p.nombre            AS prioridad,
        s.nombre            AS servicio,
        c.asunto,
        c.fecha_registro,
        ROUND(EXTRACT(EPOCH FROM (NOW() - c.fecha_registro)) / 3600, 1) AS horas_en_cola,
        c.fecha_limite_resolucion,
        (NOW() > c.fecha_limite_resolucion) AS sla_vencido
FROM caso c
JOIN tipo_solicitud t ON t.id = c.tipo_solicitud_id
JOIN prioridad p      ON p.id = c.prioridad_id
JOIN estado_caso e    ON e.id = c.estado_id
LEFT JOIN servicio s  ON s.id = c.servicio_id
WHERE e.codigo = 'EN_COLA'
  AND t.ingresa_bolsa
  AND c.tecnico_asignado_id IS NULL;

-- =====================================================================
-- CU-04 — Solicitar siguiente caso
--
-- El técnico presiona el botón; esta función decide qué caso le toca.
-- FOR UPDATE SKIP LOCKED evita que dos técnicos reciban el mismo caso.
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_solicitar_siguiente_caso(
    p_tecnico_id    INTEGER,
    p_ip            VARCHAR DEFAULT NULL
) RETURNS TABLE (
    caso_id         INTEGER,
    numero_boleta   VARCHAR,
    asunto          VARCHAR,
    score           NUMERIC
) AS $$
DECLARE
    v_tecnico       RECORD;
    v_abiertos      INTEGER;
    v_caso_id       INTEGER;
    v_score         NUMERIC;
    v_estado_ant    INTEGER;
    v_id_en_cola    INTEGER;
    v_id_asignado   INTEGER;
BEGIN
    SELECT * INTO v_tecnico FROM tecnico WHERE usuario_id = p_tecnico_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'El usuario % no está registrado como técnico', p_tecnico_id;
    END IF;

    -- FA: el técnico marcó no disponible
    IF NOT v_tecnico.disponible THEN
        RAISE EXCEPTION 'El técnico no se encuentra disponible para recibir casos';
    END IF;

    -- FA: el técnico ya llegó a su tope de casos
    SELECT COUNT(*) INTO v_abiertos
    FROM caso c JOIN estado_caso e ON e.id = c.estado_id
    WHERE c.tecnico_asignado_id = p_tecnico_id AND NOT e.es_final;

    IF v_abiertos >= v_tecnico.capacidad_maxima THEN
        RAISE EXCEPTION 'El técnico alcanzó su capacidad máxima (% casos abiertos)', v_abiertos;
    END IF;

    SELECT id INTO v_id_en_cola  FROM estado_caso WHERE codigo = 'EN_COLA';
    SELECT id INTO v_id_asignado FROM estado_caso WHERE codigo = 'ASIGNADO';

    -- Selección del caso ganador
    SELECT c.id INTO v_caso_id
    FROM caso c
    WHERE c.estado_id = v_id_en_cola
      AND c.tecnico_asignado_id IS NULL
      AND EXISTS (SELECT 1 FROM tipo_solicitud t
                  WHERE t.id = c.tipo_solicitud_id AND t.ingresa_bolsa)
      -- RN: Conflicto de interés
      AND NOT EXISTS (SELECT 1 FROM caso_involucrado ci
                      WHERE ci.caso_id = c.id AND ci.usuario_id = p_tecnico_id)
    ORDER BY fn_calcular_score(c.id, p_tecnico_id) DESC, c.fecha_registro ASC
    LIMIT 1
    FOR UPDATE SKIP LOCKED;

    -- FA: no hay casos disponibles
    IF v_caso_id IS NULL THEN
        RAISE EXCEPTION 'No hay casos disponibles en la bolsa de demanda';
    END IF;

    v_score := fn_calcular_score(v_caso_id, p_tecnico_id);

    SELECT estado_id INTO v_estado_ant FROM caso WHERE id = v_caso_id;

    UPDATE caso
       SET estado_id           = v_id_asignado,
           tecnico_asignado_id = p_tecnico_id,
           fecha_asignacion    = NOW()
     WHERE id = v_caso_id;

    INSERT INTO asignacion (caso_id, tecnico_id, score)
    VALUES (v_caso_id, p_tecnico_id, v_score);

    INSERT INTO seguimiento (caso_id, usuario_id, estado_anterior_id, estado_nuevo_id,
                             comentario, visible_cliente)
    VALUES (v_caso_id, p_tecnico_id, v_estado_ant, v_id_asignado,
            'Caso asignado automáticamente por demanda', TRUE);

    PERFORM fn_registrar_bitacora(
        p_tecnico_id, p_ip, 'CASOS', 'ASIGNAR', 'caso', v_caso_id::VARCHAR,
        'Asignación por demanda del caso ' || v_caso_id,
        jsonb_build_object('score', v_score, 'tecnico_id', p_tecnico_id)
    );

    RETURN QUERY
    SELECT c.id, c.numero_boleta, c.asunto, v_score
    FROM caso c WHERE c.id = v_caso_id;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- Vista de apoyo para el dashboard (CU-08)
-- =====================================================================
CREATE OR REPLACE VIEW vw_indicadores AS
SELECT  t.nombre                                        AS tipo,
        e.nombre                                        AS estado,
        COUNT(*)                                        AS total,
        COUNT(*) FILTER (WHERE c.fecha_limite_resolucion < NOW()
                           AND NOT e.es_final)           AS sla_vencidos,
        ROUND(AVG(EXTRACT(EPOCH FROM (COALESCE(c.fecha_cierre, NOW()) - c.fecha_registro))/3600)::NUMERIC, 1)
                                                        AS horas_promedio
FROM caso c
JOIN tipo_solicitud t ON t.id = c.tipo_solicitud_id
JOIN estado_caso e    ON e.id = c.estado_id
GROUP BY t.nombre, e.nombre;
