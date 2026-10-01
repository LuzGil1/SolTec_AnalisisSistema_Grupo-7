package com.example.soltec.service;

import com.example.soltec.config.UsuarioActualProvider;
import com.example.soltec.dto.AdjuntoArchivoDTO;
import com.example.soltec.dto.AdjuntoDTO;
import com.example.soltec.dto.AvanceDTO;
import com.example.soltec.dto.CambiarCapacidadRequest;
import com.example.soltec.dto.CambiarDisponibilidadRequest;
import com.example.soltec.dto.CasoGeneralDTO;
import com.example.soltec.dto.CasoSupervisorDetalleDTO;
import com.example.soltec.dto.ClienteContactoDTO;
import com.example.soltec.dto.FiltrosSupervisorDTO;
import com.example.soltec.dto.InvolucradoDTO;
import com.example.soltec.dto.OpcionFiltroDTO;
import com.example.soltec.dto.ResultadoCambioTecnicoDTO;
import com.example.soltec.dto.ResumenSupervisorDTO;
import com.example.soltec.dto.ServicioRecibidoDTO;
import com.example.soltec.dto.SinAsignarDTO;
import com.example.soltec.dto.SituacionTecnicoDTO;
import com.example.soltec.dto.SolicitudSinAsignarDTO;
import com.example.soltec.dto.TecnicoAsignadoDTO;
import com.example.soltec.entity.Adjunto;
import com.example.soltec.entity.Caso;
import com.example.soltec.entity.Cliente;
import com.example.soltec.entity.OrdenServicio;
import com.example.soltec.entity.Seguimiento;
import com.example.soltec.entity.Usuario;
import com.example.soltec.exception.SolicitudInvalidaException;
import com.example.soltec.repository.AdjuntoRepository;
import com.example.soltec.repository.CambioDisponibilidadProyeccion;
import com.example.soltec.repository.CargaTecnicoProyeccion;
import com.example.soltec.repository.CasoInvolucradoRepository;
import com.example.soltec.repository.CasoRepository;
import com.example.soltec.repository.ClienteRepository;
import com.example.soltec.repository.EscalamientoProyeccion;
import com.example.soltec.repository.EstadoCasoRepository;
import com.example.soltec.repository.ResumenSupervisorProyeccion;
import com.example.soltec.repository.SeguimientoRepository;
import com.example.soltec.repository.TecnicoRepository;
import com.example.soltec.repository.TipoSolicitudRepository;
import com.example.soltec.repository.UsuarioRepository;
import com.example.soltec.storage.AlmacenamientoService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Modulo del Supervisor de Soporte. El supervisor ve TODOS los casos (no hay
// filtro de propiedad) y NO asigna solicitudes a mano: la distribucion es
// exclusiva del sistema (fn_asignar_solicitud / fn_procesar_bolsa). Lo unico
// que puede mover es la disponibilidad y la capacidad de los tecnicos.
@Service
@RequiredArgsConstructor
public class SupervisorServiceImpl implements SupervisorService {

    private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");
    private static final String MENSAJE_CAPACIDAD_INVALIDA = "La capacidad debe ser un número entero mayor a cero.";

    private final CasoRepository casoRepository;
    private final TecnicoRepository tecnicoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final AdjuntoRepository adjuntoRepository;
    private final CasoInvolucradoRepository casoInvolucradoRepository;
    private final SeguimientoRepository seguimientoRepository;
    private final EstadoCasoRepository estadoCasoRepository;
    private final TipoSolicitudRepository tipoSolicitudRepository;
    private final AlmacenamientoService almacenamientoService;
    private final ParametroService parametroService;
    private final BitacoraService bitacoraService;
    private final UsuarioActualProvider usuarioActualProvider;

    @Override
    public ResumenSupervisorDTO obtenerResumen() {
        ResumenSupervisorProyeccion resumen = casoRepository.resumenSupervisor();
        return ResumenSupervisorDTO.builder()
                .denunciasPendientes(resumen.getDenunciasPendientes())
                .escalamientosPendientes(resumen.getEscalamientosPendientes())
                .solicitudesSinAsignar(resumen.getSolicitudesSinAsignar())
                .build();
    }

    @Override
    public FiltrosSupervisorDTO obtenerFiltros() {
        List<OpcionFiltroDTO> tipos = tipoSolicitudRepository.findByActivoTrue().stream()
                .map(t -> OpcionFiltroDTO.builder().codigo(t.getCodigo()).nombre(t.getNombre()).build())
                .toList();
        List<OpcionFiltroDTO> estados = estadoCasoRepository.findAll(Sort.by("orden")).stream()
                .map(e -> OpcionFiltroDTO.builder().codigo(e.getCodigo()).nombre(e.getNombre()).build())
                .toList();
        return FiltrosSupervisorDTO.builder().tipos(tipos).estados(estados).build();
    }

    @Override
    public List<CasoGeneralDTO> listarCasos(String tipo, String estado, Integer tecnicoId,
                                            LocalDate fechaDesde, LocalDate fechaHasta, String boleta) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new SolicitudInvalidaException("La fecha inicial no puede ser posterior a la fecha final.");
        }

        return casoRepository.buscarGeneral(vacioANulo(tipo), vacioANulo(estado), tecnicoId,
                        fechaDesde, fechaHasta, escaparLike(vacioANulo(boleta)))
                .stream()
                .map(c -> CasoGeneralDTO.builder()
                        .id(c.getCasoId())
                        .numeroBoleta(c.getNumeroBoleta())
                        .tipoCodigo(c.getTipoCodigo())
                        .tipo(c.getTipo())
                        .asunto(c.getAsunto())
                        .cliente(c.getCliente())
                        .tecnicoId(c.getTecnicoId())
                        .tecnico(c.getTecnico())
                        .estadoCodigo(c.getEstadoCodigo())
                        .estado(c.getEstado())
                        .esFinal(c.getEsFinal())
                        .prioridad(c.getPrioridad())
                        .fechaRegistro(aGuatemala(c.getFechaRegistro()))
                        .fechaLimite(aGuatemala(c.getFechaLimite()))
                        .build())
                .toList();
    }

    // Read-write a proposito: RN08 del CU Dashboard del Supervisor pide
    // asentar en bitacora la consulta del detalle.
    @Override
    @Transactional
    public CasoSupervisorDetalleDTO obtenerDetalle(Integer casoId, String direccionIp) {
        Caso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("El caso indicado no existe"));

        Usuario supervisor = usuarioActualProvider.obtener();
        bitacoraService.registrar(supervisor.getId(), direccionIp, "SUPERVISOR", "CONSULTAR_DETALLE",
                "caso", casoId.toString(), "Consulta del detalle de la solicitud " + caso.getNumeroBoleta(), null);

        Cliente cliente = clienteRepository.findById(caso.getClienteId())
                .orElseThrow(() -> new IllegalStateException("Cliente no encontrado para el caso " + casoId));

        TecnicoAsignadoDTO tecnico = caso.getTecnicoAsignadoId() == null ? null
                : usuarioRepository.findById(caso.getTecnicoAsignadoId())
                        .map(u -> TecnicoAsignadoDTO.builder()
                                .id(u.getId())
                                .nombre(nombreCompleto(u))
                                .correo(u.getCorreo())
                                .telefono(u.getTelefono())
                                .build())
                        .orElse(null);

        OrdenServicio orden = caso.getOrdenServicio();
        String tecnicoServicio = orden == null || orden.getTecnicoId() == null ? null
                : usuarioRepository.findById(orden.getTecnicoId()).map(this::nombreCompleto).orElse(null);

        String casoRelacionadoBoleta = caso.getCasoRelacionadoId() == null ? null
                : casoRepository.findById(caso.getCasoRelacionadoId()).map(Caso::getNumeroBoleta).orElse(null);

        List<AdjuntoDTO> adjuntos = adjuntoRepository.findByCasoIdOrderByIdAsc(casoId).stream()
                .map(this::aAdjuntoDTO)
                .toList();

        // Todos los avances, incluidos los internos del tecnico
        List<AvanceDTO> avances = seguimientoRepository.findByCasoIdOrderByFechaDesc(casoId).stream()
                .map(this::aAvanceDTO)
                .toList();

        List<InvolucradoDTO> involucrados = casoInvolucradoRepository.listarPorCaso(casoId).stream()
                .map(i -> InvolucradoDTO.builder()
                        .usuarioId(i.getUsuarioId())
                        .nombre(i.getNombre())
                        .rol(i.getRol())
                        .motivo(i.getMotivo())
                        .build())
                .toList();

        // Quien escalo y por que (RN02 del CU Revision de Escalamientos); todo
        // null si el caso nunca fue escalado
        EscalamientoProyeccion escalamiento = casoRepository.obtenerDatosEscalamiento(casoId).orElse(null);

        return CasoSupervisorDetalleDTO.builder()
                .id(caso.getId())
                .numeroBoleta(caso.getNumeroBoleta())
                .tipo(caso.getTipoSolicitud().getNombre())
                .estado(caso.getEstado().getNombre())
                .prioridad(caso.getPrioridad().getNombre())
                .asunto(caso.getAsunto())
                .descripcion(caso.getDescripcion())
                .fechaRegistro(caso.getFechaRegistro())
                .fechaLimiteResolucion(caso.getFechaLimiteResolucion())
                .fechaAsignacion(caso.getFechaAsignacion())
                .fechaResolucion(caso.getFechaResolucion())
                .fechaCierre(caso.getFechaCierre())
                .solucion(caso.getSolucion())
                .casoRelacionadoBoleta(casoRelacionadoBoleta)
                .cliente(aClienteContacto(cliente))
                .tecnico(tecnico)
                .servicioRecibido(aServicioRecibido(orden))
                .tecnicoServicio(tecnicoServicio)
                .adjuntos(adjuntos)
                .avances(avances)
                .involucrados(involucrados)
                .escaladaPor(escalamiento == null ? null : escalamiento.getEscaladaPor())
                .motivoEscalamiento(escalamiento == null ? null : escalamiento.getMotivoEscalamiento())
                .fechaEscalamiento(escalamiento == null ? null : aGuatemala(escalamiento.getFechaEscalamiento()))
                .supervisorResponsable(escalamiento == null ? null : escalamiento.getSupervisorResponsable())
                .transicionesPermitidas(List.of())
                .build();
    }

    @Override
    public AdjuntoArchivoDTO descargarAdjunto(Integer casoId, Integer adjuntoId) throws IOException {
        Adjunto adjunto = adjuntoRepository.findByIdAndCasoId(adjuntoId, casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("El adjunto indicado no existe"));

        Resource recurso = almacenamientoService.recuperar(adjunto.getRuta());

        return AdjuntoArchivoDTO.builder()
                .nombreArchivo(adjunto.getNombreArchivo())
                .tipoMime(adjunto.getTipoMime())
                .recurso(recurso)
                .build();
    }

    // Read-write a proposito: RN05 del CU Solicitudes sin asignar pide
    // asentar la consulta en bitacora.
    @Override
    @Transactional
    public SinAsignarDTO listarSinAsignar(String direccionIp) {
        List<SolicitudSinAsignarDTO> solicitudes = casoRepository.buscarSinAsignar().stream()
                .map(s -> SolicitudSinAsignarDTO.builder()
                        .id(s.getCasoId())
                        .numeroBoleta(s.getNumeroBoleta())
                        .tipo(s.getTipo())
                        .asunto(s.getAsunto())
                        .cliente(s.getCliente())
                        .prioridad(s.getPrioridad())
                        .fechaRegistro(aGuatemala(s.getFechaRegistro()))
                        .horasEnCola(s.getHorasEnCola())
                        .fechaLimite(aGuatemala(s.getFechaLimite()))
                        .build())
                .toList();

        Usuario supervisor = usuarioActualProvider.obtener();
        bitacoraService.registrar(supervisor.getId(), direccionIp, "SUPERVISOR", "CONSULTAR_SIN_ASIGNAR",
                null, null, "Consulta del listado de solicitudes sin asignar",
                Map.of("total", solicitudes.size()));

        return SinAsignarDTO.builder()
                .horasUmbral(parametroService.obtenerHorasAlertaSinAsignar())
                .solicitudes(solicitudes)
                .tecnicos(listarTecnicos())
                .build();
    }

    @Override
    public List<SituacionTecnicoDTO> listarTecnicos() {
        return tecnicoRepository.listarCarga().stream()
                .map(this::aSituacionTecnico)
                .toList();
    }

    // La liberacion de casos y el registro en bitacora los hace
    // fn_cambiar_disponibilidad (con el trigger existente sobre
    // tecnico.disponible); aqui no se repite nada de eso.
    @Override
    @Transactional
    public ResultadoCambioTecnicoDTO cambiarDisponibilidad(Integer tecnicoId, CambiarDisponibilidadRequest request,
                                                           String direccionIp) {
        buscarTecnico(tecnicoId);
        Usuario supervisor = usuarioActualProvider.obtener();

        CambioDisponibilidadProyeccion resultado = tecnicoRepository.cambiarDisponibilidad(
                tecnicoId, request.getDisponible(), request.getMotivo(), supervisor.getId(), direccionIp);

        return ResultadoCambioTecnicoDTO.builder()
                .tecnico(aSituacionTecnico(buscarTecnico(tecnicoId)))
                .casosLiberados(resultado.getCasosLiberados())
                .casosAsignados(resultado.getCasosAsignados())
                .build();
    }

    // RN04: al reducir la capacidad el tecnico conserva sus casos (solo deja
    // de recibir nuevos); al aumentarla se reparte la bolsa de inmediato para
    // aprovechar los cupos nuevos, sin esperar al scheduler.
    @Override
    @Transactional
    public ResultadoCambioTecnicoDTO cambiarCapacidad(Integer tecnicoId, CambiarCapacidadRequest request,
                                                      String direccionIp) {
        short nuevaCapacidad = validarCapacidad(request.getCapacidadMaxima());
        CargaTecnicoProyeccion actual = buscarTecnico(tecnicoId);
        int capacidadAnterior = actual.getCapacidadMaxima();

        int asignados = 0;
        if (nuevaCapacidad != capacidadAnterior) {
            tecnicoRepository.actualizarCapacidad(tecnicoId, nuevaCapacidad);

            if (nuevaCapacidad > capacidadAnterior) {
                asignados = casoRepository.procesarBolsa();
            }

            Usuario supervisor = usuarioActualProvider.obtener();
            bitacoraService.registrar(supervisor.getId(), direccionIp, "TECNICOS", "CAMBIAR_CAPACIDAD",
                    "tecnico", tecnicoId.toString(),
                    "Capacidad del técnico " + actual.getNombre() + ": " + capacidadAnterior + " -> " + nuevaCapacidad,
                    Map.of("tecnico_id", tecnicoId,
                            "capacidad_anterior", capacidadAnterior,
                            "capacidad_nueva", (int) nuevaCapacidad,
                            "casos_asignados", asignados));
        }

        return ResultadoCambioTecnicoDTO.builder()
                .tecnico(aSituacionTecnico(buscarTecnico(tecnicoId)))
                .casosLiberados(0)
                .casosAsignados(asignados)
                .build();
    }

    // FA04: menor a uno o no entero -> mensaje del caso de uso. El tope es el
    // de la columna (SMALLINT).
    private short validarCapacidad(JsonNode valor) {
        if (valor == null || !valor.isNumber() || !valor.canConvertToExactIntegral()) {
            throw new SolicitudInvalidaException(MENSAJE_CAPACIDAD_INVALIDA);
        }
        BigDecimal numero = valor.decimalValue();
        if (numero.compareTo(BigDecimal.ONE) < 0) {
            throw new SolicitudInvalidaException(MENSAJE_CAPACIDAD_INVALIDA);
        }
        if (numero.compareTo(BigDecimal.valueOf(Short.MAX_VALUE)) > 0) {
            throw new SolicitudInvalidaException("La capacidad indicada excede el máximo permitido.");
        }
        return numero.shortValueExact();
    }

    private CargaTecnicoProyeccion buscarTecnico(Integer tecnicoId) {
        return tecnicoRepository.obtenerCarga(tecnicoId)
                .orElseThrow(() -> new SolicitudInvalidaException("El técnico indicado no existe"));
    }

    // Las consultas nativas devuelven timestamptz como Instant (UTC); se
    // entrega con el offset de Guatemala, igual que las entidades.
    private static OffsetDateTime aGuatemala(Instant instante) {
        return instante == null ? null : instante.atZone(ZONA_GUATEMALA).toOffsetDateTime();
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    // La boleta se busca con ILIKE: % y _ del usuario se toman literales
    private static String escaparLike(String valor) {
        return valor == null ? null
                : valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private SituacionTecnicoDTO aSituacionTecnico(CargaTecnicoProyeccion t) {
        return SituacionTecnicoDTO.builder()
                .id(t.getTecnicoId())
                .nombre(t.getNombre())
                .correo(t.getCorreo())
                .especialidades(t.getEspecialidades())
                .casosAbiertos(t.getCasosAbiertos())
                .capacidadMaxima(t.getCapacidadMaxima())
                .disponible(t.getDisponible())
                .motivoNoDisponible(t.getMotivoNoDisponible())
                .build();
    }

    private String nombreCompleto(Usuario usuario) {
        return usuario == null ? "" : usuario.getNombres() + " " + usuario.getApellidos();
    }

    private ClienteContactoDTO aClienteContacto(Cliente cliente) {
        return ClienteContactoDTO.builder()
                .nombre(nombreCompleto(cliente.getUsuario()))
                .correo(cliente.getUsuario().getCorreo())
                .telefono(cliente.getUsuario().getTelefono())
                .direccion(cliente.getDireccion())
                .build();
    }

    private ServicioRecibidoDTO aServicioRecibido(OrdenServicio orden) {
        if (orden == null) {
            return null;
        }
        return ServicioRecibidoDTO.builder()
                .numeroOrden(orden.getNumeroOrden())
                .servicio(orden.getServicio().getNombre())
                .fechaServicio(orden.getFechaServicio())
                .build();
    }

    private AdjuntoDTO aAdjuntoDTO(Adjunto adjunto) {
        return AdjuntoDTO.builder()
                .id(adjunto.getId())
                .nombreArchivo(adjunto.getNombreArchivo())
                .tipoMime(adjunto.getTipoMime())
                .tamanoBytes(adjunto.getTamanoBytes())
                .fechaCarga(adjunto.getFechaCarga())
                .build();
    }

    private AvanceDTO aAvanceDTO(Seguimiento seguimiento) {
        return AvanceDTO.builder()
                .fecha(seguimiento.getFecha())
                .autor(nombreCompleto(seguimiento.getUsuario()))
                .comentario(seguimiento.getComentario())
                .estadoAnterior(seguimiento.getEstadoAnterior() != null ? seguimiento.getEstadoAnterior().getNombre() : null)
                .estadoNuevo(seguimiento.getEstadoNuevo() != null ? seguimiento.getEstadoNuevo().getNombre() : null)
                .build();
    }
}
