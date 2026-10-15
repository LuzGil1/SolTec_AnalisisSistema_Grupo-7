# Pendientes / hallazgos durante la generación de diagramas

Cosas que **no se inventaron** porque no se encontró evidencia clara en el
código o en la base, o que representan una discrepancia entre lo documentado
y lo que realmente corre. Se anotan aquí en vez de maquillarse en los
diagramas.

## 1. `fn_solicitar_siguiente_caso` está obsoleta, pero `db/CLAUDE.md` dice lo contrario
`db/CLAUDE.md` afirma que el algoritmo de asignación por demanda vive en la
función SQL `fn_solicitar_siguiente_caso`. Eso es información desactualizada:
`06_asignacion_automatica.sql` marca explícitamente esa función como OBSOLETA
y la sustituye por un modelo "push" (`fn_asignar_solicitud` + `fn_procesar_bolsa`
+ triggers `tg_caso_after_insert` / `tg_caso_after_update_liberar`), confirmado
por `AsignacionAutomaticaScheduler.java` y `CasoRepository.procesarBolsa()`.
Los diagramas 4, 5 y 6 usan `fn_asignar_solicitud` / `fn_procesar_bolsa` /
`fn_calcular_score` / `fn_score_solicitud`, que es lo que realmente se ejecuta.
Vale la pena actualizar ese comentario en `db/CLAUDE.md` para que no induzca a
error a quien lo lea después.

## 2. No existe una entidad/tabla "Escalamiento"
Un escalamiento es simplemente un `Caso` en estado `ESCALADO`, con
`caso.supervisor_responsable_id` y el último `seguimiento` que transicionó a
`ESCALADO`. `EscalamientoResumenDTO` / `EscalamientoProyeccion` son
proyecciones sobre la vista `vw_escalamientos`, no una entidad JPA. Por eso el
diagrama de clases (2) no incluye una clase `Escalamiento`: inventarla sería
mentir sobre el modelo real.

## 3. Estados sembrados en `estado_caso` que el código nunca usa
- `RECIBIDO` (id 1) y `ESPERA_CLIENTE` (id 7): no son el `estado_inicial_id`
  de ningún `tipo_solicitud`, y ningún trigger, función ni mapa `TRANSICIONES*`
  en Java los asigna. Son alcanzables solo por una actualización manual en la
  base.
- `DUPLICADO` (id 12, `es_final = true`): sembrado pero no referenciado en
  ningún mapa `TRANSICIONES` ni en SQL más allá de su propia fila. Estado
  final "muerto".

El diagrama de estados (4) los muestra en un recuadro punteado separado,
marcados como no usados, en vez de dibujarlos como parte del flujo normal.

## 4. Las relaciones de `Caso` (y otras entidades) hacia `Cliente`/`Tecnico`/`Usuario` no son navegables en Java
`Caso.clienteId`, `Caso.tecnicoAsignadoId`, `Caso.casoRelacionadoId`,
`Caso.supervisorResponsableId`, `OrdenServicio.tecnicoId`,
`Seguimiento.casoId`, `Adjunto.casoId`, `CasoInvolucrado.casoId/usuarioId`,
etc. son enteros crudos (`Integer`), no relaciones `@ManyToOne` de JPA. La
base de datos sí tiene las llaves foráneas reales (`01_esquema.sql`), pero el
grafo de objetos en Java no las navega. El diagrama de clases (2) las dibuja
igual (porque la cardinalidad real existe a nivel de BD) pero con línea
punteada y la leyenda "FK no navegable" para no sugerir que existe un getter
de objeto donde solo hay un ID.

## 5. Tablas `notificacion` y `asignacion` sin capa de aplicación
Ambas existen en `01_esquema.sql`, pero no hay entidad JPA, repositorio ni
servicio que las use desde el backend. `asignacion` solo se llena por un
INSERT crudo dentro de `fn_asignar_solicitud`. Por eso no aparecen como clases
en el diagrama 2: no tienen representación en el código Java.

## 6. `fn_verificar_integridad_bitacora` no se expone por REST
Existe en `02_bitacora.sql` (verificación de integridad de la bitácora para
auditoría), pero no se encontró ningún controlador o servicio del backend que
la invoque. Probablemente se ejecuta directamente contra la base por
operación/auditoría, fuera del flujo de los diagramas 5 y 6.

## 7. Puertos y rutas usados en el diagrama de despliegue (3)
Confirmados directamente en el código, no son suposición:
- Backend: `server.port=8081` (`backend/src/main/resources/application.properties`).
- Frontend: `environment.ts` / `environment.development.ts` apuntan a
  `http://localhost:8081`; el servidor Angular corre en el puerto por defecto
  de `ng serve` (4200), que no está sobreescrito en `angular.json`.
- Almacenamiento de adjuntos: `app.almacenamiento.directorio-local=uploads`,
  implementado por `AlmacenamientoLocalServiceImpl` (sistema de archivos
  local). El comentario del propio `application.properties` indica que en
  producción se usaría Azure Blob Storage, pero la implementación actual que
  corre es la local — el diagrama 3 refleja lo que corre hoy, no lo planeado.

## 8. `bitacora.usuario_id` no tiene llave foránea real
En `02_bitacora.sql` la columna se declara `usuario_id INTEGER` sin
`REFERENCES usuario(id)` — a diferencia de todas las demás columnas
`*_id` del esquema, que sí son FK reales. Es una referencia lógica, no
forzada por el motor (probablemente a propósito: la bitácora no debe
fallar ni perder el registro de un evento aunque el usuario asociado se
elimine más adelante). El diagrama 7 la dibuja con línea punteada y la
etiqueta "sin FK real" en vez de como una relación normal, para no
inventar una restricción que no existe.
