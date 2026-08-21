-- =====================================================================
-- 04_datos_iniciales.sql — Catálogos base y datos de prueba
-- =====================================================================

SET search_path TO soltec, public;

-- ---------------------------------------------------------------------
-- Roles
-- ---------------------------------------------------------------------
INSERT INTO rol (codigo, nombre, descripcion) VALUES
 ('CLIENTE',      'Usuario Externo',  'Registra y da seguimiento a sus propios casos'),
 ('TECNICO',      'Técnico de Soporte','Atiende los casos que le asigna el algoritmo'),
 ('SUPERVISOR',   'Supervisor',       'Atiende denuncias, escalamientos y consulta reportes'),
 ('ADMIN',        'Administrador',    'Gestiona usuarios, roles, catálogos y parámetros'),
 ('AUDITOR',      'Auditor',          'Consulta la bitácora en modo solo lectura');

-- ---------------------------------------------------------------------
-- Estados del caso
-- ---------------------------------------------------------------------
INSERT INTO estado_caso (codigo, nombre, es_final, orden) VALUES
 ('RECIBIDO',      'Recibido',                    FALSE, 1),
 ('EN_COLA',       'En cola de demanda',          FALSE, 2),
 ('EN_REVISION',   'En revisión del supervisor',  FALSE, 3),
 ('EN_BACKLOG',    'En backlog de mejoras',       FALSE, 4),
 ('ASIGNADO',      'Asignado',                    FALSE, 5),
 ('EN_PROCESO',    'En proceso',                  FALSE, 6),
 ('ESPERA_CLIENTE','Esperando al cliente',        FALSE, 7),
 ('ESCALADO',      'Escalado',                    FALSE, 8),
 ('RESUELTO',      'Resuelto',                    FALSE, 9),
 ('CERRADO',       'Cerrado',                     TRUE, 10),
 ('IMPROCEDENTE',  'Improcedente',                TRUE, 11),
 ('DUPLICADO',     'Duplicado',                   TRUE, 12);

-- ---------------------------------------------------------------------
-- Prioridades
-- ---------------------------------------------------------------------
INSERT INTO prioridad (codigo, nombre, peso, orden) VALUES
 ('CRITICA', 'Crítica', 100.00, 1),
 ('ALTA',    'Alta',     75.00, 2),
 ('MEDIA',   'Media',    50.00, 3),
 ('BAJA',    'Baja',     25.00, 4);

-- ---------------------------------------------------------------------
-- Tipos de solicitud — aquí vive el ruteo
-- ---------------------------------------------------------------------
INSERT INTO tipo_solicitud
 (codigo, nombre, ingresa_bolsa, requiere_sla, requiere_caso_previo, solo_supervisor,
  estado_inicial_id, prioridad_default_id)
VALUES
 ('QUEJA',      'Queja',      TRUE,  TRUE,  FALSE, FALSE,
   (SELECT id FROM estado_caso WHERE codigo='EN_COLA'),
   (SELECT id FROM prioridad   WHERE codigo='MEDIA')),

 ('RECLAMO',    'Reclamo',    TRUE,  TRUE,  TRUE,  FALSE,
   (SELECT id FROM estado_caso WHERE codigo='EN_COLA'),
   (SELECT id FROM prioridad   WHERE codigo='ALTA')),

 ('DENUNCIA',   'Denuncia',   FALSE, TRUE,  FALSE, TRUE,
   (SELECT id FROM estado_caso WHERE codigo='EN_REVISION'),
   (SELECT id FROM prioridad   WHERE codigo='CRITICA')),

 ('SUGERENCIA', 'Sugerencia', FALSE, FALSE, FALSE, FALSE,
   (SELECT id FROM estado_caso WHERE codigo='EN_BACKLOG'),
   (SELECT id FROM prioridad   WHERE codigo='BAJA'));

-- ---------------------------------------------------------------------
-- Especialidades y servicios de SolTec
-- ---------------------------------------------------------------------
INSERT INTO especialidad (nombre) VALUES
 ('Hardware'), ('Redes'), ('Software'), ('Soporte Remoto');

