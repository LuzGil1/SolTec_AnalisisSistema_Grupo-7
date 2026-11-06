-- =====================================================================
-- 13_datos_demo.sql
-- Datos de demostracion para el dashboard: 360 solicitudes en los
-- ultimos 12 meses, con su historial de seguimiento, asignacion y
-- bitacora. Es SOLO para demostracion: no correr en produccion.
--
-- Correr DESPUES de 01-12. Se puede volver a correr: cada fila nueva
-- tiene una guarda de existencia. Las boletas usan el formato real
-- (SOL-<año de registro>-9NNNNN) en un rango alto, reservado para esta
-- carga. seq_boleta NO sigue ese rango: queda en 400, para que una
-- solicitud registrada en vivo salga como SOL-AAAA-000401.
--
-- Triggers de caso: se deshabilitan durante la carga para controlar
-- situaciones y fechas. Todo corre en UNA transaccion y en PostgreSQL el
-- ALTER TABLE es transaccional: si algo falla, el ROLLBACK los deja
-- habilitados. Lo que esos triggers hacen se escribe aqui a mano: boleta,
-- SLA, asignacion y marca del conflicto de interes.
--
-- Bitacora: se inserta directo porque fn_registrar_bitacora no acepta
-- fechas pasadas. tg_bitacora_encadenar sigue calculando el hash; la
-- cadena sigue el orden de id, no el de fecha_hora. Nunca se hace UPDATE
-- ni DELETE sobre ella. La zona horaria se fija antes de insertar porque
-- el hash depende de ella (ver 12_reporteria.sql).
--
-- Especialidades: el catalogo de 04 deja 4 de 6 servicios en Hardware y
-- ninguno en Software. Para que las graficas sean utiles, el paso 0 los
-- reparte, SOLO si la base se llama soltec_demo: en otra base este script
-- respeta el catalogo que encuentre. Revisar eso antes de correrlo en
-- soltec_db, que hoy tiene la clasificacion original.
--
-- Adjuntos: los archivos se crean aparte con 13_adjuntos_demo.py. Tamano y
-- hash de cada fila corresponden a esos archivos.
-- =====================================================================

SET search_path TO soltec, public;
SET TIME ZONE 'America/Guatemala';
SET client_min_messages = warning;

BEGIN;

ALTER TABLE caso DISABLE TRIGGER USER;

-- =====================================================================
-- 0. Reparto de servicios entre especialidades (solo en soltec_demo)
-- =====================================================================
DO $$
BEGIN
    IF current_database() = 'soltec_demo' THEN
        UPDATE servicio s
           SET especialidad_id = e.id
        FROM (VALUES
            ('Instalación y configuración de equipos', 'Hardware'),
            ('Mantenimiento correctivo',               'Hardware'),
            ('Redes',                                  'Redes'),
            ('Asistencia técnica presencial',          'Redes'),
            ('Mantenimiento preventivo',               'Software'),
            ('Asistencia técnica remota',              'Soporte Remoto')
        ) AS v(servicio, especialidad)
        JOIN especialidad e ON e.nombre = v.especialidad
        WHERE s.nombre = v.servicio
          AND s.especialidad_id IS DISTINCT FROM e.id;
    END IF;
END;
$$;

-- =====================================================================
-- 1. Personal y clientes. La clave es la de los usuarios de prueba.
-- =====================================================================
INSERT INTO usuario (rol_id, nombres, apellidos, correo, contrasena_hash, telefono)
SELECT r.id, v.nombres, v.apellidos, v.correo,
       (SELECT contrasena_hash FROM usuario WHERE correo = 'supervisor@soltec.com.gt'),
       v.telefono
FROM (VALUES
    ('SUPERVISOR', 'Elena',  'Marroquín', 'emarroquin@soltec.com.gt', '55511010'),
    ('TECNICO',    'Ana',    'Castillo',  'acastillo@soltec.com.gt',  '55512020'),
    ('TECNICO',    'Luis',   'Morales',   'lmorales@soltec.com.gt',   '55513030'),
    ('TECNICO',    'Diego',  'Herrera',   'dherrera@soltec.com.gt',   '55514040'),
    ('TECNICO',    'Karla',  'Méndez',    'kmendez@soltec.com.gt',    '55515050'),
    ('TECNICO',    'Rosa',   'Ajú',       'raju@soltec.com.gt',       '55516060'),
    ('TECNICO',    'Óscar',  'Juárez',    'ojuarez@soltec.com.gt',    '55517070'),
    ('CLIENTE', 'Distribuidora', 'El Progreso', 'contacto@progreso.demo.gt',  '55521001'),
    ('CLIENTE', 'Agroindustrias','del Sur',     'contacto@agrosur.demo.gt',   '55521002'),
    ('CLIENTE', 'Transportes',   'Quetzal',     'contacto@tquetzal.demo.gt',  '55521003'),
    ('CLIENTE', 'Clínica',       'San Rafael',  'contacto@csrafael.demo.gt',  '55521004'),
    ('CLIENTE', 'Textiles',      'La Aurora',   'contacto@laaurora.demo.gt',  '55521005'),
    ('CLIENTE', 'Comercial',     'Xela',        'contacto@comxela.demo.gt',   '55521006'),
    ('CLIENTE', 'Hotel',         'Las Flores',  'contacto@lasflores.demo.gt', '55521007'),
    ('CLIENTE', 'Ferretería',    'La Unión',    'contacto@launion.demo.gt',   '55521008'),
    ('CLIENTE', 'Marta',         'Cifuentes',   'mcifuentes@correo.demo.gt',  '55521009'),
    ('CLIENTE', 'Roberto',       'Sandoval',    'rsandoval@correo.demo.gt',   '55521010')
) AS v(rol, nombres, apellidos, correo, telefono)
JOIN rol r ON r.codigo = v.rol
WHERE NOT EXISTS (SELECT 1 FROM usuario u WHERE u.correo = v.correo);

-- Capacidades bajas a proposito: los tecnicos disponibles quedan llenos y
-- por eso las solicitudes EN_COLA siguen en la bolsa. Con cupo libre, el
-- barrido periodico las asignaria apenas arranque el backend.
INSERT INTO tecnico (usuario_id, capacidad_maxima, disponible, motivo_no_disponible)
SELECT u.id, v.capacidad, v.disponible, v.motivo
FROM (VALUES
    ('acastillo@soltec.com.gt', 4, TRUE,  NULL),
    ('lmorales@soltec.com.gt',  4, TRUE,  NULL),
    ('dherrera@soltec.com.gt',  3, TRUE,  NULL),
    ('kmendez@soltec.com.gt',   4, TRUE,  NULL),
    ('raju@soltec.com.gt',      3, TRUE,  NULL),
    ('ojuarez@soltec.com.gt',   4, FALSE, 'Vacaciones')
) AS v(correo, capacidad, disponible, motivo)
JOIN usuario u ON u.correo = v.correo
WHERE NOT EXISTS (SELECT 1 FROM tecnico t WHERE t.usuario_id = u.id);

