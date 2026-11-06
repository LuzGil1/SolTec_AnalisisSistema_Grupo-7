package com.example.soltec.service;

import com.example.soltec.exception.SolicitudInvalidaException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Lista blanca del motor. Lo que manda el cliente solo se usa como clave para
// buscar aqui una expresion SQL fija: nunca llega al SQL como texto.
public final class DashboardCatalogo {

    public record Metrica(String clave, String nombre, String corto, String unidad, int decimales,
                          boolean aditiva, boolean conteo, String sql, String sqlBitacora) {
    }

    public record Agrupacion(String clave, String nombre, String sql, String orden, boolean temporal) {
    }

    public record TipoGrafica(String clave, String nombre, String forma, String familia,
                              boolean requiereTiempo, boolean soloAditiva, boolean requiereSecundaria,
                              boolean explorador) {
    }

    public static final Map<String, Metrica> METRICAS = new LinkedHashMap<>();
    public static final Map<String, Agrupacion> AGRUPACIONES = new LinkedHashMap<>();
    public static final Map<String, String> PERIODOS = new LinkedHashMap<>();
    public static final Map<String, String> COLUMNAS_DISTRIBUCION = new LinkedHashMap<>();
    public static final List<TipoGrafica> TIPOS_GRAFICA = new ArrayList<>();

    public static final List<String> FORMAS = List.of("serie_simple", "serie_multiple", "temporal", "temporal_doble",
            "distribucion", "puntos", "jerarquia", "flujo", "acumulado", "escalar", "eventos", "tabla");

    public static final Set<String> VARIANTES = Set.of("abiertas_acumuladas", "embudo");

    public static final List<String> ROLES = List.of("Sin asignar", "Técnico", "Cliente", "Supervisor", "Ya cerrada");

    // Unica agrupacion que no sale de la vista: une la vista con bitacora
    public static final String ACCION_BITACORA = "accion_bitacora";

    public static final Set<String> AGRUPACIONES_CARGA = Set.of("tecnico", "especialidad");

    // Los valores viven en la tabla parametro; aqui solo esta que clave usa cada metrica
    public static final Map<String, String> PARAMETROS_META = Map.of(
            "sla", "META_SLA",
            "porcentaje_resolucion", "META_RESOLUCION");

    // Muestran la situacion de hoy: ignoran el rango de fechas, no los demas filtros
    public static final Set<String> METRICAS_SIN_PERIODO = Set.of("carga");

    private static final String DISTINTOS = "COUNT(DISTINCT v.caso_id) FILTER (WHERE ";

