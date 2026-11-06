import { ConsultaDashboard } from '../../../../core/services/dashboard.service';

export interface DefinicionGrafica {
  id: string;
  titulo: string;
  forma: string;
  ancha?: boolean;
  vista: 'echarts' | 'variacion' | 'tabla' | 'linea_tiempo' | 'indicadores';
  consulta?: ConsultaDashboard;
  nota?: string;
}

export const NOTA_CARGA = 'La carga no depende del período: muestra todas las solicitudes abiertas actuales.';

function g(id: string, titulo: string, forma: string, consulta: Omit<ConsultaDashboard, 'forma'>, ancha = false): DefinicionGrafica {
  return { id, titulo, forma, ancha, vista: 'echarts', consulta: { ...consulta, forma } };
}

// Las 38 graficas. El gris debajo de cada titulo es su forma de datos.
export const GRAFICAS: DefinicionGrafica[] = [
  g('barras_h', 'Solicitudes por tipo', 'serie_simple', { metrica: 'cantidad', agrupacion: 'tipo' }),
  g('barras_v', 'Solicitudes por especialidad', 'serie_simple', { metrica: 'cantidad', agrupacion: 'especialidad' }),
  g('barras_agrupadas', 'Prioridad por tipo de solicitud', 'serie_multiple', { metrica: 'cantidad', agrupacion: 'tipo', agrupacionSecundaria: 'prioridad' }),
  g('barras_apiladas', 'Situación por especialidad', 'serie_multiple', { metrica: 'cantidad', agrupacion: 'especialidad', agrupacionSecundaria: 'situacion' }),
  g('barras_100', 'Composición de prioridades por servicio', 'serie_multiple', { metrica: 'cantidad', agrupacion: 'servicio', agrupacionSecundaria: 'prioridad' }),
  { ...g('lollipop', 'Carga actual por técnico', 'serie_simple', { metrica: 'carga', agrupacion: 'tecnico' }), nota: NOTA_CARGA },
  { id: 'radar', titulo: 'Desempeño por técnico', forma: 'tabla', vista: 'echarts', consulta: { forma: 'tabla' }, nota: NOTA_CARGA },
  g('pareto', 'Pareto de servicios', 'serie_simple', { metrica: 'cantidad', agrupacion: 'servicio' }),
  g('linea', 'Solicitudes por mes', 'temporal', { metrica: 'cantidad', agrupacion: 'mes' }),
  g('lineas_multiples', 'Tipos de solicitud por mes', 'serie_multiple', { metrica: 'cantidad', agrupacion: 'mes', agrupacionSecundaria: 'tipo' }, true),
  g('area', 'Solicitudes por semana', 'temporal', { metrica: 'cantidad', agrupacion: 'semana' }),
  g('area_apilada', 'Prioridades por mes', 'serie_multiple', { metrica: 'cantidad', agrupacion: 'mes', agrupacionSecundaria: 'prioridad' }),
  g('escalonada', 'Solicitudes abiertas acumuladas', 'temporal', { variante: 'abiertas_acumuladas' }),
  g('combinada', 'Recibidas y tiempo promedio por mes', 'temporal_doble', { metrica: 'cantidad', metricaSecundaria: 'resolucion', agrupacion: 'mes' }, true),
  g('cascada', 'Del total recibido al cierre', 'acumulado', {}),
  g('calendario', 'Solicitudes por día', 'temporal', { metrica: 'cantidad', agrupacion: 'dia' }, true),
  { id: 'sparklines', titulo: 'Sparklines de los indicadores', forma: 'temporal', vista: 'indicadores' },
  g('pastel', 'Solicitudes por prioridad', 'serie_simple', { metrica: 'cantidad', agrupacion: 'prioridad' }),
  g('dona', 'Distribución por situación', 'serie_simple', { metrica: 'cantidad', agrupacion: 'situacion' }),
  g('semidona', 'Tipos de solicitud', 'serie_simple', { metrica: 'cantidad', agrupacion: 'tipo' }),
  g('treemap', 'Servicio y especialidad', 'jerarquia', { metrica: 'cantidad', agrupacion: 'especialidad', agrupacionSecundaria: 'servicio' }),
  g('sunburst', 'Servicio, tipo y situación', 'jerarquia', { metrica: 'cantidad', agrupacion: 'servicio', agrupacionSecundaria: 'tipo', agrupacionTerciaria: 'situacion' }),
  g('waffle', 'Cien solicitudes por tipo', 'serie_simple', { metrica: 'cantidad', agrupacion: 'tipo' }),
  g('histograma', 'Horas hasta resolver', 'distribucion', { metrica: 'resolucion' }),
  g('boxplot', 'Tiempos de resolución por técnico', 'distribucion', { metrica: 'resolucion', agrupacion: 'tecnico' }),
  g('dispersion', 'Asignación contra resolución por cliente', 'puntos', { metrica: 'asignacion', metricaSecundaria: 'resolucion', agrupacion: 'cliente' }),
  { ...g('burbujas', 'Carga contra tiempo promedio por técnico', 'puntos', { metrica: 'carga', metricaSecundaria: 'resolucion', agrupacion: 'tecnico' }), nota: NOTA_CARGA },
  g('calor_dia_hora', 'Solicitudes por día y hora', 'serie_multiple', { metrica: 'cantidad', agrupacion: 'hora', agrupacionSecundaria: 'dia_semana' }, true),
  g('calor_especialidad_mes', 'Solicitudes por especialidad y mes', 'serie_multiple', { metrica: 'cantidad', agrupacion: 'mes', agrupacionSecundaria: 'especialidad' }, true),
  g('embudo', 'Avance de las solicitudes', 'serie_simple', { variante: 'embudo' }),
  g('sankey', 'Transiciones entre situaciones', 'flujo', {}, true),
  { id: 'linea_tiempo', titulo: 'Línea de tiempo de una solicitud', forma: 'eventos', ancha: true, vista: 'linea_tiempo' },
  g('arbol', 'Especialidades y sus técnicos', 'jerarquia', { metrica: 'cantidad', agrupacion: 'especialidad', agrupacionSecundaria: 'tecnico' }),
  { id: 'tarjetas_variacion', titulo: 'Variación contra el período anterior', forma: 'escalar', vista: 'variacion' },
  g('gauge', 'Cumplimiento de SLA', 'escalar', { metrica: 'sla' }),
  g('bullet', 'Resolución contra la meta', 'escalar', { metrica: 'porcentaje_resolucion' }),
  g('termometro', 'Termómetro de cumplimiento', 'escalar', { metrica: 'sla' }),
  { id: 'tabla_semaforo', titulo: 'Técnicos', forma: 'tabla', ancha: true, vista: 'tabla', consulta: { forma: 'tabla' }, nota: NOTA_CARGA },
];