INSERT INTO servicio (nombre, descripcion, especialidad_id) VALUES
 ('Instalación y configuración de equipos', 'Montaje y puesta en marcha de equipo de cómputo',
   (SELECT id FROM especialidad WHERE nombre='Hardware')),
 ('Redes',                                  'Cableado, configuración de routers y switches',
   (SELECT id FROM especialidad WHERE nombre='Redes')),
 ('Mantenimiento preventivo',               'Limpieza y revisión programada',
   (SELECT id FROM especialidad WHERE nombre='Hardware')),
 ('Mantenimiento correctivo',               'Reparación de fallas reportadas',
   (SELECT id FROM especialidad WHERE nombre='Hardware')),
 ('Asistencia técnica remota',              'Soporte vía conexión remota',
   (SELECT id FROM especialidad WHERE nombre='Soporte Remoto')),
 ('Asistencia técnica presencial',          'Visita técnica en sitio',
   (SELECT id FROM especialidad WHERE nombre='Hardware'));

-- ---------------------------------------------------------------------
-- SLA por tipo y prioridad (en horas)
-- La sugerencia no lleva SLA
-- ---------------------------------------------------------------------
INSERT INTO sla (tipo_solicitud_id, prioridad_id, horas_respuesta, horas_resolucion)
SELECT t.id, p.id,
       CASE p.codigo WHEN 'CRITICA' THEN 2 WHEN 'ALTA' THEN 4
                     WHEN 'MEDIA'   THEN 8 ELSE 24 END,
       CASE p.codigo WHEN 'CRITICA' THEN 8 WHEN 'ALTA' THEN 24
                     WHEN 'MEDIA'   THEN 48 ELSE 72 END
FROM tipo_solicitud t
CROSS JOIN prioridad p
WHERE t.requiere_sla;

-- ---------------------------------------------------------------------
-- Parámetros del algoritmo (CU-04) — configurables desde el CU-09
-- ---------------------------------------------------------------------
INSERT INTO parametro (clave, valor, descripcion) VALUES
 ('PESO_PRIORIDAD',    0.3500, 'Peso de la prioridad del caso en el score'),
 ('PESO_ESPERA',       0.1500, 'Peso de la antigüedad en cola (evita casos olvidados)'),
 ('PESO_SLA',          0.3000, 'Peso de la urgencia frente al vencimiento del SLA'),
 ('PESO_ESPECIALIDAD', 0.2000, 'Peso de la coincidencia con la especialidad del técnico'),
 ('PESO_CARGA',        0.2500, 'Penalización por carga actual del técnico'),
 ('FACTOR_AGING',      2.0000, 'Puntos que gana un caso por cada hora en cola'),
 ('MAX_MB_ADJUNTO',   10.0000, 'Tamaño máximo por archivo de evidencia (CU-01)');

-- =====================================================================
-- DATOS DE PRUEBA
-- Contraseña de todos los usuarios: Soltec2026
-- Hash BCrypt de "Soltec2026", generado con pgcrypto (gen_salt('bf', 10)) y
-- verificado por round-trip; compatible con BCryptPasswordEncoder de Spring.
-- Es solo para datos de prueba: no usar en un ambiente real.
-- =====================================================================