INSERT INTO tecnico_especialidad (tecnico_id, especialidad_id)
SELECT u.id, e.id
FROM (VALUES
    ('acastillo@soltec.com.gt', 'Redes'),
    ('acastillo@soltec.com.gt', 'Soporte Remoto'),
    ('lmorales@soltec.com.gt',  'Hardware'),
    ('dherrera@soltec.com.gt',  'Software'),
    ('dherrera@soltec.com.gt',  'Soporte Remoto'),
    ('kmendez@soltec.com.gt',   'Redes'),
    ('kmendez@soltec.com.gt',   'Hardware'),
    ('raju@soltec.com.gt',      'Soporte Remoto'),
    ('raju@soltec.com.gt',      'Software'),
    ('ojuarez@soltec.com.gt',   'Hardware')
) AS v(correo, especialidad)
JOIN usuario u      ON u.correo = v.correo
JOIN especialidad e ON e.nombre = v.especialidad
WHERE NOT EXISTS (SELECT 1 FROM tecnico_especialidad te
                  WHERE te.tecnico_id = u.id AND te.especialidad_id = e.id);

INSERT INTO cliente (usuario_id, nit, direccion)
SELECT u.id, v.nit, v.direccion
FROM (VALUES
    ('contacto@progreso.demo.gt',  '4410021-3', 'Zona 12, Guatemala'),
    ('contacto@agrosur.demo.gt',   '5520133-7', 'Escuintla'),
    ('contacto@tquetzal.demo.gt',  '6630244-1', 'Zona 13, Guatemala'),
    ('contacto@csrafael.demo.gt',  '7740355-5', 'Antigua Guatemala'),
    ('contacto@laaurora.demo.gt',  '8850466-9', 'Mixco'),
    ('contacto@comxela.demo.gt',   '9960577-2', 'Quetzaltenango'),
    ('contacto@lasflores.demo.gt', '1170688-6', 'Panajachel'),
    ('contacto@launion.demo.gt',   '2280799-K', 'Villa Nueva'),
    ('mcifuentes@correo.demo.gt',  '3390800-4', 'Zona 7, Guatemala'),
    ('rsandoval@correo.demo.gt',   '4400911-8', 'Cobán')
) AS v(correo, nit, direccion)
JOIN usuario u ON u.correo = v.correo
WHERE NOT EXISTS (SELECT 1 FROM cliente c WHERE c.usuario_id = u.id);

-- =====================================================================
-- 2. Ordenes de servicio: tres por cliente. La primera es de hace mas de
--    un año, para que toda denuncia tenga una orden anterior.
-- =====================================================================
INSERT INTO orden_servicio (numero_orden, cliente_id, servicio_id, tecnico_id, fecha_servicio, descripcion)
SELECT 'OS-DEMO-' || LPAD(((c.n - 1) * 3 + k)::TEXT, 4, '0'),
       c.id,
       (SELECT id FROM servicio ORDER BY id OFFSET (c.n + k) % 6 LIMIT 1),
       (SELECT t.usuario_id FROM tecnico t ORDER BY t.usuario_id
        OFFSET (c.n * 3 + k) % (SELECT COUNT(*) FROM tecnico) LIMIT 1),
       CURRENT_DATE - (375 + c.n * 3 - (k - 1) * 120),
       'Servicio prestado en las instalaciones del cliente'
FROM (SELECT u.id, ROW_NUMBER() OVER (ORDER BY u.correo)::INTEGER AS n
      FROM usuario u
      WHERE u.correo LIKE '%.demo.gt'
         OR u.correo IN ('pgomez@empresa.com.gt', 'shernandez@empresa.com.gt')) c
CROSS JOIN generate_series(1, 3) AS k
WHERE NOT EXISTS (SELECT 1 FROM orden_servicio o
                  WHERE o.numero_orden = 'OS-DEMO-' || LPAD(((c.n - 1) * 3 + k)::TEXT, 4, '0'));

-- =====================================================================
-- 3. Funciones de apoyo. Viven en pg_temp: desaparecen con la sesion.
-- =====================================================================
CREATE FUNCTION pg_temp.azar(p_min FLOAT8, p_max FLOAT8) RETURNS FLOAT8 AS $$
    SELECT p_min + random() * (p_max - p_min);
$$ LANGUAGE sql VOLATILE;

-- Devuelve una posicion del arreglo segun el peso de cada una
CREATE FUNCTION pg_temp.ponderado(p_pesos INTEGER[]) RETURNS INTEGER AS $$
DECLARE
    v_total INTEGER := 0;
    v_tiro  FLOAT8;
    i       INTEGER;
BEGIN
    FOR i IN 1 .. array_length(p_pesos, 1) LOOP
        v_total := v_total + p_pesos[i];
    END LOOP;
    v_tiro := random() * v_total;
    FOR i IN 1 .. array_length(p_pesos, 1) LOOP
        v_tiro := v_tiro - p_pesos[i];
        IF v_tiro < 0 THEN
            RETURN i;
        END IF;
    END LOOP;
    RETURN array_length(p_pesos, 1);
END;
$$ LANGUAGE plpgsql VOLATILE;

CREATE FUNCTION pg_temp.elegir(p_textos TEXT[]) RETURNS TEXT AS $$
    SELECT p_textos[1 + floor(random() * array_length(p_textos, 1))::INTEGER];
$$ LANGUAGE sql VOLATILE;

-- Avanza el reloj de la solicitud sin pasarse del momento actual
CREATE FUNCTION pg_temp.paso(p_t TIMESTAMPTZ, p_horas FLOAT8) RETURNS TIMESTAMPTZ AS $$
    SELECT GREATEST(p_t, LEAST(p_t + make_interval(secs => p_horas * 3600), NOW() - INTERVAL '1 minute'));
$$ LANGUAGE sql STABLE;

CREATE FUNCTION pg_temp.seg(p_caso INTEGER, p_usuario INTEGER, p_anterior TEXT, p_nuevo TEXT,
                            p_comentario TEXT, p_visible BOOLEAN, p_fecha TIMESTAMPTZ) RETURNS VOID AS $$
    INSERT INTO seguimiento (caso_id, usuario_id, estado_anterior_id, estado_nuevo_id, comentario, visible_cliente, fecha)
    VALUES (p_caso, p_usuario,
            (SELECT id FROM estado_caso WHERE codigo = p_anterior),
            (SELECT id FROM estado_caso WHERE codigo = p_nuevo),
            p_comentario, p_visible, p_fecha);
$$ LANGUAGE sql VOLATILE;

CREATE FUNCTION pg_temp.evento(p_usuario INTEGER, p_modulo TEXT, p_accion TEXT, p_caso INTEGER,
                            p_descripcion TEXT, p_datos JSONB, p_fecha TIMESTAMPTZ) RETURNS VOID AS $$
    INSERT INTO bitacora (fecha_hora, usuario_id, usuario_correo, direccion_ip, modulo, accion,
                          entidad, entidad_id, descripcion, datos, hash_actual)
    VALUES (p_fecha, p_usuario, (SELECT correo FROM usuario WHERE id = p_usuario), '10.20.0.15',
            p_modulo, p_accion, 'caso', p_caso::TEXT, p_descripcion, p_datos, 'PENDIENTE');
