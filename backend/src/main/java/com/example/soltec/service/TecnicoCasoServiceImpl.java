package com.example.soltec.service;

import com.example.soltec.config.UsuarioActualProvider;
import com.example.soltec.dto.AdjuntoArchivoDTO;
import com.example.soltec.dto.AdjuntoDTO;
import com.example.soltec.dto.AvanceDTO;
import com.example.soltec.dto.CargaTecnicoDTO;
import com.example.soltec.dto.CasoTecnicoDetalleDTO;
import com.example.soltec.dto.CasoTecnicoResumenDTO;
import com.example.soltec.dto.ClienteContactoDTO;
import com.example.soltec.dto.RegistrarAvanceRequest;
import com.example.soltec.dto.ServicioRecibidoDTO;
import com.example.soltec.dto.TransicionDTO;
import com.example.soltec.entity.Adjunto;
import com.example.soltec.entity.Caso;
import com.example.soltec.entity.Cliente;
import com.example.soltec.entity.EstadoCaso;
import com.example.soltec.entity.OrdenServicio;
import com.example.soltec.entity.Seguimiento;
import com.example.soltec.entity.Tecnico;
import com.example.soltec.entity.Usuario;
import com.example.soltec.exception.AccesoNoAutorizadoException;
import com.example.soltec.exception.SolicitudInvalidaException;
import com.example.soltec.repository.AdjuntoRepository;
import com.example.soltec.repository.CasoRepository;
import com.example.soltec.repository.ClienteRepository;
import com.example.soltec.repository.EstadoCasoRepository;
import com.example.soltec.repository.SeguimientoRepository;
import com.example.soltec.repository.TecnicoRepository;
import com.example.soltec.repository.UsuarioRepository;
import com.example.soltec.storage.AlmacenamientoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TecnicoCasoServiceImpl implements TecnicoCasoService {

    // CU-06: tabla de transiciones permitidas. ESCALADO/CERRADO/IMPROCEDENTE
    // no tienen salida (el caso ya no lo maneja el tecnico).
    private static final Map<String, List<String>> TRANSICIONES = Map.of(
            "ASIGNADO",     List.of("EN_PROCESO", "RESUELTO", "ESCALADO", "IMPROCEDENTE"),
            "EN_PROCESO",   List.of("RESUELTO", "ESCALADO", "IMPROCEDENTE"),
            "RESUELTO",     List.of("CERRADO", "EN_PROCESO"),
            "ESCALADO",     List.of(),
            "CERRADO",      List.of(),
            "IMPROCEDENTE", List.of()
    );

    // Al llegar a cualquiera de estos, el tecnico libera capacidad.
    private static final Set<String> LIBERA_CAPACIDAD = Set.of("CERRADO", "IMPROCEDENTE", "ESCALADO");
    private static final String CODIGO_RESUELTO = "RESUELTO";
    private static final String CODIGO_ESCALADO = "ESCALADO";

    private final CasoRepository casoRepository;
    private final ClienteRepository clienteRepository;
    private final AdjuntoRepository adjuntoRepository;
    private final SeguimientoRepository seguimientoRepository;
    private final EstadoCasoRepository estadoCasoRepository;
    private final TecnicoRepository tecnicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlmacenamientoService almacenamientoService;
    private final BitacoraService bitacoraService;
    private final UsuarioActualProvider usuarioActualProvider;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<CasoTecnicoResumenDTO> listarAsignadosActuales() {
        Integer tecnicoId = usuarioActualProvider.obtener().getId();
        List<Caso> casos = casoRepository.findAsignadosActivos(tecnicoId);

        Map<Integer, Usuario> clientes = usuarioRepository
                .findAllById(casos.stream().map(Caso::getClienteId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Usuario::getId, u -> u));

        return casos.stream()
                .map(c -> CasoTecnicoResumenDTO.builder()
                        .id(c.getId())
                        .numeroBoleta(c.getNumeroBoleta())
                        .tipo(c.getTipoSolicitud().getNombre())
                        .asunto(c.getAsunto())
                        .prioridad(c.getPrioridad().getNombre())
                        .estado(c.getEstado().getNombre())
                        .cliente(nombreCompleto(clientes.get(c.getClienteId())))
                        .fechaRegistro(c.getFechaRegistro())
                        .fechaLimiteResolucion(c.getFechaLimiteResolucion())
                        .build())
                .toList();
    }

    @Override
    public CargaTecnicoDTO obtenerCarga() {
        Usuario usuario = usuarioActualProvider.obtener();
        Tecnico tecnico = tecnicoRepository.findById(usuario.getId())
                .orElseThrow(() -> new IllegalStateException("El usuario autenticado no es un tecnico registrado"));

        // Misma definicion de "abierto" que usa fn_calcular_score: NOT es_final.
        long abiertos = casoRepository.countByTecnicoAsignadoIdAndEstado_EsFinalFalse(usuario.getId());

        return CargaTecnicoDTO.builder()
                .casosAbiertos(abiertos)
                .capacidadMaxima(tecnico.getCapacidadMaxima())
                .disponible(tecnico.getDisponible())
                .build();
    }

    @Override
    @Transactional
    public CasoTecnicoDetalleDTO obtenerDetalle(Integer casoId, String direccionIp) {
        Usuario tecnico = usuarioActualProvider.obtener();
        Caso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("El caso indicado no existe"));

        validarPertenencia(caso, tecnico, direccionIp);

        Cliente cliente = clienteRepository.findById(caso.getClienteId())
                .orElseThrow(() -> new IllegalStateException("Cliente no encontrado para el caso " + casoId));

        List<AdjuntoDTO> adjuntos = adjuntoRepository.findByCasoIdOrderByIdAsc(casoId).stream()
                .map(this::aAdjuntoDTO)
                .toList();

        List<AvanceDTO> avances = seguimientoRepository.findByCasoIdOrderByFechaDesc(casoId).stream()
                .map(this::aAvanceDTO)
                .toList();

        List<TransicionDTO> transiciones = TRANSICIONES.getOrDefault(caso.getEstado().getCodigo(), List.of())
                .stream()
                .map(codigo -> estadoCasoRepository.findByCodigo(codigo).orElse(null))
                .filter(Objects::nonNull)
                .map(e -> TransicionDTO.builder().codigo(e.getCodigo()).nombre(e.getNombre()).build())
                .toList();

        return CasoTecnicoDetalleDTO.builder()
                .numeroBoleta(caso.getNumeroBoleta())
                .tipo(caso.getTipoSolicitud().getNombre())
                .estado(caso.getEstado().getNombre())
                .prioridad(caso.getPrioridad().getNombre())
                .asunto(caso.getAsunto())
                .descripcion(caso.getDescripcion())
                .fechaRegistro(caso.getFechaRegistro())
                .fechaLimiteResolucion(caso.getFechaLimiteResolucion())
                .cliente(aClienteContacto(cliente))
                .servicioRecibido(aServicioRecibido(caso.getOrdenServicio()))
                .adjuntos(adjuntos)
                .avances(avances)
                .transicionesPermitidas(transiciones)
                .build();
    }

    @Override
    public AdjuntoArchivoDTO descargarAdjunto(Integer casoId, Integer adjuntoId, String direccionIp) throws IOException {
        Usuario tecnico = usuarioActualProvider.obtener();
        Caso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("El caso indicado no existe"));

        validarPertenencia(caso, tecnico, direccionIp);

        Adjunto adjunto = adjuntoRepository.findByIdAndCasoId(adjuntoId, casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("El adjunto indicado no existe"));

        Resource recurso = almacenamientoService.recuperar(adjunto.getRuta());

        return AdjuntoArchivoDTO.builder()
                .nombreArchivo(adjunto.getNombreArchivo())
                .tipoMime(adjunto.getTipoMime())
                .recurso(recurso)
                .build();
    }

    @Override
    @Transactional
    public void registrarAvance(Integer casoId, RegistrarAvanceRequest request, String direccionIp) {
        Usuario tecnico = usuarioActualProvider.obtener();
        Caso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("El caso indicado no existe"));

        validarPertenencia(caso, tecnico, direccionIp);

        EstadoCaso estadoActual = caso.getEstado();
        EstadoCaso nuevoEstado = null;
        String nuevoCodigo = request.getNuevoEstado();

        if (nuevoCodigo != null && !nuevoCodigo.isBlank()) {
            List<String> permitidas = TRANSICIONES.getOrDefault(estadoActual.getCodigo(), List.of());
            if (!permitidas.contains(nuevoCodigo)) {
                throw new SolicitudInvalidaException(
                        "No se puede pasar de " + estadoActual.getNombre() + " a ese estado.");
            }
            nuevoEstado = estadoCasoRepository.findByCodigo(nuevoCodigo)
                    .orElseThrow(() -> new SolicitudInvalidaException("El estado indicado no existe"));

            aplicarTransicion(casoId, nuevoEstado, nuevoCodigo, request.getComentario());
        }

        // Los avances del tecnico son internos: el cliente nunca los ve
        // (visible_cliente = false). Lo unico que el cliente llega a ver es
        // caso.solucion, que aplicarTransicion ya llena cuando pasa a RESUELTO.
        Seguimiento seguimiento = Seguimiento.builder()
                .casoId(casoId)
                .usuario(tecnico)
                .estadoAnterior(estadoActual)
                .estadoNuevo(nuevoEstado)
                .comentario(request.getComentario())
                .visibleCliente(false)
                .build();
        seguimientoRepository.save(seguimiento);

        String descripcion = nuevoEstado != null
                ? "Avance del caso " + casoId + ": " + estadoActual.getCodigo() + " -> " + nuevoEstado.getCodigo()
                : "Avance del caso " + casoId + " sin cambio de estado";

        bitacoraService.registrar(tecnico.getId(), direccionIp, "CASOS", "REGISTRAR_AVANCE", "caso",
                casoId.toString(), descripcion,
                Map.of("estadoAnterior", estadoActual.getCodigo(),
                        "estadoNuevo", nuevoEstado != null ? nuevoEstado.getCodigo() : estadoActual.getCodigo()));
    }

    // estado_id, fecha_resolucion, fecha_cierre y tecnico_asignado_id estan
    // mapeados insertable=false/updatable=false en Caso (CLAUDE.md: son
    // campos que normalmente llenan los triggers). Aqui el dueno del cambio
    // es el tecnico, no un trigger, asi que se escribe con UPDATE nativo.
    // Ese UPDATE es lo que dispara tg_caso_after_update_liberar (07): si
    // libera capacidad, la base ya intenta asignar el siguiente caso de la
    // bolsa dentro de esta misma transaccion.
    private void aplicarTransicion(Integer casoId, EstadoCaso nuevoEstado, String nuevoCodigo, String comentario) {
        boolean esResuelto = CODIGO_RESUELTO.equals(nuevoCodigo);
        boolean liberaCapacidad = LIBERA_CAPACIDAD.contains(nuevoCodigo);
        boolean liberaTecnico = CODIGO_ESCALADO.equals(nuevoCodigo);

        entityManager.createNativeQuery("""
                UPDATE soltec.caso
                   SET estado_id = :estadoId,
                       solucion = CASE WHEN :esResuelto THEN :comentario ELSE solucion END,
                       fecha_resolucion = CASE WHEN :esResuelto THEN NOW() ELSE fecha_resolucion END,
                       fecha_cierre = CASE WHEN :liberaCapacidad THEN NOW() ELSE fecha_cierre END,
                       tecnico_asignado_id = CASE WHEN :liberaTecnico THEN NULL ELSE tecnico_asignado_id END
                 WHERE id = :casoId
                """)
                .setParameter("estadoId", nuevoEstado.getId())
                .setParameter("esResuelto", esResuelto)
                .setParameter("comentario", comentario)
                .setParameter("liberaCapacidad", liberaCapacidad)
                .setParameter("liberaTecnico", liberaTecnico)
                .setParameter("casoId", casoId)
                .executeUpdate();
    }

    // RN de seguridad: un caso solo lo puede consultar/gestionar el tecnico
    // al que esta asignado. El intento ajeno queda en bitacora.
    private void validarPertenencia(Caso caso, Usuario tecnico, String direccionIp) {
        if (caso.getTecnicoAsignadoId() != null && caso.getTecnicoAsignadoId().equals(tecnico.getId())) {
            return;
        }
        bitacoraService.registrarAudita(tecnico.getId(), direccionIp, "CASOS", "ACCESO_DENEGADO", "caso",
                caso.getId().toString(), "Intento de acceso no autorizado al caso " + caso.getId(),
                Map.of("casoId", caso.getId()));
        throw new AccesoNoAutorizadoException("No tiene autorización para consultar este caso.");
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