INSERT INTO usuario (rol_id, nombres, apellidos, correo, contrasena_hash, telefono) VALUES
 ((SELECT id FROM rol WHERE codigo='ADMIN'),      'Ana',    'Morales',  'admin@soltec.com.gt',      '$2a$10$nFy7jJqTLJVBQQc3f3EPa.2GMbqqpoHsAWQPZDQutnbMpje5CYUd.', '55501010'),
 ((SELECT id FROM rol WHERE codigo='SUPERVISOR'), 'Carlos', 'Ramírez',  'supervisor@soltec.com.gt', '$2a$10$nFy7jJqTLJVBQQc3f3EPa.2GMbqqpoHsAWQPZDQutnbMpje5CYUd.', '55502020'),
 ((SELECT id FROM rol WHERE codigo='AUDITOR'),    'Lucía',  'Estrada',  'auditor@soltec.com.gt',    '$2a$10$nFy7jJqTLJVBQQc3f3EPa.2GMbqqpoHsAWQPZDQutnbMpje5CYUd.', '55503030'),
 ((SELECT id FROM rol WHERE codigo='TECNICO'),    'Jorge',  'Pérez',    'jperez@soltec.com.gt',     '$2a$10$nFy7jJqTLJVBQQc3f3EPa.2GMbqqpoHsAWQPZDQutnbMpje5CYUd.', '55504040'),
 ((SELECT id FROM rol WHERE codigo='TECNICO'),    'María',  'López',    'mlopez@soltec.com.gt',     '$2a$10$nFy7jJqTLJVBQQc3f3EPa.2GMbqqpoHsAWQPZDQutnbMpje5CYUd.', '55505050'),
 ((SELECT id FROM rol WHERE codigo='CLIENTE'),    'Pedro',  'Gómez',    'pgomez@empresa.com.gt',    '$2a$10$nFy7jJqTLJVBQQc3f3EPa.2GMbqqpoHsAWQPZDQutnbMpje5CYUd.', '55506060'),
 ((SELECT id FROM rol WHERE codigo='CLIENTE'),    'Sofía',  'Hernández','shernandez@empresa.com.gt','$2a$10$nFy7jJqTLJVBQQc3f3EPa.2GMbqqpoHsAWQPZDQutnbMpje5CYUd.', '55507070');

INSERT INTO tecnico (usuario_id, capacidad_maxima) VALUES
 ((SELECT id FROM usuario WHERE correo='jperez@soltec.com.gt'), 5),
 ((SELECT id FROM usuario WHERE correo='mlopez@soltec.com.gt'), 4);

INSERT INTO tecnico_especialidad (tecnico_id, especialidad_id) VALUES
 ((SELECT id FROM usuario WHERE correo='jperez@soltec.com.gt'), (SELECT id FROM especialidad WHERE nombre='Redes')),
 ((SELECT id FROM usuario WHERE correo='jperez@soltec.com.gt'), (SELECT id FROM especialidad WHERE nombre='Hardware')),
 ((SELECT id FROM usuario WHERE correo='mlopez@soltec.com.gt'), (SELECT id FROM especialidad WHERE nombre='Soporte Remoto'));

INSERT INTO cliente (usuario_id, nit, direccion) VALUES
 ((SELECT id FROM usuario WHERE correo='pgomez@empresa.com.gt'),     '1234567-8', 'Zona 10, Guatemala'),
 ((SELECT id FROM usuario WHERE correo='shernandez@empresa.com.gt'), '8765432-1', 'Zona 1, Guatemala');

-- Casos de prueba: el trigger asigna boleta, estado y SLA automáticamente
INSERT INTO caso (cliente_id, tipo_solicitud_id, servicio_id, prioridad_id, estado_id, asunto, descripcion)
VALUES
 ((SELECT id FROM usuario WHERE correo='pgomez@empresa.com.gt'),
  (SELECT id FROM tipo_solicitud WHERE codigo='QUEJA'),
  (SELECT id FROM servicio WHERE nombre='Redes'),
  (SELECT id FROM prioridad WHERE codigo='ALTA'), 1,
  'El técnico no se presentó a la cita',
  'Se agendó visita para el lunes a las 9:00 y nadie llegó ni avisó.'),

 ((SELECT id FROM usuario WHERE correo='shernandez@empresa.com.gt'),
  (SELECT id FROM tipo_solicitud WHERE codigo='SUGERENCIA'),
  NULL,
  (SELECT id FROM prioridad WHERE codigo='BAJA'), 1,
  'Ampliar horario de atención',
  'Sería de mucha ayuda contar con soporte los días sábado.');

-- Verificación rápida
-- SELECT * FROM vw_bolsa_demanda;
-- SELECT * FROM fn_solicitar_siguiente_caso(
--    (SELECT id FROM usuario WHERE correo='jperez@soltec.com.gt'), '127.0.0.1');
-- SELECT * FROM fn_verificar_integridad_bitacora();