$$ LANGUAGE sql VOLATILE;

-- Avance del tecnico o del supervisor: seguimiento mas su bitacora
CREATE FUNCTION pg_temp.avance(p_caso INTEGER, p_usuario INTEGER, p_modulo TEXT, p_anterior TEXT, p_nuevo TEXT,
                               p_comentario TEXT, p_fecha TIMESTAMPTZ) RETURNS VOID AS $$
BEGIN
    PERFORM pg_temp.seg(p_caso, p_usuario, p_anterior, p_nuevo, p_comentario, FALSE, p_fecha);
    PERFORM pg_temp.evento(p_usuario, p_modulo, 'REGISTRAR_AVANCE', p_caso,
                        'Avance del caso ' || p_caso || ': ' || p_anterior || ' -> ' || p_nuevo,
                        jsonb_build_object('estadoAnterior', p_anterior, 'estadoNuevo', p_nuevo), p_fecha);
END;
$$ LANGUAGE plpgsql VOLATILE;

-- Tecnico para el historial: suele tener la especialidad del servicio
CREATE FUNCTION pg_temp.tecnico_historico(p_especialidad INTEGER, p_excluir INTEGER[]) RETURNS INTEGER AS $$
    SELECT t.usuario_id
    FROM tecnico t
    WHERE NOT t.usuario_id = ANY (p_excluir)
    ORDER BY (random() < 0.8 AND EXISTS (SELECT 1 FROM tecnico_especialidad te
                                         WHERE te.tecnico_id = t.usuario_id
                                           AND te.especialidad_id = p_especialidad)) DESC,
             random()
    LIMIT 1;
$$ LANGUAGE sql VOLATILE;

-- Tecnico para una solicitud que queda abierta: mismo filtro duro que
-- fn_asignar_solicitud. Devuelve NULL si nadie tiene cupo.
CREATE FUNCTION pg_temp.tecnico_con_cupo(p_especialidad INTEGER, p_excluir INTEGER[]) RETURNS INTEGER AS $$
    SELECT t.usuario_id
    FROM tecnico t
    JOIN usuario u ON u.id = t.usuario_id
    CROSS JOIN LATERAL (SELECT COUNT(*) AS abiertos
                        FROM caso c JOIN estado_caso e ON e.id = c.estado_id
                        WHERE c.tecnico_asignado_id = t.usuario_id AND NOT e.es_final) carga
    WHERE t.disponible AND u.activo
      AND NOT t.usuario_id = ANY (p_excluir)
      AND carga.abiertos < t.capacidad_maxima
    ORDER BY EXISTS (SELECT 1 FROM tecnico_especialidad te
                     WHERE te.tecnico_id = t.usuario_id
                       AND te.especialidad_id = p_especialidad) DESC,
             carga.abiertos, t.usuario_id
    LIMIT 1;
$$ LANGUAGE sql VOLATILE;

-- Misma formula de fn_calcular_score, pero evaluada en el momento de la
-- asignacion y no en NOW(). La carga del tecnico no quedo guardada, asi
-- que se aproxima.
CREATE FUNCTION pg_temp.score(p_peso NUMERIC, p_horas_espera FLOAT8, p_horas_sla FLOAT8,
                              p_coincide BOOLEAN) RETURNS NUMERIC AS $$
    SELECT ROUND((
          (SELECT valor FROM parametro WHERE clave = 'PESO_PRIORIDAD') * p_peso
        + (SELECT valor FROM parametro WHERE clave = 'PESO_ESPERA')
          * LEAST(100, p_horas_espera * (SELECT valor FROM parametro WHERE clave = 'FACTOR_AGING')::FLOAT8)::NUMERIC
        + (SELECT valor FROM parametro WHERE clave = 'PESO_SLA')
          * COALESCE(LEAST(100, 100 * p_horas_espera / NULLIF(p_horas_sla, 0))::NUMERIC, 0)
        + (SELECT valor FROM parametro WHERE clave = 'PESO_ESPECIALIDAD') * (CASE WHEN p_coincide THEN 100 ELSE 0 END)
        - (SELECT valor FROM parametro WHERE clave = 'PESO_CARGA') * pg_temp.azar(15, 70)::NUMERIC
    )::NUMERIC, 3);
$$ LANGUAGE sql VOLATILE;

-- Registra una asignacion con su seguimiento automatico y su bitacora
CREATE FUNCTION pg_temp.asignar(p_caso INTEGER, p_tecnico INTEGER, p_especialidad INTEGER, p_peso NUMERIC,
                                p_horas_espera FLOAT8, p_horas_sla FLOAT8, p_fecha TIMESTAMPTZ) RETURNS VOID AS $$
DECLARE
    v_coincide BOOLEAN;
    v_score    NUMERIC;
BEGIN
    SELECT EXISTS (SELECT 1 FROM tecnico_especialidad te
                   WHERE te.tecnico_id = p_tecnico AND te.especialidad_id = p_especialidad)
      INTO v_coincide;
    v_score := pg_temp.score(p_peso, p_horas_espera, p_horas_sla, v_coincide);

    INSERT INTO asignacion (caso_id, tecnico_id, score, motivo, fecha_asignacion)
    VALUES (p_caso, p_tecnico, v_score, 'Asignación automática del sistema', p_fecha);

    PERFORM pg_temp.seg(p_caso, p_tecnico, 'EN_COLA', 'ASIGNADO',
                        'Caso asignado por el sistema para su atención', TRUE, p_fecha);
    PERFORM pg_temp.evento(p_tecnico, 'CASOS', 'ASIGNAR', p_caso,
                        'Asignación automática del caso ' || p_caso || ' al técnico ' || p_tecnico,
                        jsonb_build_object('score', v_score, 'tecnico_id', p_tecnico, 'automatica', true), p_fecha);
END;
$$ LANGUAGE plpgsql VOLATILE;

