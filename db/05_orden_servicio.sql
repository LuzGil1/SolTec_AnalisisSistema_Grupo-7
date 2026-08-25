-- =====================================================================
-- 05_orden_servicio.sql
--
-- Catálogo histórico de servicios ya prestados por SolTec.
--
-- Contexto: SolTec presta el servicio (instalación, reparación, visita)
-- ANTES y FUERA de este sistema. Aquí solo se guarda el registro de lo
-- que ya ocurrió, para que el cliente pueda referenciarlo al presentar
-- su caso y para que el sistema sepa qué técnico lo atendió.
--
-- Es una tabla de CONSULTA: los datos se precargan. El sistema no crea
-- ni modifica órdenes de servicio.
--
-- Requiere: 01, 02, 03 y 04 ejecutados previamente.
-- =====================================================================

SET search_path TO soltec, public;

-- =====================================================================
-- 1. TABLA
-- =====================================================================

CREATE TABLE orden_servicio (
    id              SERIAL PRIMARY KEY,
    numero_orden    VARCHAR(20)  NOT NULL UNIQUE,
    cliente_id      INTEGER      NOT NULL REFERENCES cliente(usuario_id),
    servicio_id     INTEGER      NOT NULL REFERENCES servicio(id),
    tecnico_id      INTEGER      REFERENCES tecnico(usuario_id),
    fecha_servicio  DATE         NOT NULL,
    descripcion     VARCHAR(300),
    CONSTRAINT ck_orden_fecha CHECK (fecha_servicio <= CURRENT_DATE)
);

CREATE INDEX ix_orden_cliente ON orden_servicio(cliente_id, fecha_servicio DESC);
CREATE INDEX ix_orden_tecnico ON orden_servicio(tecnico_id);

COMMENT ON TABLE  orden_servicio IS
  'Histórico de servicios prestados. Datos precargados, solo lectura desde la aplicación.';
COMMENT ON COLUMN orden_servicio.tecnico_id IS
  'Técnico que prestó el servicio. Es lo que permite detectar el conflicto de interés en una denuncia.';

-- =====================================================================
-- 2. ENLACE CON EL CASO
-- Reemplaza el campo de texto suelto por una referencia real
-- =====================================================================

ALTER TABLE caso DROP COLUMN IF EXISTS orden_servicio;
ALTER TABLE caso DROP COLUMN IF EXISTS fecha_servicio;

ALTER TABLE caso
    ADD COLUMN orden_servicio_id INTEGER REFERENCES orden_servicio(id);

CREATE INDEX ix_caso_orden ON caso(orden_servicio_id);

COMMENT ON COLUMN caso.orden_servicio_id IS
  'Servicio al que se refiere el caso. Opcional, pero es lo que activa la detección de conflicto de interés.';

-- =====================================================================
-- 3. VALIDACIÓN
-- Un cliente solo puede referenciar sus propias órdenes de servicio
-- =====================================================================

CREATE OR REPLACE FUNCTION fn_caso_validar_orden()
RETURNS TRIGGER AS $$
DECLARE
    v_cliente_orden INTEGER;
BEGIN
    IF NEW.orden_servicio_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT cliente_id INTO v_cliente_orden
    FROM orden_servicio WHERE id = NEW.orden_servicio_id;

    IF v_cliente_orden IS DISTINCT FROM NEW.cliente_id THEN
        RAISE EXCEPTION 'La orden de servicio indicada no pertenece a este cliente';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_caso_validar_orden
    BEFORE INSERT OR UPDATE ON caso
    FOR EACH ROW EXECUTE FUNCTION fn_caso_validar_orden();

-- =====================================================================
-- 4. DETECCIÓN AUTOMÁTICA DEL CONFLICTO DE INTERÉS
--
-- Cuando se registra una DENUNCIA que referencia una orden de servicio,
-- el técnico que prestó ese servicio queda marcado como involucrado.
-- A partir de ese momento no puede ver ni recibir ese caso.
-- =====================================================================

CREATE OR REPLACE FUNCTION fn_caso_marcar_involucrado()
RETURNS TRIGGER AS $$
DECLARE
    v_es_denuncia  BOOLEAN;
    v_tecnico_id   INTEGER;
    v_numero_orden VARCHAR(20);
