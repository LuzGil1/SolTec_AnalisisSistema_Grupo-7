package com.example.soltec.service;

import com.example.soltec.dto.CatalogoDashboardDTO;
import com.example.soltec.dto.ConsultaDashboardRequest;
import com.example.soltec.dto.EventoTrazabilidadDTO;
import com.example.soltec.dto.IntegridadBitacoraDTO;
import com.example.soltec.dto.LoteDashboardRequest;
import com.example.soltec.dto.OpcionFiltroDTO;
import com.example.soltec.dto.OpcionesDashboardDTO;
import com.example.soltec.dto.ResultadoConsultaDTO;
import com.example.soltec.dto.ResultadoLoteDTO;
import com.example.soltec.dto.TrazabilidadDTO;
import com.example.soltec.exception.SolicitudInvalidaException;
import com.example.soltec.service.DashboardCatalogo.Agrupacion;
import com.example.soltec.service.DashboardCatalogo.Metrica;
import com.example.soltec.service.DashboardCatalogo.TipoGrafica;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// Motor generico del dashboard: una sola entrada y una forma de datos por
// grafica. Todo se lee de vw_reporte_solicitudes con SQL. Nada de JPA aqui.
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");
    private static final String VISTA = "soltec.vw_reporte_solicitudes v";
    private static final String CON_BITACORA = VISTA
            + " JOIN soltec.bitacora b ON b.entidad = 'caso' AND b.entidad_id = v.caso_id::TEXT";
    private static final String ESTADO_INTEGRO = "INTEGRA";

    // Nombre del nodo cuando una solicitud vuelve a una situacion por la que
    // ya paso: el Sankey no admite ciclos.
    private static final Map<String, String> NODOS_DE_RETORNO = Map.of(
            "EN_COLA", "Devuelta a la bolsa",
            "ASIGNADO", "Reasignada",
            "EN_PROCESO", "Retomada",
            "ESPERA_CLIENTE", "Espera de nuevo",
            "ESCALADO", "Escalada de nuevo",
            "RESUELTO", "Resuelta de nuevo");

    private final NamedParameterJdbcTemplate jdbc;
    private final DashboardExcelService excelService;

    // =================================================================
    // Catalogo y combos
    // =================================================================

    @Override
    public CatalogoDashboardDTO obtenerCatalogo() {
        List<Map<String, Object>> metricas = new ArrayList<>();
        for (Metrica metrica : DashboardCatalogo.METRICAS.values()) {
            List<String> permitidas = new ArrayList<>();
            for (String agrupacion : DashboardCatalogo.AGRUPACIONES.keySet()) {
                if (DashboardCatalogo.combinacionValida(metrica.clave(), agrupacion)) {
                    permitidas.add(agrupacion);
                }
            }
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("clave", metrica.clave());
            fila.put("nombre", metrica.nombre());
            fila.put("corto", metrica.corto());
            fila.put("unidad", metrica.unidad());
            fila.put("decimales", metrica.decimales());
            fila.put("aditiva", metrica.aditiva());
            fila.put("conteo", metrica.conteo());
            fila.put("ignoraPeriodo", DashboardCatalogo.METRICAS_SIN_PERIODO.contains(metrica.clave()));
            fila.put("agrupacionesPermitidas", permitidas);
            metricas.add(fila);
        }

        List<Map<String, Object>> agrupaciones = new ArrayList<>();
        for (Agrupacion agrupacion : DashboardCatalogo.AGRUPACIONES.values()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("clave", agrupacion.clave());
            fila.put("nombre", agrupacion.nombre());
            fila.put("temporal", agrupacion.temporal());
            agrupaciones.add(fila);
        }

        List<Map<String, Object>> tipos = new ArrayList<>();
        for (TipoGrafica tipo : DashboardCatalogo.TIPOS_GRAFICA) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("clave", tipo.clave());
            fila.put("nombre", tipo.nombre());
            fila.put("forma", tipo.forma());
            fila.put("familia", tipo.familia());
            fila.put("requiereTiempo", tipo.requiereTiempo());
            fila.put("soloAditiva", tipo.soloAditiva());
            fila.put("requiereSecundaria", tipo.requiereSecundaria());
            fila.put("explorador", tipo.explorador());
            tipos.add(fila);
        }

        List<Map<String, Object>> periodos = new ArrayList<>();
        for (Map.Entry<String, String> periodo : DashboardCatalogo.PERIODOS.entrySet()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("clave", periodo.getKey());
            fila.put("nombre", periodo.getValue());
            periodos.add(fila);
        }

        return CatalogoDashboardDTO.builder()
                .metricas(metricas)
                .agrupaciones(agrupaciones)
                .formas(DashboardCatalogo.FORMAS)
                .tiposGrafica(tipos)
                .periodos(periodos)
                .reglas(List.of(
                        "La carga actual solo se puede agrupar por técnico o por especialidad.",
                        "La acción en bitácora solo admite métricas de conteo.",
                        "La carga actual no depende del período: ignora el rango de fechas."))
                .build();
    }

    @Override
    public OpcionesDashboardDTO obtenerOpciones(Integer servicioId, Integer especialidadId) {
        MapSqlParameterSource vacio = new MapSqlParameterSource();

        String sqlEspecialidades = "SELECT e.id::TEXT AS codigo, e.nombre FROM soltec.especialidad e WHERE e.activo";
        MapSqlParameterSource parametrosEspecialidad = new MapSqlParameterSource();
        if (servicioId != null) {
            sqlEspecialidades += " AND e.id = (SELECT s.especialidad_id FROM soltec.servicio s WHERE s.id = :servicioId)";
            parametrosEspecialidad.addValue("servicioId", servicioId);
        }

        String sqlTecnicos = "SELECT t.usuario_id::TEXT AS codigo, u.nombres || ' ' || u.apellidos AS nombre"
                + " FROM soltec.tecnico t JOIN soltec.usuario u ON u.id = t.usuario_id WHERE TRUE";
        MapSqlParameterSource parametrosTecnico = new MapSqlParameterSource();
        if (especialidadId != null) {
            sqlTecnicos += " AND EXISTS (SELECT 1 FROM soltec.tecnico_especialidad te"
                    + " WHERE te.tecnico_id = t.usuario_id AND te.especialidad_id = :especialidadId)";
            parametrosTecnico.addValue("especialidadId", especialidadId);
        }

        return OpcionesDashboardDTO.builder()
                .tipos(opciones("SELECT codigo, nombre FROM soltec.tipo_solicitud WHERE activo ORDER BY id", vacio))
                .prioridades(opciones("SELECT codigo, nombre FROM soltec.prioridad ORDER BY orden", vacio))
                .situaciones(opciones("SELECT codigo, nombre FROM soltec.estado_caso ORDER BY orden", vacio))
                .servicios(opciones("SELECT id::TEXT AS codigo, nombre FROM soltec.servicio WHERE activo ORDER BY nombre", vacio))
                .especialidades(opciones(sqlEspecialidades + " ORDER BY e.nombre", parametrosEspecialidad))
                .tecnicos(opciones(sqlTecnicos + " ORDER BY 2", parametrosTecnico))
                .clientes(opciones("SELECT c.usuario_id::TEXT AS codigo, u.nombres || ' ' || u.apellidos AS nombre"
                        + " FROM soltec.cliente c JOIN soltec.usuario u ON u.id = c.usuario_id ORDER BY 2", vacio))
                .supervisores(opciones("SELECT u.id::TEXT AS codigo, u.nombres || ' ' || u.apellidos AS nombre"
                        + " FROM soltec.usuario u JOIN soltec.rol r ON r.id = u.rol_id"
                        + " WHERE r.codigo = 'SUPERVISOR' ORDER BY 2", vacio))
                .roles(DashboardCatalogo.ROLES)
                .boletaEjemplo(boletaConMasHistoria())
                .build();
    }

    // Boleta con la que abre la linea de tiempo de trazabilidad
    private String boletaConMasHistoria() {
        List<String> boletas = jdbc.queryForList("SELECT c.numero_boleta FROM soltec.caso c"
                + " JOIN soltec.seguimiento s ON s.caso_id = c.id"
                + " GROUP BY c.id ORDER BY COUNT(*) DESC, c.fecha_registro DESC LIMIT 1",
                new MapSqlParameterSource(), String.class);
        return boletas.isEmpty() ? null : boletas.get(0);
    }

    private List<OpcionFiltroDTO> opciones(String sql, MapSqlParameterSource parametros) {
        return jdbc.query(sql, parametros, (fila, numero) -> OpcionFiltroDTO.builder()
                .codigo(fila.getString("codigo"))
                .nombre(fila.getString("nombre"))
                .build());
    }

    // =================================================================
    // Motor
    // =================================================================

    @Override
    @Transactional(readOnly = true)
    public ResultadoConsultaDTO consultar(ConsultaDashboardRequest request) {
        DashboardFiltro filtro = DashboardFiltro.construir(request.getPeriodo(), request.getFiltros());
        return ejecutar(request, filtro);
    }

    // REPEATABLE READ: todas las graficas del lote leen la misma foto, aunque
    // entre una consulta y la siguiente entre una solicitud nueva.
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultadoLoteDTO consultarLote(LoteDashboardRequest request) {
        if (request.getConsultas() == null || request.getConsultas().isEmpty()) {
            throw new SolicitudInvalidaException("Indique al menos una consulta.");
        }

        DashboardFiltro filtro = DashboardFiltro.construir(request.getPeriodo(), request.getFiltros());
        List<ResultadoConsultaDTO> resultados = new ArrayList<>();

        for (int i = 0; i < request.getConsultas().size(); i++) {
            ConsultaDashboardRequest consulta = request.getConsultas().get(i);
            try {
                resultados.add(ejecutar(consulta, filtro));
            } catch (SolicitudInvalidaException e) {
                // Una consulta invalida no debe tumbar a las demas
                resultados.add(ResultadoConsultaDTO.builder().clave(consulta.getClave()).error(e.getMessage()).build());
            }
        }

        return ResultadoLoteDTO.builder()
                .periodo(filtro.etiquetaPeriodo())
                .totalSolicitudes(contarSolicitudes(filtro))
                .resultados(resultados)
                .build();
    }

    private long contarSolicitudes(DashboardFiltro filtro) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + VISTA + " WHERE " + filtro.where(),
                filtro.parametros(), Long.class);
        return total == null ? 0 : total;
    }

    private ResultadoConsultaDTO ejecutar(ConsultaDashboardRequest consulta, DashboardFiltro filtro) {
        String forma = consulta.getForma();
        if (forma == null || !DashboardCatalogo.FORMAS.contains(forma)) {
            throw new SolicitudInvalidaException("La forma de datos indicada no existe.");
        }
        String variante = consulta.getVariante();
        if (variante != null && !variante.isBlank() && !DashboardCatalogo.VARIANTES.contains(variante)) {
            throw new SolicitudInvalidaException("La variante indicada no existe.");
        }
        boolean hayVariante = variante != null && !variante.isBlank();

        ResultadoConsultaDTO resultado = ResultadoConsultaDTO.builder()
                .clave(consulta.getClave())
                .forma(forma)
                .periodo(filtro.etiquetaPeriodo())
                .build();

        if (hayVariante && "embudo".equals(variante)) {
            resultado.setTitulo("Avance de las solicitudes");
            resultado.setDatos(embudo(filtro));
            return resultado;
        }
        if (hayVariante) {
            resultado.setTitulo("Solicitudes abiertas acumuladas");
            resultado.setDatos(abiertasAcumuladas(filtro));
            return resultado;
        }

        switch (forma) {
            case "serie_simple" -> serieSimple(consulta, filtro, resultado);
            case "serie_multiple" -> serieMultiple(consulta, filtro, resultado);
            case "temporal" -> temporal(consulta, filtro, resultado);
            case "temporal_doble" -> temporalDoble(consulta, filtro, resultado);
            case "distribucion" -> distribucion(consulta, filtro, resultado);
            case "puntos" -> puntos(consulta, filtro, resultado);
            case "jerarquia" -> jerarquia(consulta, filtro, resultado);
            case "flujo" -> resultado.setDatos(flujo(filtro));
            case "acumulado" -> resultado.setDatos(cascada(filtro));
            case "escalar" -> escalar(consulta, filtro, resultado);
            case "eventos" -> resultado.setDatos(obtenerTrazabilidad(consulta.getBoleta()));
            default -> resultado.setDatos(tablaTecnicos(filtro));
        }
        return resultado;
    }

    private void serieSimple(ConsultaDashboardRequest consulta, DashboardFiltro filtro, ResultadoConsultaDTO resultado) {
        Metrica metrica = DashboardCatalogo.metrica(consulta.getMetrica());
        Agrupacion agrupacion = DashboardCatalogo.agrupacion(consulta.getAgrupacion());
        DashboardCatalogo.validarCombinacion(metrica.clave(), agrupacion.clave());

        DashboardFiltro alcance = alcanceDe(metrica, filtro);
        String expresion = expresionMetrica(metrica, agrupacion);
        String sql = "SELECT " + expresionAgrupacion(agrupacion, alcance) + " AS categoria, " + expresion + " AS valor"
                + " FROM " + origen(agrupacion) + " WHERE " + alcance.where()
                + " GROUP BY 1" + soloConCarga(metrica)
                + " ORDER BY " + (agrupacion.orden() != null ? "MIN(" + agrupacion.orden() + ")" : "2 DESC NULLS LAST, 1");

        List<Map<String, Object>> filas = jdbc.queryForList(sql, alcance.parametros());
        resultado.setDatos(filas);
        resultado.setPeriodo(alcance.etiquetaPeriodo());
        resultado.setTitulo(titulo(metrica, agrupacion));
        if (metrica.aditiva()) {
            resultado.setTotal(sumar(filas, "valor"));
        }
    }

    private void serieMultiple(ConsultaDashboardRequest consulta, DashboardFiltro filtro, ResultadoConsultaDTO resultado) {
        Metrica metrica = DashboardCatalogo.metrica(consulta.getMetrica());
        Agrupacion primera = DashboardCatalogo.agrupacion(consulta.getAgrupacion());
        Agrupacion segunda = DashboardCatalogo.agrupacion(consulta.getAgrupacionSecundaria());
        if (primera.clave().equals(segunda.clave())) {
            throw new SolicitudInvalidaException("Elija dos agrupaciones distintas.");
        }
        DashboardCatalogo.validarCombinacion(metrica.clave(), primera.clave());
        DashboardCatalogo.validarCombinacion(metrica.clave(), segunda.clave());

        DashboardFiltro alcance = alcanceDe(metrica, filtro);
        boolean conBitacora = usaBitacora(primera) || usaBitacora(segunda);
        String sql = "SELECT " + expresionAgrupacion(primera, alcance) + " AS categoria, "
                + expresionAgrupacion(segunda, alcance) + " AS serie, "
                + (conBitacora ? metrica.sqlBitacora() : metrica.sql()) + " AS valor"
                + " FROM " + (conBitacora ? CON_BITACORA : VISTA) + " WHERE " + alcance.where()
                + " GROUP BY 1, 2" + soloConCarga(metrica)
                + " ORDER BY " + (primera.orden() != null ? "MIN(" + primera.orden() + ")" : "1")
                + ", " + (segunda.orden() != null ? "MIN(" + segunda.orden() + ")" : "2");

        List<Map<String, Object>> filas = jdbc.queryForList(sql, alcance.parametros());
        resultado.setDatos(filas);
        resultado.setPeriodo(alcance.etiquetaPeriodo());
        resultado.setTitulo(titulo(metrica, primera) + " y " + segunda.nombre().toLowerCase());
        if (metrica.aditiva()) {
            resultado.setTotal(sumar(filas, "valor"));
        }
    }

    private void temporal(ConsultaDashboardRequest consulta, DashboardFiltro filtro, ResultadoConsultaDTO resultado) {
        Metrica metrica = DashboardCatalogo.metrica(consulta.getMetrica());
        Agrupacion agrupacion = agrupacionTemporal(consulta.getAgrupacion());
        DashboardCatalogo.validarCombinacion(metrica.clave(), agrupacion.clave());

        String sql = "SELECT " + agrupacion.sql() + " AS fecha, " + metrica.sql() + " AS valor FROM " + VISTA
                + " WHERE " + filtro.where() + " GROUP BY 1 ORDER BY MIN(" + agrupacion.orden() + ")";
        List<Map<String, Object>> filas = jdbc.queryForList(sql, filtro.parametros());
        resultado.setDatos(filas);
        resultado.setTitulo(titulo(metrica, agrupacion));
        if (metrica.aditiva()) {
            resultado.setTotal(sumar(filas, "valor"));
        }
    }

    private void temporalDoble(ConsultaDashboardRequest consulta, DashboardFiltro filtro, ResultadoConsultaDTO resultado) {
        Metrica barra = DashboardCatalogo.metrica(consulta.getMetrica());
        Metrica linea = DashboardCatalogo.metrica(consulta.getMetricaSecundaria());
        Agrupacion agrupacion = agrupacionTemporal(consulta.getAgrupacion());
        DashboardCatalogo.validarCombinacion(barra.clave(), agrupacion.clave());
        DashboardCatalogo.validarCombinacion(linea.clave(), agrupacion.clave());

        String sql = "SELECT " + agrupacion.sql() + " AS fecha, " + barra.sql() + " AS valor_barra, "
                + linea.sql() + " AS valor_linea FROM " + VISTA + " WHERE " + filtro.where()
                + " GROUP BY 1 ORDER BY MIN(" + agrupacion.orden() + ")";

        List<Map<String, Object>> filas = jdbc.queryForList(sql, filtro.parametros());
        List<Map<String, Object>> datos = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("fecha", filas.get(i).get("fecha"));
            fila.put("valorBarra", filas.get(i).get("valor_barra"));
            fila.put("valorLinea", filas.get(i).get("valor_linea"));
            datos.add(fila);
        }
        resultado.setDatos(datos);
        resultado.setTitulo(barra.nombre() + " y " + linea.nombre().toLowerCase());
    }

    private Agrupacion agrupacionTemporal(String clave) {
        Agrupacion agrupacion = DashboardCatalogo.agrupacion(clave);
        if (!agrupacion.temporal()) {
            throw new SolicitudInvalidaException("Esta gráfica necesita una agrupación de tiempo: día, semana o mes.");
        }
        return agrupacion;
    }

    // Sin agrupacion devuelve [valor, ...]. Con agrupacion, una lista de
    // valores por categoria (el boxplot necesita una caja por tecnico).
    private void distribucion(ConsultaDashboardRequest consulta, DashboardFiltro filtro, ResultadoConsultaDTO resultado) {
        Metrica metrica = DashboardCatalogo.metrica(consulta.getMetrica());
        String columna = DashboardCatalogo.COLUMNAS_DISTRIBUCION.get(metrica.clave());
        if (columna == null) {
            throw new SolicitudInvalidaException("Esta métrica no tiene un valor por solicitud para distribuir.");
        }
        resultado.setTitulo("Distribución de " + metrica.nombre().toLowerCase());

        if (consulta.getAgrupacion() == null || consulta.getAgrupacion().isBlank()) {
            String sql = "SELECT " + columna + " AS valor FROM " + VISTA + " WHERE " + filtro.where()
                    + " AND " + columna + " IS NOT NULL ORDER BY 1";
            resultado.setDatos(jdbc.queryForList(sql, filtro.parametros(), BigDecimal.class));
            return;
        }

        Agrupacion agrupacion = DashboardCatalogo.agrupacion(consulta.getAgrupacion());
        if (usaBitacora(agrupacion)) {
            throw new SolicitudInvalidaException("La acción en bitácora solo admite métricas de conteo.");
        }
        String sql = "SELECT " + expresionAgrupacion(agrupacion, filtro) + " AS categoria, " + columna + " AS valor"
                + " FROM " + VISTA + " WHERE " + filtro.where() + " AND " + columna + " IS NOT NULL ORDER BY 1, 2";

        List<Map<String, Object>> filas = jdbc.queryForList(sql, filtro.parametros());
        Map<String, List<Object>> grupos = new LinkedHashMap<>();
        for (int i = 0; i < filas.size(); i++) {
            String categoria = String.valueOf(filas.get(i).get("categoria"));
            grupos.computeIfAbsent(categoria, clave -> new ArrayList<>()).add(filas.get(i).get("valor"));
        }

        List<Map<String, Object>> datos = new ArrayList<>();
        for (Map.Entry<String, List<Object>> grupo : grupos.entrySet()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("categoria", grupo.getKey());
            fila.put("valores", grupo.getValue());
            datos.add(fila);
        }
        resultado.setDatos(datos);
    }

    private void puntos(ConsultaDashboardRequest consulta, DashboardFiltro filtro, ResultadoConsultaDTO resultado) {
        Metrica ejeX = DashboardCatalogo.metrica(consulta.getMetrica());
        Metrica ejeY = DashboardCatalogo.metrica(consulta.getMetricaSecundaria());
        Agrupacion agrupacion = DashboardCatalogo.agrupacion(consulta.getAgrupacion());
        DashboardCatalogo.validarCombinacion(ejeX.clave(), agrupacion.clave());
        DashboardCatalogo.validarCombinacion(ejeY.clave(), agrupacion.clave());
        if (usaBitacora(agrupacion)) {
            throw new SolicitudInvalidaException("La acción en bitácora solo admite métricas de conteo.");
        }

        String sql = "SELECT " + expresionAgrupacion(agrupacion, filtro) + " AS etiqueta, " + ejeX.sql() + " AS x, "
                + ejeY.sql() + " AS y, COUNT(*) AS radio FROM " + VISTA + " WHERE " + filtro.where() + " GROUP BY 1";
        List<Map<String, Object>> filas = jdbc.queryForList(sql, filtro.parametros());

        // La carga no depende del periodo, aunque el otro eje si
        boolean cargaEnX = ignoraPeriodo(ejeX);
        boolean cargaEnY = ignoraPeriodo(ejeY);
        if (cargaEnX || cargaEnY) {
            Map<String, Object> cargas = cargaActual(expresionAgrupacion(agrupacion, filtro), filtro);
            for (int i = 0; i < filas.size(); i++) {
                Object carga = cargas.getOrDefault(String.valueOf(filas.get(i).get("etiqueta")), 0);
                if (cargaEnX) {
                    filas.get(i).put("x", carga);
                }
                if (cargaEnY) {
                    filas.get(i).put("y", carga);
                }
            }
        }

        // Un punto sin alguno de sus dos ejes no se puede dibujar
        List<Map<String, Object>> datos = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            if (filas.get(i).get("x") != null && filas.get(i).get("y") != null) {
                datos.add(filas.get(i));
            }
        }
        resultado.setDatos(datos);
        resultado.setTitulo(ejeX.nombre() + " y " + ejeY.nombre().toLowerCase());
    }

    private void jerarquia(ConsultaDashboardRequest consulta, DashboardFiltro filtro, ResultadoConsultaDTO resultado) {
        Metrica metrica = DashboardCatalogo.metrica(consulta.getMetrica());
        if (!metrica.aditiva()) {
            throw new SolicitudInvalidaException("Una jerarquía solo admite métricas que se suman.");
        }
        List<Agrupacion> niveles = new ArrayList<>();
        niveles.add(DashboardCatalogo.agrupacion(consulta.getAgrupacion()));
        niveles.add(DashboardCatalogo.agrupacion(consulta.getAgrupacionSecundaria()));
        if (consulta.getAgrupacionTerciaria() != null && !consulta.getAgrupacionTerciaria().isBlank()) {
            niveles.add(DashboardCatalogo.agrupacion(consulta.getAgrupacionTerciaria()));
        }

        DashboardFiltro alcance = alcanceDe(metrica, filtro);
        StringBuilder columnas = new StringBuilder();
        StringBuilder grupos = new StringBuilder();
        for (int i = 0; i < niveles.size(); i++) {
            DashboardCatalogo.validarCombinacion(metrica.clave(), niveles.get(i).clave());
            if (usaBitacora(niveles.get(i))) {
                throw new SolicitudInvalidaException("Una jerarquía no admite la acción en bitácora.");
            }
            columnas.append(expresionAgrupacion(niveles.get(i), alcance)).append(" AS n").append(i).append(", ");
            grupos.append(i == 0 ? "" : ", ").append(i + 1);
        }

        String sql = "SELECT " + columnas + metrica.sql() + " AS valor FROM " + VISTA + " WHERE " + alcance.where()
                + " GROUP BY " + grupos + soloConCarga(metrica) + " ORDER BY " + (niveles.size() + 1) + " DESC";
        List<Map<String, Object>> filas = jdbc.queryForList(sql, alcance.parametros());

        List<Map<String, Object>> raiz = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            double valor = ((Number) filas.get(i).get("valor")).doubleValue();
            List<Map<String, Object>> nivelActual = raiz;
            for (int n = 0; n < niveles.size(); n++) {
                Map<String, Object> nodo = buscarNodo(nivelActual, String.valueOf(filas.get(i).get("n" + n)));
                nodo.put("valor", ((Number) nodo.get("valor")).doubleValue() + valor);
                nivelActual = hijos(nodo);
            }
        }
        resultado.setDatos(raiz);
        resultado.setPeriodo(alcance.etiquetaPeriodo());
        resultado.setTitulo(metrica.nombre());
        resultado.setTotal(sumar(raiz, "valor"));
    }

    private Map<String, Object> buscarNodo(List<Map<String, Object>> nivel, String nombre) {
        for (int i = 0; i < nivel.size(); i++) {
            if (nombre.equals(nivel.get(i).get("nombre"))) {
                return nivel.get(i);
            }
        }
        Map<String, Object> nodo = new LinkedHashMap<>();
        nodo.put("nombre", nombre);
        nodo.put("valor", 0.0);
        nodo.put("hijos", new ArrayList<Map<String, Object>>());
        nivel.add(nodo);
        return nodo;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> hijos(Map<String, Object> nodo) {
        return (List<Map<String, Object>>) nodo.get("hijos");
    }

    // Recorre el historial de cada solicitud. Si vuelve a una situacion por
    // la que ya paso, entra a un nodo propio ("Devuelta a la bolsa"): asi no
    // se forman ciclos y la devolucion se ve como un paso.
    private List<Map<String, Object>> flujo(DashboardFiltro filtro) {
        String sql = "SELECT s.caso_id, ea.codigo AS anterior_codigo, ea.nombre AS anterior,"
                + " en.codigo AS nuevo_codigo, en.nombre AS nuevo"
                + " FROM soltec.seguimiento s"
                + " JOIN soltec.estado_caso en ON en.id = s.estado_nuevo_id"
                + " LEFT JOIN soltec.estado_caso ea ON ea.id = s.estado_anterior_id"
                + " WHERE s.caso_id IN (SELECT v.caso_id FROM " + VISTA + " WHERE " + filtro.where() + ")"
                + " ORDER BY s.caso_id, s.fecha, s.id";
        List<Map<String, Object>> filas = jdbc.queryForList(sql, filtro.parametros());

        Map<String, Integer> conteo = new LinkedHashMap<>();
        Set<String> visitados = new HashSet<>();
        Object casoActual = null;
        String nodoActual = null;

        for (int i = 0; i < filas.size(); i++) {
            Map<String, Object> fila = filas.get(i);
            if (!fila.get("caso_id").equals(casoActual)) {
                casoActual = fila.get("caso_id");
                visitados.clear();
                visitados.add((String) fila.get("anterior_codigo"));
                nodoActual = (String) fila.get("anterior");
            }

            String codigo = (String) fila.get("nuevo_codigo");
            String destino = (String) fila.get("nuevo");
            if (!visitados.add(codigo)) {
                destino = NODOS_DE_RETORNO.getOrDefault(codigo, destino);
            }
            if (nodoActual != null && !nodoActual.equals(destino)) {
                conteo.merge(nodoActual + "|" + destino, 1, Integer::sum);
            }
            nodoActual = destino;
        }
        return enlacesSinCiclos(conteo);
    }

    // Seguro final: aunque una historia rara arme un ciclo entre solicitudes
    // distintas, ECharts nunca lo recibe. Pesan primero los enlaces mayores.
    private List<Map<String, Object>> enlacesSinCiclos(Map<String, Integer> conteo) {
        List<Map.Entry<String, Integer>> enlaces = new ArrayList<>(conteo.entrySet());
        enlaces.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        Map<String, Set<String>> salidas = new HashMap<>();
        List<Map<String, Object>> datos = new ArrayList<>();
        for (int i = 0; i < enlaces.size(); i++) {
            String[] partes = enlaces.get(i).getKey().split("\\|");
            if (hayCamino(salidas, partes[1], partes[0])) {
                continue;
            }
            salidas.computeIfAbsent(partes[0], clave -> new HashSet<>()).add(partes[1]);

            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("origen", partes[0]);
            fila.put("destino", partes[1]);
            fila.put("valor", enlaces.get(i).getValue());
            datos.add(fila);
        }
        return datos;
    }

    private boolean hayCamino(Map<String, Set<String>> salidas, String desde, String hasta) {
        List<String> pendientes = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        pendientes.add(desde);
        for (int i = 0; i < pendientes.size(); i++) {
            String nodo = pendientes.get(i);
            if (nodo.equals(hasta)) {
                return true;
            }
            if (vistos.add(nodo) && salidas.containsKey(nodo)) {
                pendientes.addAll(salidas.get(nodo));
            }
        }
        return false;
    }

    private List<Map<String, Object>> cascada(DashboardFiltro filtro) {
        String sql = "SELECT COUNT(*) AS recibidas,"
                + " COUNT(*) FILTER (WHERE v.rol_responsable = 'Sin asignar') AS sin_asignar,"
                + " COUNT(*) FILTER (WHERE v.situacion_codigo IN ('IMPROCEDENTE', 'DUPLICADO')) AS improcedentes,"
                + " COUNT(*) FILTER (WHERE v.situacion_codigo = 'CERRADO') AS cerradas"
                + " FROM " + VISTA + " WHERE " + filtro.where();
        Map<String, Object> fila = jdbc.queryForMap(sql, filtro.parametros());

        long recibidas = ((Number) fila.get("recibidas")).longValue();
        long sinAsignar = ((Number) fila.get("sin_asignar")).longValue();
        long improcedentes = ((Number) fila.get("improcedentes")).longValue();
        long cerradas = ((Number) fila.get("cerradas")).longValue();
        long sinResolver = recibidas - sinAsignar - improcedentes - cerradas;

        List<Map<String, Object>> datos = new ArrayList<>();
        datos.add(etapa("Recibidas", recibidas));
        datos.add(etapa("Sin asignar", -sinAsignar));
        datos.add(etapa("En atención", -sinResolver));
        datos.add(etapa("Improcedentes", -improcedentes));
        datos.add(etapa("Cerradas", cerradas));
        return datos;
    }

    private Map<String, Object> etapa(String nombre, long delta) {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("etapa", nombre);
        fila.put("delta", delta);
        return fila;
    }

    // Cuantas solicitudes pasaron por cada etapa, segun su historial. Solo
    // queja y reclamo: la denuncia y la sugerencia no recorren este camino.
    private List<Map<String, Object>> embudo(DashboardFiltro filtro) {
        String sql = "WITH filtradas AS (SELECT v.caso_id, v.situacion_codigo FROM " + VISTA
                + " WHERE " + filtro.where() + " AND v.tipo_codigo IN ('QUEJA', 'RECLAMO')),"
                + " pasos AS (SELECT caso_id, situacion_codigo AS codigo FROM filtradas"
                + " UNION SELECT s.caso_id, e.codigo FROM soltec.seguimiento s"
                + " JOIN filtradas f ON f.caso_id = s.caso_id"
                + " JOIN soltec.estado_caso e ON e.id = s.estado_anterior_id"
                + " UNION SELECT s.caso_id, e.codigo FROM soltec.seguimiento s"
                + " JOIN filtradas f ON f.caso_id = s.caso_id"
                + " JOIN soltec.estado_caso e ON e.id = s.estado_nuevo_id)"
                + " SELECT e.nombre AS categoria, COUNT(p.caso_id) AS valor"
                + " FROM soltec.estado_caso e LEFT JOIN pasos p ON p.codigo = e.codigo"
                + " WHERE e.codigo IN ('EN_COLA', 'ASIGNADO', 'EN_PROCESO', 'RESUELTO', 'CERRADO')"
                + " GROUP BY e.nombre, e.orden ORDER BY e.orden";
        return jdbc.queryForList(sql, filtro.parametros());
    }

    // Saldo de abiertas al cierre de cada mes: las registradas hasta ese
    // momento que todavia no concluian.
    private List<Map<String, Object>> abiertasAcumuladas(DashboardFiltro filtro) {
        LocalDate hoy = LocalDate.now(ZONA_GUATEMALA);
        MapSqlParameterSource parametros = new MapSqlParameterSource(filtro.parametros().getValues());
        parametros.addValue("inicioSerie", filtro.desde() != null ? filtro.desde() : hoy.minusMonths(12));
        parametros.addValue("finSerie", filtro.hasta() != null ? filtro.hasta() : hoy);

        String sql = "SELECT TO_CHAR(m.mes, 'YYYY-MM') AS fecha, ("
                + "SELECT COUNT(*) FROM " + VISTA + " WHERE " + filtro.where()
                + " AND v.dia < (m.mes + INTERVAL '1 month')::DATE"
                + " AND (v.abierta OR (v.fecha_cierre AT TIME ZONE 'America/Guatemala')::DATE >= (m.mes + INTERVAL '1 month')::DATE)"
                + ") AS valor FROM generate_series(DATE_TRUNC('month', CAST(:inicioSerie AS DATE)),"
                + " DATE_TRUNC('month', CAST(:finSerie AS DATE)), INTERVAL '1 month') AS m(mes) ORDER BY 1";
        return jdbc.queryForList(sql, parametros);
    }

    private void escalar(ConsultaDashboardRequest consulta, DashboardFiltro filtro, ResultadoConsultaDTO resultado) {
        Metrica metrica = DashboardCatalogo.metrica(consulta.getMetrica());
        DashboardFiltro alcance = alcanceDe(metrica, filtro);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("valor", valorEscalar(metrica, alcance));
        datos.put("meta", metaDe(metrica.clave()));

        DashboardFiltro anterior = alcance.periodoAnterior();
        datos.put("anterior", anterior == null ? null : valorEscalar(metrica, anterior));
        resultado.setDatos(datos);
        resultado.setPeriodo(alcance.etiquetaPeriodo());
        resultado.setTitulo(metrica.nombre());
    }

    private Object valorEscalar(Metrica metrica, DashboardFiltro filtro) {
        String sql = "SELECT " + metrica.sql() + " AS valor FROM " + VISTA + " WHERE " + filtro.where();
        return jdbc.queryForMap(sql, filtro.parametros()).get("valor");
    }

    private Double metaDe(String metrica) {
        String clave = DashboardCatalogo.PARAMETROS_META.get(metrica);
        if (clave == null) {
            return null;
        }
        List<BigDecimal> valores = jdbc.queryForList("SELECT valor FROM soltec.parametro WHERE clave = :clave",
                new MapSqlParameterSource("clave", clave), BigDecimal.class);
        return valores.isEmpty() ? null : valores.get(0).doubleValue();
    }

    private boolean ignoraPeriodo(Metrica metrica) {
        return DashboardCatalogo.METRICAS_SIN_PERIODO.contains(metrica.clave());
    }

    private DashboardFiltro alcanceDe(Metrica metrica, DashboardFiltro filtro) {
        return ignoraPeriodo(metrica) ? filtro.sinFechas() : filtro;
    }

    // Carga por etiqueta sin rango de fechas, para unirla a consultas que
    // mezclan la carga con metricas que si dependen del periodo.
    private Map<String, Object> cargaActual(String expresion, DashboardFiltro filtro) {
        DashboardFiltro alcance = filtro.sinFechas();
        Metrica carga = DashboardCatalogo.metrica("carga");
        String sql = "SELECT " + expresion + " AS etiqueta, " + carga.sql() + " AS carga FROM " + VISTA
                + " WHERE " + alcance.where() + " GROUP BY 1" + soloConCarga(carga);
        List<Map<String, Object>> filas = jdbc.queryForList(sql, alcance.parametros());

        Map<String, Object> cargas = new LinkedHashMap<>();
        for (int i = 0; i < filas.size(); i++) {
            cargas.put(String.valueOf(filas.get(i).get("etiqueta")), filas.get(i).get("carga"));
        }
        return cargas;
    }

    private List<Map<String, Object>> tablaTecnicos(DashboardFiltro filtro) {
        String sql = "SELECT v.tecnico, "
                + DashboardCatalogo.metrica("carga").sql() + " AS carga, "
                + DashboardCatalogo.metrica("resueltas").sql() + " AS resueltas, "
                + DashboardCatalogo.metrica("resolucion").sql() + " AS horas, "
                + DashboardCatalogo.metrica("sla").sql() + " AS sla, "
                + DashboardCatalogo.metrica("score").sql() + " AS score"
                + " FROM " + VISTA + " WHERE " + filtro.where() + " AND v.tecnico IS NOT NULL"
                + " GROUP BY 1 ORDER BY 3 DESC, 1";
        List<Map<String, Object>> filas = jdbc.queryForList(sql, filtro.parametros());
        unirCarga(filas, filtro);

        Double meta = metaDe("sla");
        for (int i = 0; i < filas.size(); i++) {
            Number sla = (Number) filas.get(i).get("sla");
            String semaforo = null;
            if (sla != null && meta != null) {
                semaforo = sla.doubleValue() >= meta ? "verde" : sla.doubleValue() >= meta - 15 ? "amarillo" : "rojo";
            }
            filas.get(i).put("semaforo", semaforo);
        }
        return filas;
    }

    // Un tecnico sin actividad en el periodo igual aparece si tiene carga hoy
    private void unirCarga(List<Map<String, Object>> filas, DashboardFiltro filtro) {
        Map<String, Object> cargas = cargaActual("v.tecnico", filtro);
        for (int i = 0; i < filas.size(); i++) {
            Object carga = cargas.remove(String.valueOf(filas.get(i).get("tecnico")));
            filas.get(i).put("carga", carga != null ? carga : 0);
        }
        for (Map.Entry<String, Object> pendiente : cargas.entrySet()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("tecnico", pendiente.getKey());
            fila.put("carga", pendiente.getValue());
            fila.put("resueltas", 0);
            fila.put("horas", null);
            fila.put("sla", null);
            fila.put("score", null);
            filas.add(fila);
        }
    }

    private String expresionMetrica(Metrica metrica, Agrupacion agrupacion) {
        return usaBitacora(agrupacion) ? metrica.sqlBitacora() : metrica.sql();
    }

    private String origen(Agrupacion agrupacion) {
        return usaBitacora(agrupacion) ? CON_BITACORA : VISTA;
    }

    private boolean usaBitacora(Agrupacion agrupacion) {
        return DashboardCatalogo.ACCION_BITACORA.equals(agrupacion.clave());
    }

    // Un ranking por cliente muestra el nombre; el NIT solo aparece cuando se
    // filtra por ese cliente.
    private String expresionAgrupacion(Agrupacion agrupacion, DashboardFiltro filtro) {
        if ("cliente".equals(agrupacion.clave()) && filtro.filtraCliente()) {
            return "v.cliente || ' (NIT ' || COALESCE(v.cliente_nit, 'sin registrar') || ')'";
        }
        return agrupacion.sql();
    }

    private String soloConCarga(Metrica metrica) {
        return "carga".equals(metrica.clave()) ? " HAVING " + metrica.sql() + " > 0" : "";
    }

    private String titulo(Metrica metrica, Agrupacion agrupacion) {
        return metrica.nombre() + " por " + agrupacion.nombre().toLowerCase();
    }

    private double sumar(List<Map<String, Object>> filas, String columna) {
        double total = 0;
        for (int i = 0; i < filas.size(); i++) {
            Object valor = filas.get(i).get(columna);
            if (valor instanceof Number numero) {
                total += numero.doubleValue();
            }
        }
        return total;
    }

    // =================================================================
    // Trazabilidad
    // =================================================================

    @Override
    @Transactional(readOnly = true)
    public TrazabilidadDTO obtenerTrazabilidad(String numeroBoleta) {
        if (numeroBoleta == null || numeroBoleta.isBlank()) {
            throw new SolicitudInvalidaException("Indique el número de boleta.");
        }
        MapSqlParameterSource parametros = new MapSqlParameterSource("boleta", numeroBoleta.trim());

        List<TrazabilidadDTO> cabeceras = jdbc.query("SELECT v.caso_id, v.numero_boleta, v.asunto, v.tipo, v.prioridad,"
                        + " v.situacion, v.cliente, v.tecnico, v.fecha_registro, v.fecha_limite_resolucion, v.cumplio_sla"
                        + " FROM " + VISTA + " WHERE v.numero_boleta = :boleta",
                parametros,
                (fila, numero) -> TrazabilidadDTO.builder()
                        .numeroBoleta(fila.getString("numero_boleta"))
                        .asunto(fila.getString("asunto"))
                        .tipo(fila.getString("tipo"))
                        .prioridad(fila.getString("prioridad"))
                        .situacion(fila.getString("situacion"))
                        .cliente(fila.getString("cliente"))
                        .tecnico(fila.getString("tecnico"))
                        .fechaRegistro(aGuatemala(fila.getObject("fecha_registro", OffsetDateTime.class)))
                        .fechaLimiteResolucion(aGuatemala(fila.getObject("fecha_limite_resolucion", OffsetDateTime.class)))
                        .cumplioSla(fila.getObject("cumplio_sla", Boolean.class))
                        .build());
        if (cabeceras.isEmpty()) {
            throw new SolicitudInvalidaException("No existe una solicitud con esa boleta.");
        }

        Integer casoId = jdbc.queryForObject("SELECT id FROM soltec.caso WHERE numero_boleta = :boleta", parametros, Integer.class);
        MapSqlParameterSource porCaso = new MapSqlParameterSource("casoId", casoId);

        List<EventoTrazabilidadDTO> eventos = new ArrayList<>();
        eventos.addAll(eventosSeguimiento(porCaso));
        eventos.addAll(eventosAsignacion(porCaso));
        eventos.addAll(eventosBitacora(porCaso));
        eventos.sort(Comparator.comparing(EventoTrazabilidadDTO::getFecha));

        TrazabilidadDTO trazabilidad = cabeceras.get(0);
        trazabilidad.setEventos(eventos);
        trazabilidad.setIntegridad(verificarIntegridad());
        return trazabilidad;
    }

    private List<EventoTrazabilidadDTO> eventosSeguimiento(MapSqlParameterSource parametros) {
        String sql = "SELECT s.fecha, u.nombres || ' ' || u.apellidos AS usuario, s.comentario,"
                + " ea.nombre AS anterior, en.nombre AS nuevo"
                + " FROM soltec.seguimiento s JOIN soltec.usuario u ON u.id = s.usuario_id"
                + " LEFT JOIN soltec.estado_caso ea ON ea.id = s.estado_anterior_id"
                + " LEFT JOIN soltec.estado_caso en ON en.id = s.estado_nuevo_id"
                + " WHERE s.caso_id = :casoId";
        return jdbc.query(sql, parametros, (fila, numero) -> {
            String nuevo = fila.getString("nuevo");
            String situacion = nuevo != null ? nuevo : fila.getString("anterior");
            return EventoTrazabilidadDTO.builder()
                    .fecha(aGuatemala(fila.getObject("fecha", OffsetDateTime.class)))
                    .origen("SEGUIMIENTO")
                    .usuario(fila.getString("usuario"))
                    .operacion(nuevo != null ? "Cambio de situación" : "Avance")
                    .detalle(fila.getString("comentario"))
                    .situacion(situacion)
                    .build();
        });
    }

    private List<EventoTrazabilidadDTO> eventosAsignacion(MapSqlParameterSource parametros) {
        String sql = "SELECT a.fecha_asignacion, a.fecha_liberacion, a.score, a.motivo,"
                + " u.nombres || ' ' || u.apellidos AS tecnico"
                + " FROM soltec.asignacion a JOIN soltec.usuario u ON u.id = a.tecnico_id WHERE a.caso_id = :casoId";
        List<Map<String, Object>> filas = jdbc.query(sql, parametros, (fila, numero) -> {
            Map<String, Object> mapa = new HashMap<>();
            mapa.put("asignacion", fila.getObject("fecha_asignacion", OffsetDateTime.class));
            mapa.put("liberacion", fila.getObject("fecha_liberacion", OffsetDateTime.class));
            mapa.put("score", fila.getBigDecimal("score"));
            mapa.put("motivo", fila.getString("motivo"));
            mapa.put("tecnico", fila.getString("tecnico"));
            return mapa;
        });

        List<EventoTrazabilidadDTO> eventos = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            Map<String, Object> fila = filas.get(i);
            String tecnico = (String) fila.get("tecnico");
            eventos.add(EventoTrazabilidadDTO.builder()
                    .fecha(aGuatemala((OffsetDateTime) fila.get("asignacion")))
                    .origen("ASIGNACION")
                    .usuario(tecnico)
                    .operacion("Asignación")
                    .detalle("Asignada a " + tecnico + ". Score " + fila.get("score") + ". Motivo: " + fila.get("motivo"))
                    .build());
            if (fila.get("liberacion") != null) {
                eventos.add(EventoTrazabilidadDTO.builder()
                        .fecha(aGuatemala((OffsetDateTime) fila.get("liberacion")))
                        .origen("ASIGNACION")
                        .usuario(tecnico)
                        .operacion("Liberación")
                        .detalle("La asignación de " + tecnico + " quedó liberada.")
                        .build());
            }
        }
        return eventos;
    }

    // Lista las columnas a proposito: los hash de la bitacora no salen de aqui
    private List<EventoTrazabilidadDTO> eventosBitacora(MapSqlParameterSource parametros) {
        String sql = "SELECT b.fecha_hora, b.accion, b.descripcion, b.usuario_correo,"
                + " u.nombres || ' ' || u.apellidos AS usuario"
                + " FROM soltec.bitacora b LEFT JOIN soltec.usuario u ON u.id = b.usuario_id"
                + " WHERE b.entidad = 'caso' AND b.entidad_id = CAST(:casoId AS TEXT)";
        return jdbc.query(sql, parametros, (fila, numero) -> {
            String usuario = fila.getString("usuario");
            return EventoTrazabilidadDTO.builder()
                    .fecha(aGuatemala(fila.getObject("fecha_hora", OffsetDateTime.class)))
                    .origen("BITACORA")
                    .usuario(usuario != null ? usuario : "Sistema")
                    .operacion(fila.getString("accion"))
                    .detalle(fila.getString("descripcion"))
                    .build();
        });
    }

    // La funcion siempre devuelve una fila: se revisa el estado, no la
    // cantidad de filas.
    private IntegridadBitacoraDTO verificarIntegridad() {
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT estado, detalle FROM soltec.fn_verificar_integridad_bitacora()", new MapSqlParameterSource());
        return IntegridadBitacoraDTO.builder()
                .integra(ESTADO_INTEGRO.equals(fila.get("estado")))
                .mensaje(String.valueOf(fila.get("detalle")))
                .build();
    }

    private OffsetDateTime aGuatemala(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.atZoneSameInstant(ZONA_GUATEMALA).toOffsetDateTime();
    }

    // =================================================================
    // Excel
    // =================================================================

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] exportarExcel(ConsultaDashboardRequest request) throws IOException {
        if (request.getMetrica() == null || request.getMetrica().isBlank()) {
            request.setMetrica("cantidad");
        }
        if (request.getAgrupacion() == null || request.getAgrupacion().isBlank()) {
            request.setAgrupacion("tipo");
        }
        request.setForma("serie_simple");

        DashboardFiltro filtro = DashboardFiltro.construir(request.getPeriodo(), request.getFiltros());
        ResultadoConsultaDTO reporte = ejecutar(request, filtro);
        Metrica metrica = DashboardCatalogo.metrica(request.getMetrica());
        Agrupacion agrupacion = DashboardCatalogo.agrupacion(request.getAgrupacion());

        Map<String, Object> indicadores = new LinkedHashMap<>();
        indicadores.put("Solicitudes", contarSolicitudes(filtro));
        indicadores.put("Resolución media (h)", valorEscalar(DashboardCatalogo.metrica("resolucion"), filtro));
        indicadores.put("Cumplimiento de SLA (%)", valorEscalar(DashboardCatalogo.metrica("sla"), filtro));
        indicadores.put("Vencidas", valorEscalar(DashboardCatalogo.metrica("vencidas"), filtro));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filas = (List<Map<String, Object>>) reporte.getDatos();
        return excelService.generar(reporte.getTitulo(), reporte.getPeriodo(), agrupacion.nombre(), metrica,
                filas, reporte.getTotal(), indicadores, tablaTecnicos(filtro));
    }
}