-- =====================================================================
-- 4. Solicitudes. Cada una recorre el mismo flujo que la aplicacion.
--
-- Se avanza por dias de forma despareja: pesan mas los dias habiles, los
-- meses recientes y dos semanas con incidentes. Cada solicitud tiene su
-- propia semilla, asi el contenido no cambia entre corridas.
-- =====================================================================
DO $$
DECLARE
    c_total         CONSTANT INTEGER := 360;
    v_hoy           DATE := (NOW() AT TIME ZONE 'America/Guatemala')::DATE;
    v_pesos_dia     FLOAT8[] := ARRAY[]::FLOAT8[];
    v_suma_dias     FLOAT8 := 0;
    v_acumulado     FLOAT8;
    v_objetivo      FLOAT8;
    v_peso_dia      FLOAT8;
    v_dia_pos       INTEGER;
    v_dia           DATE;
    d               INTEGER;
    i               INTEGER;

    v_clientes      INTEGER[];
    v_servicios     INTEGER[];
    v_personal      INTEGER[];
    v_supervisores  INTEGER[];
    v_horas_pico    INTEGER[] := ARRAY[7,8,8,9,9,9,9,9,10,10,10,10,10,11,11,11,12,13,14,14,14,14,15,15,15,15,16,16,16,17,17,18,19,21];

    v_boleta        VARCHAR(20);
    v_caso          INTEGER;
    v_tipo          TEXT;
    v_tipo_id       INTEGER;
    v_prioridad     TEXT;
    v_prioridad_id  INTEGER;
    v_peso_prio     NUMERIC;
    v_cliente       INTEGER;
    v_servicio      INTEGER;
    v_especialidad  INTEGER;
    v_orden         INTEGER;
    v_orden_tecnico INTEGER;
    v_orden_numero  TEXT;
    v_relacionado   INTEGER;
    v_h_resp        FLOAT8;
    v_h_res         FLOAT8;
    v_reg           TIMESTAMPTZ;
    v_edad          FLOAT8;
    v_destino       TEXT;
    v_t             TIMESTAMPTZ;
    v_estado        TEXT;
    v_tecnico       INTEGER;
    v_anterior      INTEGER;
    v_supervisor    INTEGER;
    v_responsable   INTEGER;
    v_f_asig        TIMESTAMPTZ;
    v_f_res         TIMESTAMPTZ;
    v_f_cierre      TIMESTAMPTZ;
    v_solucion      TEXT;
    v_modulo        TEXT;
    v_excluir       INTEGER[];
    v_abierta       BOOLEAN;
    v_plazo         FLOAT8;
    r               FLOAT8;