BEGIN
    IF NEW.orden_servicio_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT (codigo = 'DENUNCIA') INTO v_es_denuncia
    FROM tipo_solicitud WHERE id = NEW.tipo_solicitud_id;

    IF NOT v_es_denuncia THEN
        RETURN NEW;
    END IF;

    SELECT tecnico_id, numero_orden INTO v_tecnico_id, v_numero_orden
    FROM orden_servicio WHERE id = NEW.orden_servicio_id;

    IF v_tecnico_id IS NULL THEN
        RETURN NEW;
    END IF;

    INSERT INTO caso_involucrado (caso_id, usuario_id, motivo)
    VALUES (NEW.id, v_tecnico_id,
            'Técnico que prestó el servicio de la orden ' || v_numero_orden)
    ON CONFLICT (caso_id, usuario_id) DO NOTHING;

    PERFORM fn_registrar_bitacora(
        NULL, NULL, 'CASOS', 'CONFLICTO_INTERES', 'caso', NEW.id::VARCHAR,
        'Técnico marcado como involucrado por denuncia sobre la orden ' || v_numero_orden,
        jsonb_build_object('tecnico_id', v_tecnico_id, 'orden', v_numero_orden)
    );

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_caso_marcar_involucrado
    AFTER INSERT ON caso
    FOR EACH ROW EXECUTE FUNCTION fn_caso_marcar_involucrado();

-- =====================================================================
-- 5. VISTA DE APOYO
-- Alimenta el desplegable "servicio recibido" del formulario del cliente
-- =====================================================================

CREATE OR REPLACE VIEW vw_ordenes_cliente AS
SELECT  o.id              AS orden_id,
        o.cliente_id,
        o.numero_orden,
        s.nombre          AS servicio,
        o.fecha_servicio,
        o.descripcion,
        u.nombres || ' ' || u.apellidos AS tecnico
FROM orden_servicio o
JOIN servicio s   ON s.id = o.servicio_id
LEFT JOIN usuario u ON u.id = o.tecnico_id;

COMMENT ON VIEW vw_ordenes_cliente IS
  'El backend debe filtrar por cliente_id: cada cliente ve únicamente sus propias órdenes.';

-- =====================================================================
-- 6. DATOS PRECARGADOS
-- Historial de servicios ya prestados a los clientes de prueba
-- =====================================================================

INSERT INTO orden_servicio
 (numero_orden, cliente_id, servicio_id, tecnico_id, fecha_servicio, descripcion)
VALUES
 ('OS-2026-0085',
  (SELECT id FROM usuario WHERE correo='pgomez@empresa.com.gt'),
  (SELECT id FROM servicio WHERE nombre='Redes'),
  (SELECT id FROM usuario WHERE correo='jperez@soltec.com.gt'),
  CURRENT_DATE - 20, 'Configuración de router y switch en oficina central'),

 ('OS-2026-0087',
  (SELECT id FROM usuario WHERE correo='pgomez@empresa.com.gt'),
  (SELECT id FROM servicio WHERE nombre='Mantenimiento correctivo'),
  (SELECT id FROM usuario WHERE correo='jperez@soltec.com.gt'),
  CURRENT_DATE - 8,  'Reparación de impresora de red'),

 ('OS-2026-0091',
  (SELECT id FROM usuario WHERE correo='shernandez@empresa.com.gt'),
  (SELECT id FROM servicio WHERE nombre='Asistencia técnica remota'),
  (SELECT id FROM usuario WHERE correo='mlopez@soltec.com.gt'),
  CURRENT_DATE - 5,  'Asistencia remota por lentitud del sistema'),

 ('OS-2026-0094',
  (SELECT id FROM usuario WHERE correo='shernandez@empresa.com.gt'),
  (SELECT id FROM servicio WHERE nombre='Mantenimiento preventivo'),
  (SELECT id FROM usuario WHERE correo='jperez@soltec.com.gt'),
  CURRENT_DATE - 2,  'Mantenimiento preventivo de equipos de cómputo');

-- =====================================================================
-- Comprobaciones sugeridas
-- =====================================================================
-- SELECT * FROM vw_ordenes_cliente ORDER BY fecha_servicio DESC;
--
-- Denuncia sobre la orden OS-2026-0087 (atendida por Jorge Pérez):
-- INSERT INTO caso (cliente_id, tipo_solicitud_id, servicio_id, orden_servicio_id,
--                   prioridad_id, estado_id, asunto, descripcion)
-- VALUES ((SELECT id FROM usuario WHERE correo='pgomez@empresa.com.gt'),
--         (SELECT id FROM tipo_solicitud WHERE codigo='DENUNCIA'),
--         (SELECT id FROM servicio WHERE nombre='Mantenimiento correctivo'),
--         (SELECT id FROM orden_servicio WHERE numero_orden='OS-2026-0087'),
--         (SELECT id FROM prioridad WHERE codigo='CRITICA'), 1,
--         'Cobro no autorizado', 'El técnico solicitó un pago adicional en efectivo.');
--
-- SELECT * FROM caso_involucrado;   -- Jorge Pérez debe aparecer automáticamente
