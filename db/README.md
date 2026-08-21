# Base de datos — Sistema de Quejas SolTec (Grupo 7)

PostgreSQL 15+. Los cuatro scripts se ejecutan **en orden**.

```bash
createdb soltec_db
psql -d soltec_db -f 01_esquema.sql
psql -d soltec_db -f 02_bitacora.sql
psql -d soltec_db -f 03_reglas_y_algoritmo.sql
psql -d soltec_db -f 04_datos_iniciales.sql
```

Probado contra PostgreSQL 16: los cuatro corren limpios desde cero, **19 tablas** creadas.

> **Versión 2** — incorpora la depuración del equipo: se eliminaron `usuario.fecha_ultimo_acceso`,
> el bloqueo por intentos fallidos, `cliente.empresa`, `cliente.contrato`, `notificacion.intentos`
> y la tabla `plantilla_notificacion` (las plantillas van en el código).

---

## Qué hay en cada script

| Script | Contenido |
|---|---|
| `01_esquema.sql` | 19 tablas: seguridad, catálogos, casos, adjuntos, notificaciones, parámetros |
| `02_bitacora.sql` | Bitácora inalterable con cadena de hash + verificación de integridad |
| `03_reglas_y_algoritmo.sql` | Ruteo por tipo, numeración de boleta, SLA y el algoritmo por demanda |
| `04_datos_iniciales.sql` | Catálogos, parámetros del algoritmo y datos de prueba |

---

## Qué se probó y qué resultado dio

**Ruteo por tipo de solicitud.** Al insertar un caso, el trigger lo manda solo:

| Tipo | Estado inicial | SLA |
|---|---|---|
| Queja | En cola de demanda | sí |
| Reclamo | En cola de demanda | sí |
| Denuncia | En revisión del supervisor | sí |
| Sugerencia | En backlog de mejoras | **no** |

**Reclamo referenciado.** Insertar un reclamo sin `caso_relacionado_id` devuelve:
`ERROR: Un Reclamo debe referenciar el caso previo relacionado`

**Conflicto de interés.** Se registró una denuncia contra el técnico Jorge Pérez. La denuncia nunca aparece en la bolsa (no ingresa por tipo), y aunque apareciera, `fn_solicitar_siguiente_caso` la excluye para él.

**Efecto de la especialidad en el score.** El mismo caso puntúa distinto según quién lo pida:

| Caso | Servicio | Score para Jorge (Redes/Hardware) | Score para María (Remoto) |
|---|---|---|---|
| Técnico no se presentó | Redes | **46.27** | 26.27 |
| Sesión remota se cortó | Asistencia remota | 17.50 | **37.50** |

Cada uno recibió el caso que le correspondía. Esto es lo que hay que enseñar en la defensa.

**Bitácora.** `UPDATE` y `DELETE` son rechazados por trigger. Al forzar una alteración desactivando el trigger (simulando acceso directo al motor), `fn_verificar_integridad_bitacora()` devuelve:

```
registro_id | estado   | detalle
          1 | ALTERADO | El contenido no corresponde al hash almacenado
```

---

## Notas para el equipo de Spring Boot

**El esquema es `soltec`, no `public`.** Si no lo configuran, Hibernate no encuentra nada:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/soltec_db?currentSchema=soltec
spring.jpa.properties.hibernate.default_schema=soltec
spring.jpa.hibernate.ddl-auto=validate
```

**`ddl-auto=validate`, nunca `update`.** Si dejan que Hibernate genere las tablas, se pierden los triggers, y con ellos el ruteo, la numeración de boleta y toda la bitácora.

**Los hashes de prueba están en blanco.** Reemplacen `$2a$10$REEMPLAZAR_POR_HASH_BCRYPT` por lo que genere `BCryptPasswordEncoder`. Contraseña sugerida para las pruebas: `Soltec2026`.

**Llamar el algoritmo desde el repositorio:**

```java
@Query(value = "SELECT * FROM soltec.fn_solicitar_siguiente_caso(:tecnicoId, :ip)",
       nativeQuery = true)
Optional<CasoAsignadoView> solicitarSiguiente(@Param("tecnicoId") Integer tecnicoId,
                                              @Param("ip") String ip);
```

La función lanza excepción cuando la bolsa está vacía, el técnico llegó a su tope o está marcado como no disponible. Cápturenlas en un `@ControllerAdvice` y devuélvanlas como respuestas HTTP con mensaje, no como error 500.

**Permisos de la bitácora.** Al final de `02_bitacora.sql` están comentadas las sentencias para crear el rol de aplicación y revocarle `UPDATE`/`DELETE`. Ejecútenlas antes de la entrega: es la mitad del argumento de "impedir modificación de bitácoras".

**Concurrencia.** `fn_solicitar_siguiente_caso` usa `FOR UPDATE SKIP LOCKED`, así que dos técnicos que presionen el botón al mismo tiempo reciben casos distintos. Vale la pena mencionarlo en la documentación.

---

## Ajustar el algoritmo sin tocar código

```sql
UPDATE soltec.parametro SET valor = 0.45 WHERE clave = 'PESO_SLA';
```

| Parámetro | Default | Qué controla |
|---|---|---|
| `PESO_PRIORIDAD` | 0.35 | Cuánto pesa la prioridad del caso |
| `PESO_ESPERA` | 0.15 | Cuánto pesa la antigüedad en cola |
| `PESO_SLA` | 0.30 | Cuánto pesa la cercanía al vencimiento |
| `PESO_ESPECIALIDAD` | 0.20 | Cuánto pesa el match con el técnico |
| `PESO_CARGA` | 0.25 | Penalización por carga del técnico |
| `FACTOR_AGING` | 2.0 | Puntos por hora en cola |

Subir `PESO_ESPERA` o `FACTOR_AGING` hace que ningún caso quede olvidado; subir `PESO_SLA` prioriza no incumplir los tiempos comprometidos.

---

## Consultas para la demostración

```sql
SET search_path TO soltec, public;

SELECT * FROM vw_bolsa_demanda;
SELECT * FROM fn_solicitar_siguiente_caso(4, '192.168.1.20');
SELECT * FROM vw_indicadores;
SELECT * FROM fn_verificar_integridad_bitacora();
```