    static {
        metrica("cantidad", "Cantidad de solicitudes", "Cantidad", "", 0, true, true,
                "COUNT(*)", "COUNT(*)");
        metrica("resueltas", "Solicitudes resueltas", "Resueltas", "", 0, true, true,
                "COUNT(*) FILTER (WHERE v.resuelta)", DISTINTOS + "v.resuelta)");
        metrica("porcentaje_resolucion", "Porcentaje de resolución", "Resolución", " %", 1, false, false,
                "ROUND(100.0 * COUNT(*) FILTER (WHERE v.resuelta) / NULLIF(COUNT(*), 0), 1)", null);
        metrica("asignacion", "Tiempo promedio de asignación", "Asignación", " h", 1, false, false,
                "ROUND(AVG(v.horas_asignacion), 1)", null);
        metrica("resolucion", "Tiempo promedio de resolución", "Horas", " h", 1, false, false,
                "ROUND(AVG(v.horas_resolucion), 1)", null);
        metrica("sla", "Cumplimiento de SLA", "SLA", " %", 1, false, false,
                "ROUND(100.0 * COUNT(*) FILTER (WHERE v.cumplio_sla) / NULLIF(COUNT(v.cumplio_sla), 0), 1)", null);
        metrica("escalamientos", "Cantidad de escalamientos", "Escalamientos", "", 0, true, true,
                "COALESCE(SUM(v.escalamientos), 0)", DISTINTOS + "v.escalamientos > 0)");
        metrica("reasignaciones", "Cantidad de reasignaciones", "Reasignaciones", "", 0, true, true,
                "COALESCE(SUM(v.reasignaciones), 0)", DISTINTOS + "v.reasignaciones > 0)");
        metrica("vencidas", "Solicitudes vencidas", "Vencidas", "", 0, true, true,
                "COUNT(*) FILTER (WHERE v.vencida)", DISTINTOS + "v.vencida)");
        metrica("carga", "Carga actual", "Carga", "", 0, true, false,
                "COUNT(*) FILTER (WHERE v.abierta AND v.tecnico_id IS NOT NULL)", null);
        metrica("score", "Score promedio de asignación", "Score", "", 2, false, false,
                "ROUND(AVG(v.score_asignacion), 2)", null);

        agrupacion("tipo", "Tipo", "v.tipo", "v.tipo_solicitud_id", false);
        agrupacion("prioridad", "Prioridad", "v.prioridad", "v.prioridad_orden", false);
        agrupacion("situacion", "Situación", "v.situacion", "v.situacion_orden", false);
        agrupacion("tecnico", "Técnico", "COALESCE(v.tecnico, 'Sin técnico')", null, false);
        agrupacion("especialidad", "Especialidad", "COALESCE(v.especialidad, 'Sin especialidad')", null, false);
        agrupacion("cliente", "Cliente", "v.cliente", null, false);
        agrupacion("servicio", "Servicio", "COALESCE(v.servicio, 'Sin servicio')", null, false);
        agrupacion("rol_responsable", "En manos de", "v.rol_responsable", null, false);
        agrupacion("dia", "Día", "v.dia::TEXT", "v.dia", true);
        agrupacion("dia_semana", "Día de la semana", "v.dia_semana", "v.dia_semana_num", false);
        agrupacion("hora", "Hora", "LPAD(v.hora::TEXT, 2, '0')", "v.hora", false);
        agrupacion("semana", "Semana", "v.semana", "v.semana", true);
        agrupacion("mes", "Mes", "v.mes", "v.mes", true);
        agrupacion(ACCION_BITACORA, "Acción en bitácora", "b.accion", null, false);

        PERIODOS.put("ultimos_7_dias", "Últimos 7 días");
        PERIODOS.put("ultimos_30_dias", "Últimos 30 días");
        PERIODOS.put("ultimos_3_meses", "Últimos 3 meses");
        PERIODOS.put("este_anio", "Este año");
        PERIODOS.put("ultimos_12_meses", "Últimos 12 meses");
        PERIODOS.put("todo", "Todo el historial");

        COLUMNAS_DISTRIBUCION.put("resolucion", "v.horas_resolucion");
        COLUMNAS_DISTRIBUCION.put("asignacion", "v.horas_asignacion");
        COLUMNAS_DISTRIBUCION.put("score", "v.score_asignacion");

        // clave, nombre, forma, familia, tiempo, aditiva, secundaria, explorador
        tipo("barras_v", "Barras verticales", "serie_simple", "barras", false, false, false, true);
        tipo("barras_h", "Barras horizontales", "serie_simple", "barras", false, false, false, true);
        tipo("barras_agrupadas", "Barras agrupadas", "serie_multiple", "barras", false, false, true, true);
        tipo("barras_apiladas", "Barras apiladas", "serie_multiple", "barras", false, true, true, true);
        tipo("barras_100", "Barras apiladas al 100 por ciento", "serie_multiple", "barras", false, true, true, true);
        tipo("lollipop", "Lollipop", "serie_simple", "barras", false, false, false, true);
        tipo("radar", "Radar de desempeño", "tabla", "comparacion", false, false, false, false);
        tipo("pareto", "Pareto", "serie_simple", "barras", false, true, false, true);
        tipo("linea", "Línea", "temporal", "tiempo", true, false, false, true);
        tipo("lineas_multiples", "Líneas múltiples", "serie_multiple", "tiempo", true, false, true, true);
        tipo("area", "Área", "temporal", "tiempo", true, false, false, true);
        tipo("area_apilada", "Área apilada", "serie_multiple", "tiempo", true, true, true, true);
        tipo("escalonada", "Escalonada", "temporal", "tiempo", true, false, false, true);
        tipo("combinada", "Barras más línea", "temporal_doble", "tiempo", true, false, false, false);
        tipo("cascada", "Cascada", "acumulado", "composicion", false, false, false, false);
        tipo("calendario", "Calendario", "temporal", "tiempo", true, false, false, false);
        tipo("sparklines", "Sparklines", "temporal", "indicadores", true, false, false, false);
        tipo("pastel", "Pastel", "serie_simple", "composicion", false, true, false, true);
        tipo("dona", "Dona", "serie_simple", "composicion", false, true, false, true);
        tipo("semidona", "Semidona", "serie_simple", "composicion", false, true, false, true);
        tipo("treemap", "Treemap", "jerarquia", "jerarquia", false, true, true, false);
        tipo("sunburst", "Sunburst", "jerarquia", "jerarquia", false, true, true, false);
        tipo("waffle", "Waffle", "serie_simple", "composicion", false, true, false, true);
        tipo("histograma", "Histograma", "distribucion", "distribucion", false, false, false, false);
        tipo("boxplot", "Boxplot", "distribucion", "distribucion", false, false, false, false);
        tipo("dispersion", "Dispersión", "puntos", "relacion", false, false, false, false);
        tipo("burbujas", "Burbujas", "puntos", "relacion", false, false, false, false);
        tipo("calor_dia_hora", "Mapa de calor por día y hora", "serie_multiple", "calor", false, false, true, false);
        tipo("calor_especialidad_mes", "Mapa de calor por especialidad y mes", "serie_multiple", "calor", false, false, true, false);
        tipo("embudo", "Embudo", "serie_simple", "flujo", false, true, false, false);
        tipo("sankey", "Sankey", "flujo", "flujo", false, false, false, false);
        tipo("linea_tiempo", "Línea de tiempo de trazabilidad", "eventos", "flujo", false, false, false, false);
        tipo("arbol", "Árbol", "jerarquia", "jerarquia", false, true, true, false);
        tipo("tarjetas_variacion", "Tarjetas con variación", "escalar", "indicadores", false, false, false, false);
        tipo("gauge", "Gauge", "escalar", "indicadores", false, false, false, false);
        tipo("bullet", "Bullet", "escalar", "indicadores", false, false, false, false);
        tipo("termometro", "Termómetro", "escalar", "indicadores", false, false, false, false);
        tipo("tabla_semaforo", "Tabla con semáforo", "tabla", "tabla", false, false, false, false);
    }