BEGIN
    SELECT array_agg(u.id ORDER BY u.correo) INTO v_clientes
    FROM usuario u
    WHERE u.correo LIKE '%.demo.gt'
       OR u.correo IN ('pgomez@empresa.com.gt', 'shernandez@empresa.com.gt');
    SELECT array_agg(id ORDER BY id) INTO v_servicios FROM servicio;
    SELECT array_agg(usuario_id ORDER BY usuario_id) INTO v_personal FROM tecnico;
    SELECT array_agg(u.id ORDER BY u.id) INTO v_supervisores
    FROM usuario u JOIN rol ro ON ro.id = u.rol_id
    WHERE ro.codigo = 'SUPERVISOR' AND u.correo IN ('supervisor@soltec.com.gt', 'emarroquin@soltec.com.gt');

    PERFORM setseed(0.25);
    FOR d IN 0 .. 364 LOOP
        v_dia := v_hoy - 364 + d;
        v_peso_dia := (0.5 + d / 364.0) * pg_temp.azar(0.4, 1.6);
        IF EXTRACT(ISODOW FROM v_dia) = 6 THEN
            v_peso_dia := v_peso_dia * 0.3;
        ELSIF EXTRACT(ISODOW FROM v_dia) = 7 THEN
            v_peso_dia := v_peso_dia * 0.12;
        END IF;
        IF d BETWEEN 118 AND 130 THEN
            v_peso_dia := v_peso_dia * 3;
        ELSIF d BETWEEN 280 AND 289 THEN
            v_peso_dia := v_peso_dia * 2.5;
        END IF;
        v_pesos_dia := v_pesos_dia || v_peso_dia;
        v_suma_dias := v_suma_dias + v_peso_dia;
    END LOOP;

    v_dia_pos := 1;
    v_acumulado := v_pesos_dia[1];

    FOR i IN 1 .. c_total LOOP
        -- El dia se calcula siempre, para que no dependa de que ya existan
        v_objetivo := (i - 0.5) / c_total * v_suma_dias;
        WHILE v_acumulado < v_objetivo AND v_dia_pos < 365 LOOP
            v_dia_pos := v_dia_pos + 1;
            v_acumulado := v_acumulado + v_pesos_dia[v_dia_pos];
        END LOOP;
        v_dia := v_hoy - 365 + v_dia_pos;

        -- La guarda mira solo el numero: el año sale de la fecha de registro
        IF EXISTS (SELECT 1 FROM caso WHERE numero_boleta LIKE 'SOL-%-' || (900000 + i)::TEXT) THEN
            CONTINUE;
        END IF;

        PERFORM setseed(i::FLOAT8 / (c_total + 1) - 0.5);

        v_reg := (v_dia + make_interval(hours => v_horas_pico[1 + floor(random() * array_length(v_horas_pico, 1))::INTEGER],
                                        mins  => floor(random() * 60)::INTEGER))
                 AT TIME ZONE 'America/Guatemala';
        IF v_reg > NOW() - INTERVAL '2 hours' THEN
            v_reg := NOW() - make_interval(hours => (2 + floor(random() * 10))::INTEGER);
        END IF;
        v_edad := EXTRACT(EPOCH FROM (NOW() - v_reg)) / 3600;
        v_boleta := 'SOL-' || TO_CHAR(v_reg AT TIME ZONE 'America/Guatemala', 'YYYY') || '-' || (900000 + i)::TEXT;

        v_tipo := (ARRAY['QUEJA', 'RECLAMO', 'DENUNCIA', 'SUGERENCIA'])[pg_temp.ponderado(ARRAY[45, 25, 10, 20])];
        v_cliente := v_clientes[1 + (pg_temp.ponderado(ARRAY[12, 10, 9, 8, 8, 7, 6, 6, 5, 5, 3, 3]) - 1) % array_length(v_clientes, 1)];
        v_servicio := v_servicios[1 + (pg_temp.ponderado(ARRAY[30, 22, 18, 14, 10, 6]) - 1) % array_length(v_servicios, 1)];
        v_orden := NULL;
        v_orden_tecnico := NULL;
        v_orden_numero := NULL;
        v_relacionado := NULL;

        IF v_tipo = 'RECLAMO' THEN
            SELECT c.id INTO v_relacionado
            FROM caso c JOIN tipo_solicitud t ON t.id = c.tipo_solicitud_id
            WHERE c.cliente_id = v_cliente AND c.fecha_registro < v_reg AND t.codigo IN ('QUEJA', 'RECLAMO')
            ORDER BY random() LIMIT 1;
            IF v_relacionado IS NULL THEN
                v_tipo := 'QUEJA';
            END IF;
        END IF;

        -- Las ultimas cuatro quedan recien recibidas
        IF i > c_total - 4 THEN
            v_tipo := 'QUEJA';
            v_relacionado := NULL;
        END IF;

        IF v_tipo = 'SUGERENCIA' AND random() < 0.3 THEN
            v_servicio := NULL;
        END IF;

        IF v_tipo = 'DENUNCIA' OR (v_tipo IN ('QUEJA', 'RECLAMO') AND random() < 0.4) THEN
            SELECT o.id, o.tecnico_id, o.numero_orden, o.servicio_id
              INTO v_orden, v_orden_tecnico, v_orden_numero, v_servicio
            FROM orden_servicio o
            WHERE o.cliente_id = v_cliente AND o.fecha_servicio <= v_dia AND o.numero_orden LIKE 'OS-DEMO-%'
            ORDER BY random() LIMIT 1;
        END IF;

        SELECT id INTO v_tipo_id FROM tipo_solicitud WHERE codigo = v_tipo;
        SELECT especialidad_id INTO v_especialidad FROM servicio WHERE id = v_servicio;

        v_prioridad := (ARRAY['CRITICA', 'ALTA', 'MEDIA', 'BAJA'])[
            pg_temp.ponderado(CASE v_tipo WHEN 'DENUNCIA'   THEN ARRAY[65, 25, 8, 2]
                                          WHEN 'SUGERENCIA' THEN ARRAY[1, 5, 24, 70]
                                          ELSE ARRAY[10, 30, 42, 18] END)];
        SELECT p.id, p.peso INTO v_prioridad_id, v_peso_prio FROM prioridad p WHERE p.codigo = v_prioridad;

        v_h_resp := NULL;
        v_h_res := NULL;
        SELECT horas_respuesta, horas_resolucion INTO v_h_resp, v_h_res
        FROM sla WHERE tipo_solicitud_id = v_tipo_id AND prioridad_id = v_prioridad_id;

        -- Destino de la solicitud segun su antiguedad
        r := random();
        IF i > c_total - 4 THEN
            v_destino := 'RECIBIDO';
        ELSIF v_tipo = 'SUGERENCIA' THEN
            v_destino := CASE WHEN r < (CASE WHEN v_edad < 720 THEN 0.6 ELSE 0.12 END)
                              THEN 'EN_BACKLOG' ELSE 'CERRADO' END;
        ELSIF v_tipo = 'DENUNCIA' THEN
            IF v_edad < 720 THEN
                v_destino := CASE WHEN r < 0.5 THEN 'EN_REVISION' WHEN r < 0.9 THEN 'EN_PROCESO' ELSE 'RESUELTO' END;
            ELSE
                v_destino := CASE WHEN r < 0.05 THEN 'EN_PROCESO' WHEN r < 0.1 THEN 'RESUELTO'
                                  WHEN r < 0.75 THEN 'CERRADO' ELSE 'IMPROCEDENTE' END;
            END IF;
        ELSIF v_edad < 720 THEN
            v_destino := CASE WHEN r < 0.10 THEN 'EN_COLA' WHEN r < 0.40 THEN 'ASIGNADO'
                              WHEN r < 0.68 THEN 'EN_PROCESO' WHEN r < 0.76 THEN 'ESPERA_CLIENTE'
                              WHEN r < 0.86 THEN 'ESCALADO' ELSE 'RESUELTO' END;
        ELSE
            v_destino := CASE WHEN r < 0.012 THEN 'EN_PROCESO' WHEN r < 0.02 THEN 'ESPERA_CLIENTE'
                              WHEN r < 0.035 THEN 'ESCALADO' WHEN r < 0.055 THEN 'RESUELTO'
                              WHEN r < 0.82 THEN 'CERRADO' WHEN r < 0.94 THEN 'IMPROCEDENTE'
                              ELSE 'DUPLICADO' END;
        END IF;

        INSERT INTO caso (numero_boleta, cliente_id, tipo_solicitud_id, servicio_id, prioridad_id, estado_id,
                          caso_relacionado_id, asunto, descripcion, orden_servicio_id, fecha_registro,
                          fecha_limite_respuesta, fecha_limite_resolucion)
        VALUES (v_boleta, v_cliente, v_tipo_id, v_servicio, v_prioridad_id,
                (SELECT estado_inicial_id FROM tipo_solicitud WHERE id = v_tipo_id),
                v_relacionado,
                CASE v_tipo
                    WHEN 'QUEJA' THEN pg_temp.elegir(ARRAY['El técnico no llegó a la cita', 'El enlace se cae varias veces al día',
                        'La falla reportada sigue igual', 'Mala atención en la visita', 'El equipo volvió a fallar', 'Lentitud en la red de la oficina'])
                    WHEN 'RECLAMO' THEN pg_temp.elegir(ARRAY['La solución no resolvió el problema', 'Cobro distinto al acordado',
                        'Reclamo por caso anterior sin resolver', 'Repuesto instalado con defecto', 'Solicito revisión del caso previo'])
                    WHEN 'DENUNCIA' THEN pg_temp.elegir(ARRAY['Cobro no autorizado en efectivo', 'Trato irrespetuoso del técnico',
                        'Retiro de equipo sin autorización', 'Uso indebido de la información'])
                    ELSE pg_temp.elegir(ARRAY['Ampliar el horario de atención', 'Portal para consultar facturas',
                        'Avisos por mensaje de texto', 'Encuesta después de cada visita', 'Más técnicos en occidente'])
                END,
                CASE v_tipo
                    WHEN 'DENUNCIA' THEN 'Relato de lo ocurrido durante el servicio prestado por el personal de SolTec.'
                    WHEN 'SUGERENCIA' THEN 'Propuesta de mejora enviada por el cliente para el servicio de soporte.'
                    ELSE pg_temp.elegir(ARRAY['La oficina central quedó sin servicio durante la mañana.',
                        'El problema afecta a varios usuarios y se repite cada semana.',
                        'Se reportó por teléfono y nadie dio seguimiento.',
                        'El servicio se interrumpe en horario de atención al público.'])
                END,
                v_orden, v_reg,
                CASE WHEN v_h_resp IS NOT NULL THEN v_reg + make_interval(hours => v_h_resp::INTEGER) END,
                CASE WHEN v_h_res  IS NOT NULL THEN v_reg + make_interval(hours => v_h_res::INTEGER) END)
        RETURNING id INTO v_caso;

        PERFORM pg_temp.evento(v_cliente, 'CASOS', 'REGISTRO_SOLICITUD', v_caso,
                            'Registro de solicitud ' || v_boleta,
                            jsonb_build_object('numeroBoleta', v_boleta, 'tipo', v_tipo), v_reg);

        IF random() < 0.2 THEN
            IF i % 2 = 0 THEN
                INSERT INTO adjunto (caso_id, nombre_archivo, tipo_mime, tamano_bytes, ruta, hash_sha256, usuario_id, fecha_carga)
                VALUES (v_caso, 'evidencia_' || i || '.png', 'image/png', 165, 'demo/evidencia_' || i || '.png',
                        'e064b5fbfed3bfb02d8e23b20c496dfc296c5755135f9c4f6f4a149944936db3', v_cliente, pg_temp.paso(v_reg, 0.05));
            ELSE
                INSERT INTO adjunto (caso_id, nombre_archivo, tipo_mime, tamano_bytes, ruta, hash_sha256, usuario_id, fecha_carga)
                VALUES (v_caso, 'evidencia_' || i || '.pdf', 'application/pdf', 607, 'demo/evidencia_' || i || '.pdf',
                        'f64da0d37e0f7ca4544a657dac0c7c8d394d23c01de60227f10a0ccd37d7ff29', v_cliente, pg_temp.paso(v_reg, 0.05));
            END IF;
        END IF;

        v_t := v_reg;
        v_estado := CASE v_tipo WHEN 'DENUNCIA' THEN 'EN_REVISION' WHEN 'SUGERENCIA' THEN 'EN_BACKLOG' ELSE 'EN_COLA' END;
        v_tecnico := NULL;
        v_responsable := NULL;
        v_f_asig := NULL;
        v_f_res := NULL;
        v_f_cierre := NULL;
        v_solucion := NULL;
        v_supervisor := v_supervisores[1 + floor(random() * array_length(v_supervisores, 1))::INTEGER];
        v_excluir := ARRAY[]::INTEGER[];

        <<flujo>>
        BEGIN
            IF v_destino = 'RECIBIDO' THEN
                v_estado := 'RECIBIDO';
                EXIT flujo;
            END IF;

            -- Sugerencia: la responde el supervisor y termina en CERRADO
            IF v_tipo = 'SUGERENCIA' THEN
                IF v_destino = 'CERRADO' THEN
                    v_t := pg_temp.paso(v_t, pg_temp.azar(24, 300));
                    v_solucion := 'Se revisó la propuesta y se informó al cliente la decisión tomada.';
                    v_f_res := v_t;
                    v_f_cierre := v_t;
                    PERFORM pg_temp.seg(v_caso, v_supervisor, 'EN_BACKLOG', 'CERRADO',
                                        CASE WHEN random() < 0.5 THEN 'Sugerencia acogida. ' ELSE 'Sugerencia no acogida. ' END || v_solucion,
                                        FALSE, v_t);
                    PERFORM pg_temp.evento(v_supervisor, 'SUGERENCIAS', 'RESPONDER', v_caso,
                                        'Respuesta a la sugerencia ' || v_boleta, jsonb_build_object('acogida', true), v_t);
                    v_estado := 'CERRADO';
                END IF;
                EXIT flujo;
            END IF;

            -- Denuncia: nunca pasa por un tecnico
            IF v_tipo = 'DENUNCIA' THEN
                INSERT INTO caso_involucrado (caso_id, usuario_id, motivo)
                VALUES (v_caso, v_orden_tecnico, 'Técnico que prestó el servicio de la orden ' || v_orden_numero);
                PERFORM pg_temp.evento(NULL, 'CASOS', 'CONFLICTO_INTERES', v_caso,
                                    'Técnico marcado como involucrado por denuncia sobre la orden ' || v_orden_numero,
                                    jsonb_build_object('tecnico_id', v_orden_tecnico, 'orden', v_orden_numero), v_reg);
                IF v_destino = 'EN_REVISION' THEN
                    EXIT flujo;
                END IF;

                v_t := pg_temp.paso(v_t, v_h_resp * pg_temp.azar(0.3, 1.5));
                IF v_destino = 'IMPROCEDENTE' AND random() < 0.4 THEN
                    PERFORM pg_temp.avance(v_caso, v_supervisor, 'DENUNCIAS', 'EN_REVISION', 'IMPROCEDENTE',
                                           'No se encontraron elementos para continuar con la denuncia.', v_t);
                    v_estado := 'IMPROCEDENTE';
                    v_f_cierre := v_t;
                    EXIT flujo;
                END IF;
                PERFORM pg_temp.avance(v_caso, v_supervisor, 'DENUNCIAS', 'EN_REVISION', 'EN_PROCESO',
                                       'Se inicia la investigación y se cita al personal señalado.', v_t);
                v_estado := 'EN_PROCESO';

                IF random() < 0.25 THEN
                    v_anterior := v_personal[1 + floor(random() * array_length(v_personal, 1))::INTEGER];
                    IF v_anterior <> v_orden_tecnico THEN
                        v_t := pg_temp.paso(v_t, pg_temp.azar(0.5, 4));
                        INSERT INTO caso_involucrado (caso_id, usuario_id, motivo)
                        VALUES (v_caso, v_anterior, 'Acompañó al técnico durante la visita.');
                        PERFORM pg_temp.evento(v_supervisor, 'DENUNCIAS', 'REGISTRAR_INVOLUCRADO', v_caso,
                                            'Registro de personal involucrado en la denuncia ' || v_boleta,
                                            jsonb_build_object('usuario_id', v_anterior), v_t);
                    END IF;
                END IF;

                IF v_destino = 'EN_PROCESO' THEN
                    EXIT flujo;
                END IF;
                IF v_destino = 'IMPROCEDENTE' THEN
                    v_t := pg_temp.paso(v_t, pg_temp.azar(2, 20));
                    PERFORM pg_temp.avance(v_caso, v_supervisor, 'DENUNCIAS', 'EN_PROCESO', 'IMPROCEDENTE',
                                           'La investigación no confirmó los hechos denunciados.', v_t);
                    v_estado := 'IMPROCEDENTE';
                    v_f_cierre := v_t;
                    EXIT flujo;
                END IF;

                v_plazo := CASE WHEN random() < 0.7 THEN pg_temp.azar(0.4, 0.98) ELSE pg_temp.azar(1.1, 3) END;
                v_t := GREATEST(pg_temp.paso(v_t, 0.5), pg_temp.paso(v_reg, v_h_res * v_plazo));
                v_solucion := 'Se concluyó la investigación y se informó al cliente la resolución.';
                PERFORM pg_temp.avance(v_caso, v_supervisor, 'DENUNCIAS', 'EN_PROCESO', 'RESUELTO', v_solucion, v_t);
                v_estado := 'RESUELTO';
                v_f_res := v_t;
                IF v_destino = 'CERRADO' THEN
                    v_t := pg_temp.paso(v_t, pg_temp.azar(4, 72));
                    PERFORM pg_temp.avance(v_caso, v_supervisor, 'DENUNCIAS', 'RESUELTO', 'CERRADO', 'Se da por concluida la denuncia.', v_t);
                    v_estado := 'CERRADO';
                    v_f_cierre := v_t;
                END IF;
                EXIT flujo;
            END IF;

            -- Queja y reclamo
            IF v_destino = 'EN_COLA' THEN
                EXIT flujo;
            END IF;

            v_abierta := v_destino IN ('ASIGNADO', 'EN_PROCESO', 'ESPERA_CLIENTE', 'RESUELTO');

            -- Tiempo en bolsa: casi siempre minutos, a veces horas
            r := random();
            v_t := pg_temp.paso(v_t, CASE WHEN r < 0.8 THEN pg_temp.azar(0.01, 0.4)
                                          WHEN r < 0.95 THEN pg_temp.azar(0.5, 4)
                                          ELSE pg_temp.azar(4, 24) END);

            -- Reasignacion: el primer tecnico libera la solicitud
            IF random() < 0.13 THEN
                v_anterior := pg_temp.tecnico_historico(v_especialidad, v_excluir);
                PERFORM pg_temp.asignar(v_caso, v_anterior, v_especialidad, v_peso_prio,
                                        EXTRACT(EPOCH FROM (v_t - v_reg)) / 3600, v_h_res, v_t);
                v_t := pg_temp.paso(v_t, pg_temp.azar(1, 10));
                UPDATE asignacion SET fecha_liberacion = v_t WHERE caso_id = v_caso AND tecnico_id = v_anterior;
                PERFORM pg_temp.seg(v_caso, v_anterior, 'ASIGNADO', 'EN_COLA',
                                    'Caso liberado: el técnico quedó no disponible, vuelve a la bolsa de demanda', TRUE, v_t);
                PERFORM pg_temp.evento(v_anterior, 'CASOS', 'LIBERAR', v_caso,
                                    'Caso ' || v_caso || ' liberado por no disponibilidad del técnico ' || v_anterior,
                                    jsonb_build_object('tecnico_id', v_anterior), v_t);
                v_excluir := v_excluir || v_anterior;
                v_t := pg_temp.paso(v_t, pg_temp.azar(0.1, 2));
            END IF;

            IF v_abierta THEN
                v_tecnico := pg_temp.tecnico_con_cupo(v_especialidad, v_excluir);
                IF v_tecnico IS NULL THEN
                    -- Nadie tiene cupo: se queda en la bolsa
                    EXIT flujo;
                END IF;
            ELSE
                v_tecnico := pg_temp.tecnico_historico(v_especialidad, v_excluir);
            END IF;

            PERFORM pg_temp.asignar(v_caso, v_tecnico, v_especialidad, v_peso_prio,
                                    EXTRACT(EPOCH FROM (v_t - v_reg)) / 3600, v_h_res, v_t);
            v_estado := 'ASIGNADO';
            v_f_asig := v_t;
            IF v_destino = 'ASIGNADO' THEN
                EXIT flujo;
            END IF;

            -- Primera respuesta del tecnico
            v_plazo := CASE WHEN random() < 0.82 THEN pg_temp.azar(0.2, 0.95) ELSE pg_temp.azar(1.1, 2.5) END;
            v_t := GREATEST(pg_temp.paso(v_t, 0.1), pg_temp.paso(v_reg, v_h_resp * v_plazo));

            IF v_destino = 'IMPROCEDENTE' AND random() < 0.4 THEN
                PERFORM pg_temp.avance(v_caso, v_tecnico, 'CASOS', 'ASIGNADO', 'IMPROCEDENTE',
                                       'La solicitud no corresponde a un servicio contratado.', v_t);
                v_estado := 'IMPROCEDENTE';
                v_f_cierre := v_t;
                EXIT flujo;
            END IF;

            PERFORM pg_temp.avance(v_caso, v_tecnico, 'CASOS', 'ASIGNADO', 'EN_PROCESO',
                                   'Se revisó el reporte y se inició el diagnóstico.', v_t);
            v_estado := 'EN_PROCESO';

            IF random() < 0.35 THEN
                v_t := pg_temp.paso(v_t, pg_temp.azar(0.5, 6));
                PERFORM pg_temp.seg(v_caso, v_tecnico, 'EN_PROCESO', NULL,
                                    'Se realizaron pruebas y se documentó el avance.', FALSE, v_t);
                PERFORM pg_temp.evento(v_tecnico, 'CASOS', 'REGISTRAR_AVANCE', v_caso,
                                    'Avance del caso ' || v_caso || ' sin cambio de estado',
                                    jsonb_build_object('estadoAnterior', 'EN_PROCESO', 'estadoNuevo', 'EN_PROCESO'), v_t);
            END IF;

            -- Espera al cliente, como destino o de paso
            IF v_destino = 'ESPERA_CLIENTE' OR random() < 0.08 THEN
                v_t := pg_temp.paso(v_t, pg_temp.azar(0.5, 8));
                PERFORM pg_temp.avance(v_caso, v_tecnico, 'CASOS', 'EN_PROCESO', 'ESPERA_CLIENTE',
                                       'Se solicitó información al cliente para continuar.', v_t);
                v_estado := 'ESPERA_CLIENTE';
                IF v_destino = 'ESPERA_CLIENTE' THEN
                    EXIT flujo;
                END IF;
                v_t := pg_temp.paso(v_t, pg_temp.azar(1, 12));
                PERFORM pg_temp.avance(v_caso, v_tecnico, 'CASOS', 'ESPERA_CLIENTE', 'EN_PROCESO',
                                       'El cliente envió la información solicitada.', v_t);
                v_estado := 'EN_PROCESO';
            END IF;

            -- Reabierta que sigue en proceso: conserva la resolucion anterior
            IF v_destino = 'EN_PROCESO' AND random() < 0.2 THEN
                v_t := pg_temp.paso(v_t, pg_temp.azar(1, 8));
                v_f_res := v_t;
                v_solucion := 'Se aplicó el ajuste y se confirmó el funcionamiento.';
                PERFORM pg_temp.avance(v_caso, v_tecnico, 'CASOS', 'EN_PROCESO', 'RESUELTO', v_solucion, v_t);
                v_t := pg_temp.paso(v_t, pg_temp.azar(1, 8));
                PERFORM pg_temp.avance(v_caso, v_tecnico, 'CASOS', 'RESUELTO', 'EN_PROCESO',
                                       'El cliente reportó que la falla regresó.', v_t);
                EXIT flujo;
            END IF;

            -- Escalamiento
            IF v_destino = 'ESCALADO' OR (v_destino NOT IN ('EN_PROCESO') AND random() < 0.16) THEN
                v_t := pg_temp.paso(v_t, pg_temp.azar(1, 10));
                PERFORM pg_temp.avance(v_caso, v_tecnico, 'CASOS', 'EN_PROCESO', 'ESCALADO',
                                       pg_temp.elegir(ARRAY['La falla excede lo que se puede resolver en sitio.',
                                                            'Se requiere autorización para reemplazar el equipo.',
                                                            'El cliente pide hablar con un responsable.']), v_t);
                v_estado := 'ESCALADO';
                v_anterior := v_tecnico;
                v_tecnico := NULL;
                v_f_cierre := v_t;
                IF v_destino = 'ESCALADO' THEN
                    EXIT flujo;
                END IF;

                r := random();
                v_t := pg_temp.paso(v_t, pg_temp.azar(1, 12));
                IF r < 0.1 THEN
                    PERFORM pg_temp.avance(v_caso, v_supervisor, 'ESCALAMIENTOS', 'ESCALADO', 'IMPROCEDENTE',
                                           'Revisado el caso, no procede atenderlo.', v_t);
                    v_estado := 'IMPROCEDENTE';
                    EXIT flujo;
                ELSIF r < 0.55 OR v_abierta THEN
                    -- El supervisor la toma: queda como responsable
                    v_responsable := v_supervisor;
                    v_f_cierre := NULL;
                    PERFORM pg_temp.seg(v_caso, v_supervisor, 'ESCALADO', 'EN_PROCESO',
                                        'El supervisor toma la atención de la solicitud.', FALSE, v_t);
                    PERFORM pg_temp.evento(v_supervisor, 'ESCALAMIENTOS', 'ATENDER', v_caso,
                                        'El supervisor toma la atención de la solicitud ' || v_boleta,
                                        jsonb_build_object('estadoAnterior', 'ESCALADO', 'estadoNuevo', 'EN_PROCESO'), v_t);
                    v_estado := 'EN_PROCESO';
                ELSE
                    -- Vuelve a la bolsa y el que la escalo queda excluido
                    INSERT INTO caso_involucrado (caso_id, usuario_id, motivo)
                    VALUES (v_caso, v_anterior, 'Escaló la solicitud')
                    ON CONFLICT (caso_id, usuario_id) DO NOTHING;
                    PERFORM pg_temp.seg(v_caso, v_supervisor, 'ESCALADO', 'EN_COLA',
                                        'Se devuelve a la bolsa para que la atienda otro técnico.', FALSE, v_t);
                    PERFORM pg_temp.evento(v_supervisor, 'ESCALAMIENTOS', 'DEVOLVER_BOLSA', v_caso,
                                        'Solicitud ' || v_caso || ' devuelta a la bolsa de demanda',
                                        jsonb_build_object('tecnico_excluido', v_anterior), v_t);
                    v_excluir := v_excluir || v_anterior;
                    v_f_cierre := NULL;
                    v_t := pg_temp.paso(v_t, pg_temp.azar(0.1, 2));
                    v_tecnico := pg_temp.tecnico_historico(v_especialidad, v_excluir);
                    PERFORM pg_temp.asignar(v_caso, v_tecnico, v_especialidad, v_peso_prio,
                                            EXTRACT(EPOCH FROM (v_t - v_reg)) / 3600, v_h_res, v_t);
                    v_f_asig := v_t;
                    v_t := pg_temp.paso(v_t, pg_temp.azar(0.5, 6));
                    PERFORM pg_temp.avance(v_caso, v_tecnico, 'CASOS', 'ASIGNADO', 'EN_PROCESO',
                                           'Se retoma la solicitud devuelta por el supervisor.', v_t);
                    v_estado := 'EN_PROCESO';
                END IF;
            END IF;

            IF v_destino = 'EN_PROCESO' THEN
                EXIT flujo;
            END IF;

            v_modulo := CASE WHEN v_responsable IS NULL THEN 'CASOS' ELSE 'ESCALAMIENTOS' END;
            IF v_destino IN ('IMPROCEDENTE', 'DUPLICADO') THEN
                v_t := pg_temp.paso(v_t, pg_temp.azar(1, 24));
                IF v_destino = 'DUPLICADO' THEN
                    SELECT c.id INTO v_relacionado
                    FROM caso c
                    WHERE c.cliente_id = v_cliente AND c.fecha_registro < v_reg AND c.id <> v_caso
                    ORDER BY c.fecha_registro DESC LIMIT 1;
                END IF;
                PERFORM pg_temp.avance(v_caso, COALESCE(v_tecnico, v_supervisor), v_modulo, 'EN_PROCESO', v_destino,
                                       CASE v_destino WHEN 'DUPLICADO' THEN 'Corresponde a una solicitud ya registrada.'
                                                      ELSE 'La solicitud no corresponde a un servicio contratado.' END, v_t);
                v_estado := v_destino;
                v_f_cierre := v_t;
                EXIT flujo;
            END IF;

            -- Resolucion: dentro o fuera del SLA, y va mejorando con el tiempo
            v_plazo := CASE WHEN random() < 0.68 + 0.17 * i / c_total THEN pg_temp.azar(0.25, 0.97)
                            ELSE pg_temp.azar(1.05, 2.6) END;
            v_t := GREATEST(pg_temp.paso(v_t, 0.2), pg_temp.paso(v_reg, v_h_res * v_plazo));
            v_solucion := pg_temp.elegir(ARRAY['Se reemplazó el componente dañado y se probó el servicio.',
                                               'Se ajustó la configuración y el servicio quedó estable.',
                                               'Se corrigió la falla y el cliente confirmó el funcionamiento.']);
            PERFORM pg_temp.avance(v_caso, COALESCE(v_tecnico, v_supervisor), v_modulo, 'EN_PROCESO', 'RESUELTO', v_solucion, v_t);
            v_estado := 'RESUELTO';
            v_f_res := v_t;

            -- Reabierta y vuelta a resolver
            IF random() < 0.06 THEN
                v_t := pg_temp.paso(v_t, pg_temp.azar(2, 24));
                PERFORM pg_temp.avance(v_caso, COALESCE(v_tecnico, v_supervisor), v_modulo, 'RESUELTO', 'EN_PROCESO',
                                       'El cliente reportó que la falla regresó.', v_t);
                v_t := pg_temp.paso(v_t, pg_temp.azar(2, 24));
                PERFORM pg_temp.avance(v_caso, COALESCE(v_tecnico, v_supervisor), v_modulo, 'EN_PROCESO', 'RESUELTO', v_solucion, v_t);
                v_f_res := v_t;
            END IF;

            IF v_destino = 'CERRADO' THEN
                v_t := pg_temp.paso(v_t, pg_temp.azar(2, 48));
                PERFORM pg_temp.avance(v_caso, COALESCE(v_tecnico, v_supervisor), v_modulo, 'RESUELTO', 'CERRADO',
                                       'El cliente aceptó la solución.', v_t);
                v_estado := 'CERRADO';
                v_f_cierre := v_t;
            END IF;
        END flujo;

        IF random() < 0.2 AND v_tipo <> 'SUGERENCIA' THEN
            PERFORM pg_temp.evento(v_supervisor, 'SUPERVISOR', 'CONSULTAR_DETALLE', v_caso,
                                'Consulta del detalle de la solicitud ' || v_boleta, NULL, pg_temp.paso(v_t, 1));
        END IF;

        UPDATE caso
           SET estado_id                 = (SELECT id FROM estado_caso WHERE codigo = v_estado),
               tecnico_asignado_id       = v_tecnico,
               supervisor_responsable_id = v_responsable,
               caso_relacionado_id       = v_relacionado,
               fecha_asignacion          = v_f_asig,
               fecha_resolucion          = v_f_res,
               fecha_cierre              = v_f_cierre,
               solucion                  = v_solucion
         WHERE id = v_caso;
    END LOOP;
END;
$$;

-- =====================================================================
-- 5. Cierre: secuencia de boletas, triggers de vuelta y cadena verificada
-- =====================================================================
-- Ignora el rango de demostracion y respeta cualquier boleta real mayor a 400
SELECT setval('seq_boleta',
              GREATEST(400,
                       COALESCE((SELECT MAX(split_part(numero_boleta, '-', 3)::BIGINT)
                                 FROM caso
                                 WHERE numero_boleta ~ '^SOL-[0-9]{4}-[0-9]+$'
                                   AND split_part(numero_boleta, '-', 3)::BIGINT < 900000), 0)));

ALTER TABLE caso ENABLE TRIGGER USER;

DO $$
DECLARE
    v_estado VARCHAR;
BEGIN
    SELECT estado INTO v_estado FROM fn_verificar_integridad_bitacora();
    IF v_estado <> 'INTEGRA' THEN
        RAISE EXCEPTION 'La bitacora no quedo integra (%): se revierte la carga', v_estado;
    END IF;
END;
$$;

COMMIT;
