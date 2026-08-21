-- =====================================================================
-- 02_bitacora.sql — Bitácora inalterable (Módulo 7 del TDR / CU-10)
--
-- Estrategia de "impedir modificación de bitácoras":
--   1. Tabla de solo inserción: triggers que rechazan UPDATE y DELETE
--   2. Permisos revocados a nivel de motor para el usuario de la aplicación
--   3. Cadena de hash: cada fila encadena con el hash de la anterior
-- =====================================================================

SET search_path TO soltec, public;

CREATE TABLE bitacora (
    id              BIGSERIAL PRIMARY KEY,
    fecha_hora      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    usuario_id      INTEGER,
    usuario_correo  VARCHAR(150),
    direccion_ip    VARCHAR(45),
    modulo          VARCHAR(50)  NOT NULL,
    accion          VARCHAR(50)  NOT NULL,
    entidad         VARCHAR(50),
    entidad_id      VARCHAR(50),
    descripcion     VARCHAR(500) NOT NULL,
    datos           JSONB,
    hash_anterior   CHAR(64),
    hash_actual     CHAR(64)     NOT NULL
);

CREATE INDEX ix_bitacora_fecha   ON bitacora(fecha_hora);
CREATE INDEX ix_bitacora_usuario ON bitacora(usuario_id);
CREATE INDEX ix_bitacora_modulo  ON bitacora(modulo, accion);

COMMENT ON COLUMN bitacora.hash_anterior IS 'Hash del registro inmediatamente anterior; NULL solo en el primero';
COMMENT ON COLUMN bitacora.hash_actual   IS 'SHA-256 del contenido de esta fila + hash_anterior';

-- ---------------------------------------------------------------------
-- Cálculo de la cadena de hash
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_bitacora_encadenar()
RETURNS TRIGGER AS $$
DECLARE
    v_hash_anterior CHAR(64);
    v_contenido     TEXT;
BEGIN
    -- Serializa las inserciones concurrentes para que la cadena no se rompa
    PERFORM pg_advisory_xact_lock(hashtext('soltec.bitacora'));

    SELECT hash_actual INTO v_hash_anterior
    FROM bitacora
    ORDER BY id DESC
    LIMIT 1;

    NEW.hash_anterior := v_hash_anterior;

    v_contenido := COALESCE(v_hash_anterior, 'GENESIS')
                || '|' || NEW.fecha_hora::TEXT
                || '|' || COALESCE(NEW.usuario_id::TEXT, '')
                || '|' || COALESCE(NEW.usuario_correo, '')
                || '|' || COALESCE(NEW.direccion_ip, '')
                || '|' || NEW.modulo
                || '|' || NEW.accion
                || '|' || COALESCE(NEW.entidad, '')
                || '|' || COALESCE(NEW.entidad_id, '')
                || '|' || NEW.descripcion
                || '|' || COALESCE(NEW.datos::TEXT, '');

    NEW.hash_actual := encode(sha256(convert_to(v_contenido, 'UTF8')), 'hex');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_bitacora_encadenar
    BEFORE INSERT ON bitacora
    FOR EACH ROW EXECUTE FUNCTION fn_bitacora_encadenar();

-- ---------------------------------------------------------------------
-- Bloqueo de modificación y borrado
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_bitacora_inmutable()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'La bitácora es de solo inserción: no se permite % (TDR, módulo 7)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_bitacora_no_update
    BEFORE UPDATE ON bitacora
    FOR EACH ROW EXECUTE FUNCTION fn_bitacora_inmutable();

CREATE TRIGGER tg_bitacora_no_delete
    BEFORE DELETE ON bitacora
    FOR EACH ROW EXECUTE FUNCTION fn_bitacora_inmutable();

-- ---------------------------------------------------------------------
-- Registro de eventos: única puerta de entrada desde la aplicación
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_registrar_bitacora(
    p_usuario_id    INTEGER,
    p_ip            VARCHAR,
    p_modulo        VARCHAR,
    p_accion        VARCHAR,
    p_entidad       VARCHAR,
    p_entidad_id    VARCHAR,
    p_descripcion   VARCHAR,
    p_datos         JSONB DEFAULT NULL
) RETURNS BIGINT AS $$
DECLARE
    v_id     BIGINT;
    v_correo VARCHAR(150);
BEGIN
    SELECT correo INTO v_correo FROM usuario WHERE id = p_usuario_id;

    INSERT INTO bitacora (usuario_id, usuario_correo, direccion_ip, modulo,
                          accion, entidad, entidad_id, descripcion, datos, hash_actual)
    VALUES (p_usuario_id, v_correo, p_ip, p_modulo,
            p_accion, p_entidad, p_entidad_id, p_descripcion, p_datos, 'PENDIENTE')
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- Verificación de integridad (CU-10)
-- Recorre la cadena y devuelve la primera fila alterada, si existe
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_verificar_integridad_bitacora()
RETURNS TABLE (
    registro_id     BIGINT,
    fecha_hora      TIMESTAMPTZ,
    estado          VARCHAR,
    detalle         VARCHAR
) AS $$
DECLARE
    r               RECORD;
    v_esperado      CHAR(64);
    v_hash_previo   CHAR(64) := NULL;
    v_contenido     TEXT;
BEGIN
    FOR r IN SELECT * FROM bitacora ORDER BY id LOOP

        IF r.hash_anterior IS DISTINCT FROM v_hash_previo THEN
            RETURN QUERY SELECT r.id, r.fecha_hora, 'CADENA ROTA'::VARCHAR,
                   'El enlace con el registro anterior no coincide'::VARCHAR;
            RETURN;
        END IF;

        v_contenido := COALESCE(r.hash_anterior, 'GENESIS')
                    || '|' || r.fecha_hora::TEXT
                    || '|' || COALESCE(r.usuario_id::TEXT, '')
                    || '|' || COALESCE(r.usuario_correo, '')
                    || '|' || COALESCE(r.direccion_ip, '')
                    || '|' || r.modulo
                    || '|' || r.accion
                    || '|' || COALESCE(r.entidad, '')
                    || '|' || COALESCE(r.entidad_id, '')
                    || '|' || r.descripcion
                    || '|' || COALESCE(r.datos::TEXT, '');

        v_esperado := encode(sha256(convert_to(v_contenido, 'UTF8')), 'hex');

        IF v_esperado <> r.hash_actual THEN
            RETURN QUERY SELECT r.id, r.fecha_hora, 'ALTERADO'::VARCHAR,
                   'El contenido no corresponde al hash almacenado'::VARCHAR;
            RETURN;
        END IF;

        v_hash_previo := r.hash_actual;
    END LOOP;

    RETURN QUERY SELECT NULL::BIGINT, NOW(), 'INTEGRA'::VARCHAR,
           'La cadena completa fue verificada sin alteraciones'::VARCHAR;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- Permisos: el usuario de la aplicación no puede tocar la bitácora
-- Ejecutar DESPUÉS de crear el rol de aplicación
-- ---------------------------------------------------------------------
-- CREATE ROLE app_soltec LOGIN PASSWORD 'cambiar_esta_clave';
-- GRANT USAGE ON SCHEMA soltec TO app_soltec;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA soltec TO app_soltec;
-- GRANT USAGE ON ALL SEQUENCES IN SCHEMA soltec TO app_soltec;
--
-- REVOKE UPDATE, DELETE, TRUNCATE ON soltec.bitacora FROM app_soltec;
-- GRANT  SELECT, INSERT ON soltec.bitacora TO app_soltec;
