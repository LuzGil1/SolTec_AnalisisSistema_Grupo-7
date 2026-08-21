-- =====================================================================
-- SolTec Servicios Tecnológicos, S.A.
-- Sistema de Gestión de Quejas, Reclamos, Denuncias y Sugerencias
-- Grupo 7
--
-- 01_esquema.sql — Estructura de tablas
-- Motor: PostgreSQL 15+
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS soltec;
SET search_path TO soltec, public;

-- =====================================================================
-- 1. SEGURIDAD Y USUARIOS
-- =====================================================================

CREATE TABLE rol (
    id              SERIAL PRIMARY KEY,
    codigo          VARCHAR(20)  NOT NULL UNIQUE,
    nombre          VARCHAR(50)  NOT NULL,
    descripcion     VARCHAR(200),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE
);
COMMENT ON TABLE rol IS 'CU-09: catálogo de roles del sistema';

CREATE TABLE usuario (
    id                  SERIAL PRIMARY KEY,
    rol_id              INTEGER      NOT NULL REFERENCES rol(id),
    nombres             VARCHAR(100) NOT NULL,
    apellidos           VARCHAR(100) NOT NULL,
    correo              VARCHAR(150) NOT NULL UNIQUE,
    contrasena_hash     VARCHAR(255) NOT NULL,
    telefono            VARCHAR(20),
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_usuario_correo CHECK (correo ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);
COMMENT ON CONSTRAINT ck_usuario_correo ON usuario IS 'RN01 del CU-00: formato usuario@dominio';

CREATE INDEX ix_usuario_rol ON usuario(rol_id);

-- Datos adicionales del Usuario Externo (CU-02)
CREATE TABLE cliente (
    usuario_id      INTEGER PRIMARY KEY REFERENCES usuario(id),
    nit             VARCHAR(20),
    direccion       VARCHAR(250)
);

CREATE TABLE especialidad (
    id              SERIAL PRIMARY KEY,
    nombre          VARCHAR(80) NOT NULL UNIQUE,
    activo          BOOLEAN     NOT NULL DEFAULT TRUE
);

-- Datos adicionales del Técnico (CU-04: capacidad y disponibilidad)
CREATE TABLE tecnico (
    usuario_id          INTEGER PRIMARY KEY REFERENCES usuario(id),
    capacidad_maxima    SMALLINT NOT NULL DEFAULT 5,
    disponible          BOOLEAN  NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_tecnico_capacidad CHECK (capacidad_maxima > 0)
);

CREATE TABLE tecnico_especialidad (
    tecnico_id      INTEGER NOT NULL REFERENCES tecnico(usuario_id),
    especialidad_id INTEGER NOT NULL REFERENCES especialidad(id),
    PRIMARY KEY (tecnico_id, especialidad_id)
);

-- =====================================================================
-- 2. CATÁLOGOS DEL NEGOCIO
-- =====================================================================

CREATE TABLE servicio (
    id              SERIAL PRIMARY KEY,
    nombre          VARCHAR(120) NOT NULL UNIQUE,
    descripcion     VARCHAR(300),
    especialidad_id INTEGER REFERENCES especialidad(id),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE
);
COMMENT ON COLUMN servicio.especialidad_id IS 'Permite el match de especialidad en el score del CU-04';

CREATE TABLE prioridad (
    id              SERIAL PRIMARY KEY,
    codigo          VARCHAR(20)  NOT NULL UNIQUE,
    nombre          VARCHAR(50)  NOT NULL,
    peso            NUMERIC(5,2) NOT NULL,
    orden           SMALLINT     NOT NULL,
    CONSTRAINT ck_prioridad_peso CHECK (peso BETWEEN 0 AND 100)
);

CREATE TABLE estado_caso (
    id              SERIAL PRIMARY KEY,
    codigo          VARCHAR(25)  NOT NULL UNIQUE,
    nombre          VARCHAR(60)  NOT NULL,
    es_final        BOOLEAN      NOT NULL DEFAULT FALSE,
    orden           SMALLINT     NOT NULL
);

-- RN: Ruteo por tipo de solicitud
CREATE TABLE tipo_solicitud (
    id                      SERIAL PRIMARY KEY,
    codigo                  VARCHAR(20) NOT NULL UNIQUE,
    nombre                  VARCHAR(60) NOT NULL,
    ingresa_bolsa           BOOLEAN     NOT NULL DEFAULT TRUE,
    requiere_sla            BOOLEAN     NOT NULL DEFAULT TRUE,
    requiere_caso_previo    BOOLEAN     NOT NULL DEFAULT FALSE,
    solo_supervisor         BOOLEAN     NOT NULL DEFAULT FALSE,
    estado_inicial_id       INTEGER     NOT NULL REFERENCES estado_caso(id),
    prioridad_default_id    INTEGER     NOT NULL REFERENCES prioridad(id),
    activo                  BOOLEAN     NOT NULL DEFAULT TRUE
);
COMMENT ON TABLE tipo_solicitud IS
  'Las 4 banderas implementan el ruteo: queja/reclamo -> bolsa, denuncia -> supervisor, sugerencia -> backlog';

-- SLA por combinación tipo + prioridad
CREATE TABLE sla (
    id                  SERIAL PRIMARY KEY,
    tipo_solicitud_id   INTEGER  NOT NULL REFERENCES tipo_solicitud(id),
    prioridad_id        INTEGER  NOT NULL REFERENCES prioridad(id),
    horas_respuesta     SMALLINT NOT NULL,
    horas_resolucion    SMALLINT NOT NULL,
    CONSTRAINT uq_sla UNIQUE (tipo_solicitud_id, prioridad_id),
    CONSTRAINT ck_sla_horas CHECK (horas_respuesta > 0 AND horas_resolucion >= horas_respuesta)
);

-- =====================================================================
-- 3. CASOS
-- =====================================================================

CREATE SEQUENCE seq_boleta START 1;

CREATE TABLE caso (
    id                      SERIAL PRIMARY KEY,
    numero_boleta           VARCHAR(20)  NOT NULL UNIQUE,
    cliente_id              INTEGER      NOT NULL REFERENCES cliente(usuario_id),
    tipo_solicitud_id       INTEGER      NOT NULL REFERENCES tipo_solicitud(id),
    servicio_id             INTEGER      REFERENCES servicio(id),
    prioridad_id            INTEGER      NOT NULL REFERENCES prioridad(id),
    estado_id               INTEGER      NOT NULL REFERENCES estado_caso(id),
    caso_relacionado_id     INTEGER      REFERENCES caso(id),
    tecnico_asignado_id     INTEGER      REFERENCES tecnico(usuario_id),
    asunto                  VARCHAR(150) NOT NULL,
    descripcion             TEXT         NOT NULL,
    orden_servicio          VARCHAR(50),
    fecha_servicio          DATE,
    fecha_registro          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    fecha_limite_respuesta  TIMESTAMPTZ,
    fecha_limite_resolucion TIMESTAMPTZ,
    fecha_asignacion        TIMESTAMPTZ,
    fecha_resolucion        TIMESTAMPTZ,
    fecha_cierre            TIMESTAMPTZ,
    solucion                TEXT,
    CONSTRAINT ck_caso_no_autorelacion CHECK (caso_relacionado_id IS NULL OR caso_relacionado_id <> id)
);

CREATE INDEX ix_caso_estado      ON caso(estado_id);
CREATE INDEX ix_caso_cliente     ON caso(cliente_id);
CREATE INDEX ix_caso_tecnico     ON caso(tecnico_asignado_id);
CREATE INDEX ix_caso_fecha       ON caso(fecha_registro);
-- Índice parcial: acelera la consulta de la bolsa de demanda (CU-04)
CREATE INDEX ix_caso_bolsa ON caso(estado_id, fecha_registro)
    WHERE tecnico_asignado_id IS NULL;

-- RN: Conflicto de interés — personal señalado en una denuncia
CREATE TABLE caso_involucrado (
    id          SERIAL PRIMARY KEY,
    caso_id     INTEGER      NOT NULL REFERENCES caso(id),
    usuario_id  INTEGER      NOT NULL REFERENCES usuario(id),
    motivo      VARCHAR(200),
    CONSTRAINT uq_caso_involucrado UNIQUE (caso_id, usuario_id)
);
COMMENT ON TABLE caso_involucrado IS
  'Un técnico aquí registrado nunca podrá recibir ni visualizar este caso (CU-04)';

-- Historial de avances y cambios de estado (CU-05, CU-06)
CREATE TABLE seguimiento (
    id                  SERIAL PRIMARY KEY,
    caso_id             INTEGER     NOT NULL REFERENCES caso(id),
    usuario_id          INTEGER     NOT NULL REFERENCES usuario(id),
    estado_anterior_id  INTEGER     REFERENCES estado_caso(id),
    estado_nuevo_id     INTEGER     REFERENCES estado_caso(id),
    comentario          TEXT        NOT NULL,
    visible_cliente     BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_seguimiento_caso ON seguimiento(caso_id, fecha);

-- Evidencia: audio, fotografía, PDF u otro multimedia (CU-01)
CREATE TABLE adjunto (
    id              SERIAL PRIMARY KEY,
    caso_id         INTEGER      NOT NULL REFERENCES caso(id),
    seguimiento_id  INTEGER      REFERENCES seguimiento(id),
    nombre_archivo  VARCHAR(255) NOT NULL,
    tipo_mime       VARCHAR(100) NOT NULL,
    tamano_bytes    BIGINT       NOT NULL,
    ruta            VARCHAR(500) NOT NULL,
    hash_sha256     CHAR(64),
    usuario_id      INTEGER      NOT NULL REFERENCES usuario(id),
    fecha_carga     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_adjunto_tamano CHECK (tamano_bytes > 0)
);
CREATE INDEX ix_adjunto_caso ON adjunto(caso_id);

-- Historial de asignaciones (CU-04): deja rastro del score que ganó
CREATE TABLE asignacion (
    id                  SERIAL PRIMARY KEY,
    caso_id             INTEGER      NOT NULL REFERENCES caso(id),
    tecnico_id          INTEGER      NOT NULL REFERENCES tecnico(usuario_id),
    score               NUMERIC(8,3),
    motivo              VARCHAR(150) NOT NULL DEFAULT 'Asignación por demanda',
    fecha_asignacion    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    fecha_liberacion    TIMESTAMPTZ
);
CREATE INDEX ix_asignacion_caso    ON asignacion(caso_id);
CREATE INDEX ix_asignacion_tecnico ON asignacion(tecnico_id);

-- =====================================================================
-- 4. NOTIFICACIONES (CU-07)
-- =====================================================================

CREATE TABLE notificacion (
    id              SERIAL PRIMARY KEY,
    caso_id         INTEGER      REFERENCES caso(id),
    usuario_id      INTEGER      NOT NULL REFERENCES usuario(id),
    correo_destino  VARCHAR(150) NOT NULL,
    asunto          VARCHAR(150) NOT NULL,
    cuerpo          TEXT         NOT NULL,
    estado          VARCHAR(15)  NOT NULL DEFAULT 'PENDIENTE',
    fecha_creacion  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    fecha_envio     TIMESTAMPTZ,
    CONSTRAINT ck_notificacion_estado CHECK (estado IN ('PENDIENTE','ENVIADA','FALLIDA'))
);
CREATE INDEX ix_notificacion_pendiente ON notificacion(estado) WHERE estado = 'PENDIENTE';

-- =====================================================================
-- 5. PARÁMETROS DEL ALGORITMO (CU-09 configura, CU-04 consume)
-- =====================================================================

CREATE TABLE parametro (
    clave           VARCHAR(50) PRIMARY KEY,
    valor           NUMERIC(10,4) NOT NULL,
    descripcion     VARCHAR(250),
    fecha_modificacion TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE parametro IS
  'Los pesos del score son configurables: el algoritmo NO está quemado en el código';
