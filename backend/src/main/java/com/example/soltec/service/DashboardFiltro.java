package com.example.soltec.service;

import com.example.soltec.dto.FiltrosDashboardDTO;
import com.example.soltec.exception.SolicitudInvalidaException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

// Unico lugar donde se arma el WHERE. Cada valor del cliente va como
// parametro nombrado; el texto del SQL solo tiene condiciones fijas.
public final class DashboardFiltro {

    private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FECHA_RESOLUCION = "(v.fecha_resolucion AT TIME ZONE 'America/Guatemala')::DATE";

    private final StringBuilder where = new StringBuilder("TRUE");
    private final MapSqlParameterSource parametros = new MapSqlParameterSource();
    private final LocalDate desde;
    private final LocalDate hasta;
    private final String etiquetaPeriodo;
    private final FiltrosDashboardDTO filtros;
    private final String periodo;

    private DashboardFiltro(String periodo, FiltrosDashboardDTO filtros, LocalDate desde, LocalDate hasta, String etiqueta) {
        this.periodo = periodo;
        this.filtros = filtros;
        this.desde = desde;
        this.hasta = hasta;
        this.etiquetaPeriodo = etiqueta;
        aplicarFechas();
        aplicarListas();
        aplicarBanderas();
    }

    public static DashboardFiltro construir(String periodo, FiltrosDashboardDTO recibidos) {
        FiltrosDashboardDTO filtros = recibidos != null ? recibidos : new FiltrosDashboardDTO();
        String clave = periodo == null || periodo.isBlank() ? "ultimos_12_meses" : periodo;
        if (!DashboardCatalogo.PERIODOS.containsKey(clave)) {
            throw new SolicitudInvalidaException("El período indicado no existe.");
        }

        LocalDate hoy = LocalDate.now(ZONA_GUATEMALA);
        LocalDate desde = switch (clave) {
            case "ultimos_7_dias" -> hoy.minusDays(6);
            case "ultimos_30_dias" -> hoy.minusDays(29);
            case "ultimos_3_meses" -> hoy.minusMonths(3);
            case "este_anio" -> hoy.withDayOfYear(1);
            case "ultimos_12_meses" -> hoy.minusMonths(12);
            default -> null;
        };
        LocalDate hasta = "todo".equals(clave) ? null : hoy;
        String etiqueta = DashboardCatalogo.PERIODOS.get(clave);

        // Las fechas escritas a mano mandan sobre el periodo elegido
        if (filtros.getDesde() != null || filtros.getHasta() != null) {
            desde = filtros.getDesde();
            hasta = filtros.getHasta();
            etiqueta = textoRango(desde, hasta);
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException("La fecha inicial no puede ser posterior a la fecha final.");
        }
        return new DashboardFiltro(clave, filtros, desde, hasta, etiqueta);
    }

    // El mismo filtro, en el periodo de igual largo inmediatamente anterior.
    // Devuelve null si el periodo no tiene los dos extremos.
    public DashboardFiltro periodoAnterior() {
        if (desde == null || hasta == null) {
            return null;
        }
        long dias = hasta.toEpochDay() - desde.toEpochDay() + 1;
        return new DashboardFiltro(periodo, filtros, desde.minusDays(dias), desde.minusDays(1), etiquetaPeriodo);
    }

    // El mismo filtro sin rango de fechas, para las metricas que muestran la
    // situacion actual. Los demas filtros se conservan.
    public DashboardFiltro sinFechas() {
        return new DashboardFiltro(periodo, filtros, null, null, "Actuales, sin importar el período");
    }

    private static String textoRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null) {
            return "Del " + desde.format(FORMATO) + " al " + hasta.format(FORMATO);
        }
        return desde != null ? "Desde el " + desde.format(FORMATO) : "Hasta el " + hasta.format(FORMATO);
    }

    private void aplicarFechas() {
        String modo = filtros.getModoFecha();
        boolean porResolucion = "RESOLUCION".equals(modo);
        if (modo != null && !modo.isBlank() && !porResolucion && !"REGISTRO".equals(modo)) {
            throw new SolicitudInvalidaException("El modo de fecha indicado no existe.");
        }
        String columna = porResolucion ? FECHA_RESOLUCION : "v.dia";
        if (desde != null) {
            where.append(" AND ").append(columna).append(" >= :desde");
            parametros.addValue("desde", desde);
        }
        if (hasta != null) {
            where.append(" AND ").append(columna).append(" <= :hasta");
            parametros.addValue("hasta", hasta);
        }
    }

    private void aplicarListas() {
        String texto = filtros.getTexto();
        if (texto != null && !texto.isBlank()) {
            where.append(" AND (v.numero_boleta ILIKE :texto OR v.asunto ILIKE :texto OR v.descripcion ILIKE :texto)");
            parametros.addValue("texto", "%" + escaparLike(texto.trim()) + "%");
        }
        igual("v.tipo_codigo", "tipo", filtros.getTipo());
        igual("v.prioridad_codigo", "prioridad", filtros.getPrioridad());
        igual("v.situacion_codigo", "situacion", filtros.getSituacion());
        igual("v.servicio_id", "servicioId", filtros.getServicioId());
        igual("v.especialidad_id", "especialidadId", filtros.getEspecialidadId());
        igual("v.tecnico_id", "tecnicoId", filtros.getTecnicoId());
        igual("v.cliente_id", "clienteId", filtros.getClienteId());
        igual("v.supervisor_responsable_id", "supervisorId", filtros.getSupervisorId());

        String rol = filtros.getRolResponsable();
        if (rol != null && !rol.isBlank()) {
            if (!DashboardCatalogo.ROLES.contains(rol)) {
                throw new SolicitudInvalidaException("El valor de En manos de no existe.");
            }
            igual("v.rol_responsable", "rolResponsable", rol);
        }
    }

    private void aplicarBanderas() {
        bandera(filtros.getSoloVencidas(), "v.vencida");
        bandera(filtros.getSoloDentroSla(), "v.cumplio_sla");
        bandera(filtros.getSoloAbiertas(), "v.abierta");
        bandera(filtros.getIncumplioRespuesta(), "v.incumplio_respuesta");
        bandera(filtros.getConPersonalSenalado(), "v.con_personal_senalado");
        bandera(filtros.getConTecnicoExcluido(), "v.con_tecnico_excluido");
        bandera(filtros.getConAdjuntos(), "v.con_adjuntos");
        bandera(filtros.getConOrdenServicio(), "v.con_orden_servicio");
        bandera(filtros.getDuplicadasRelacionadas(), "v.con_caso_relacionado");
    }

    private void igual(String columna, String parametro, Object valor) {
        if (valor == null || "".equals(valor)) {
            return;
        }
        where.append(" AND ").append(columna).append(" = :").append(parametro);
        parametros.addValue(parametro, valor);
    }

    private void bandera(Boolean activa, String condicion) {
        if (Boolean.TRUE.equals(activa)) {
            where.append(" AND ").append(condicion);
        }
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public String where() {
        return where.toString();
    }

    public MapSqlParameterSource parametros() {
        return parametros;
    }

    public LocalDate desde() {
        return desde;
    }

    public LocalDate hasta() {
        return hasta;
    }

    public String etiquetaPeriodo() {
        return etiquetaPeriodo;
    }

    public boolean filtraCliente() {
        return filtros.getClienteId() != null;
    }
}