export const SOLO_BARRAS = ['barras_h', 'barras_v', 'barras_agrupadas', 'barras_apiladas', 'barras_100', 'lollipop', 'histograma', 'tabla_semaforo'];

export const METRICAS_VARIACION = ['cantidad', 'resueltas', 'vencidas', 'escalamientos'];

export interface Indicador {
  metrica: string;
  titulo: string;
  color: string;
  unidad: string;
}

export const INDICADORES: Indicador[] = [
  { metrica: 'cantidad', titulo: 'Solicitudes', color: '#2E1065', unidad: '' },
  { metrica: 'resolucion', titulo: 'Resolución media', color: '#6D28D9', unidad: ' h' },
  { metrica: 'sla', titulo: 'Cumplimiento SLA', color: '#2E7D5B', unidad: ' %' },
  { metrica: 'vencidas', titulo: 'Vencidas', color: '#9A3412', unidad: '' },
];

export interface ReporteFrecuente {
  texto: string;
  metrica?: string;
  agrupacion?: string;
  tipo?: string;
}

export const FRECUENTES: ReporteFrecuente[] = [
  { texto: 'Personalizado' },
  { texto: 'Solicitudes por tipo', metrica: 'cantidad', agrupacion: 'tipo', tipo: 'barras_h' },
  { texto: 'Carga actual por técnico', metrica: 'carga', agrupacion: 'tecnico', tipo: 'barras_h' },
  { texto: 'Cumplimiento de SLA por prioridad', metrica: 'sla', agrupacion: 'prioridad', tipo: 'barras_v' },
  { texto: 'Resolución por especialidad', metrica: 'resolucion', agrupacion: 'especialidad', tipo: 'barras_v' },
  { texto: 'Distribución por situación', metrica: 'cantidad', agrupacion: 'situacion', tipo: 'pastel' },
  { texto: 'Evolución mensual', metrica: 'cantidad', agrupacion: 'mes', tipo: 'linea' },
  { texto: 'Escalamientos por técnico', metrica: 'escalamientos', agrupacion: 'tecnico', tipo: 'barras_h' },
  { texto: 'Vencidas por prioridad', metrica: 'vencidas', agrupacion: 'prioridad', tipo: 'barras_v' },
  { texto: 'Solicitudes por cliente', metrica: 'cantidad', agrupacion: 'cliente', tipo: 'barras_h' },
];

export interface CampoFiltro {
  id: string;
  titulo: string;
}

// Lo que el panel de configuracion puede ocultar: 13 listas y 9 banderas
export const CAMPOS_FILTRO: CampoFiltro[] = [
  { id: 'texto', titulo: 'Búsqueda por texto' },
  { id: 'desde', titulo: 'Desde' },
  { id: 'hasta', titulo: 'Hasta' },
  { id: 'modoFecha', titulo: 'Modo de fecha' },
  { id: 'tipo', titulo: 'Tipo' },
  { id: 'prioridad', titulo: 'Prioridad' },
  { id: 'situacion', titulo: 'Situación' },
  { id: 'servicioId', titulo: 'Servicio' },
  { id: 'especialidadId', titulo: 'Especialidad' },
  { id: 'tecnicoId', titulo: 'Técnico' },
  { id: 'clienteId', titulo: 'Cliente' },
  { id: 'supervisorId', titulo: 'Supervisor responsable' },
  { id: 'rolResponsable', titulo: 'En manos de' },
  { id: 'soloVencidas', titulo: 'Solo vencidas' },
  { id: 'soloDentroSla', titulo: 'Solo dentro de SLA' },
  { id: 'soloAbiertas', titulo: 'Solo abiertas' },
  { id: 'incumplioRespuesta', titulo: 'Incumplió el tiempo de respuesta' },
  { id: 'conPersonalSenalado', titulo: 'Con personal señalado' },
  { id: 'conTecnicoExcluido', titulo: 'Con técnico excluido por escalar' },
  { id: 'conAdjuntos', titulo: 'Con adjuntos' },
  { id: 'conOrdenServicio', titulo: 'Con orden de servicio' },
  { id: 'duplicadasRelacionadas', titulo: 'Duplicadas o relacionadas' },
];

export const BANDERAS = CAMPOS_FILTRO.slice(13);