    private DashboardCatalogo() {
    }

    private static void metrica(String clave, String nombre, String corto, String unidad, int decimales,
                                boolean aditiva, boolean conteo, String sql, String sqlBitacora) {
        METRICAS.put(clave, new Metrica(clave, nombre, corto, unidad, decimales, aditiva, conteo, sql, sqlBitacora));
    }

    private static void agrupacion(String clave, String nombre, String sql, String orden, boolean temporal) {
        AGRUPACIONES.put(clave, new Agrupacion(clave, nombre, sql, orden, temporal));
    }

    private static void tipo(String clave, String nombre, String forma, String familia, boolean tiempo,
                             boolean aditiva, boolean secundaria, boolean explorador) {
        TIPOS_GRAFICA.add(new TipoGrafica(clave, nombre, forma, familia, tiempo, aditiva, secundaria, explorador));
    }

    public static Metrica metrica(String clave) {
        Metrica metrica = clave == null ? null : METRICAS.get(clave);
        if (metrica == null) {
            throw new SolicitudInvalidaException("La métrica indicada no existe.");
        }
        return metrica;
    }

    public static Agrupacion agrupacion(String clave) {
        Agrupacion agrupacion = clave == null ? null : AGRUPACIONES.get(clave);
        if (agrupacion == null) {
            throw new SolicitudInvalidaException("La agrupación indicada no existe.");
        }
        return agrupacion;
    }

    // Reglas de validez: las mismas que el catalogo le explica al frontend
    public static void validarCombinacion(String metrica, String agrupacion) {
        if ("carga".equals(metrica) && !AGRUPACIONES_CARGA.contains(agrupacion)) {
            throw new SolicitudInvalidaException("La carga actual solo se puede agrupar por técnico o por especialidad.");
        }
        if (ACCION_BITACORA.equals(agrupacion) && !metrica(metrica).conteo()) {
            throw new SolicitudInvalidaException("La acción en bitácora solo admite métricas de conteo.");
        }
    }

    public static boolean combinacionValida(String metrica, String agrupacion) {
        return !("carga".equals(metrica) && !AGRUPACIONES_CARGA.contains(agrupacion))
                && !(ACCION_BITACORA.equals(agrupacion) && !METRICAS.get(metrica).conteo());
    }
}
