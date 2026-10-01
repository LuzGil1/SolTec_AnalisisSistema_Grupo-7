package com.example.soltec.service;

import com.example.soltec.config.UsuarioActualProvider;
import com.example.soltec.dto.AgregarInvolucradoRequest;
import com.example.soltec.dto.CasoSupervisorDetalleDTO;
import com.example.soltec.dto.DenunciaResumenDTO;
import com.example.soltec.dto.EscalamientoResumenDTO;
import com.example.soltec.dto.PersonalDTO;
import com.example.soltec.dto.RegistrarAvanceRequest;
import com.example.soltec.dto.ResponderSugerenciaRequest;
import com.example.soltec.dto.ResultadoDevolucionDTO;
import com.example.soltec.dto.SugerenciaResumenDTO;
import com.example.soltec.dto.TransicionDTO;
import com.example.soltec.entity.Caso;
import com.example.soltec.entity.CasoInvolucrado;
import com.example.soltec.entity.EstadoCaso;
import com.example.soltec.entity.Seguimiento;
import com.example.soltec.entity.Usuario;
import com.example.soltec.exception.SolicitudInvalidaException;
import com.example.soltec.repository.CasoInvolucradoRepository;
import com.example.soltec.repository.CasoRepository;
import com.example.soltec.repository.EstadoCasoRepository;
import com.example.soltec.repository.SeguimientoRepository;
import com.example.soltec.repository.UsuarioRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// El supervisor es la ultima instancia: ninguna de sus transiciones lleva a
// ESCALADO. Los avances que redacta son internos (visible_cliente = false);
// lo unico que el cliente llega a conocer es caso.solucion, que se llena al
// resolver una denuncia o un escalamiento y al responder una sugerencia.
@Service
@RequiredArgsConstructor
public class SupervisorAtencionServiceImpl implements SupervisorAtencionService {

    private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");

    // RN06 del CU Atencion de Denuncias. CERRADO e IMPROCEDENTE no tienen
    // salida: la denuncia concluyo.
    private static final Map<String, List<String>> TRANSICIONES_DENUNCIA = Map.of(
            "EN_REVISION", List.of("EN_PROCESO", "IMPROCEDENTE"),
            "EN_PROCESO",  List.of("RESUELTO", "IMPROCEDENTE"),
            "RESUELTO",    List.of("CERRADO", "EN_PROCESO")
    );

    // CU Revision de Escalamientos. Desde ESCALADO solo se declara
    // improcedente (FA03) por esta via: devolver (FA01) y atender (FA02)
    // tienen su propio endpoint. Una vez que el supervisor la atiende, son
    // las del tecnico en el CU-06 sin ESCALADO.
    private static final Map<String, List<String>> TRANSICIONES_ESCALAMIENTO = Map.of(
            "ESCALADO",   List.of("IMPROCEDENTE"),
            "EN_PROCESO", List.of("RESUELTO", "IMPROCEDENTE"),
            "RESUELTO",   List.of("CERRADO", "EN_PROCESO")
    );

    private static final Set<String> CONCLUYEN = Set.of("CERRADO", "IMPROCEDENTE");
    private static final String RESUELTO = "RESUELTO";
    private static final String ESCALADO = "ESCALADO";
    private static final String MENSAJE_SIN_AVANCE = "Escriba el avance antes de continuar.";

    private final CasoRepository casoRepository;
    private final CasoInvolucradoRepository casoInvolucradoRepository;
    private final EstadoCasoRepository estadoCasoRepository;
    private final SeguimientoRepository seguimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SupervisorService supervisorService;
    private final BitacoraService bitacoraService;
    private final UsuarioActualProvider usuarioActualProvider;

    // =================================================================
    // Denuncias
    // =================================================================

    @Override
    public List<DenunciaResumenDTO> listarDenuncias() {
        return casoRepository.listarDenuncias().stream()
                .map(d -> DenunciaResumenDTO.builder()
                        .id(d.getCasoId())
                        .numeroBoleta(d.getNumeroBoleta())
                        .asunto(d.getAsunto())
                        .cliente(d.getCliente())
                        .involucrados(d.getInvolucrados())
                        .estadoCodigo(d.getEstadoCodigo())
                        .estado(d.getEstado())
                        .fechaRegistro(aGuatemala(d.getFechaRegistro()))
                        .fechaLimite(aGuatemala(d.getFechaLimite()))
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public CasoSupervisorDetalleDTO obtenerDenuncia(Integer casoId, String direccionIp) {
        Caso caso = buscarDenuncia(casoId);
        CasoSupervisorDetalleDTO detalle = supervisorService.obtenerDetalle(casoId, direccionIp);
        detalle.setTransicionesPermitidas(transiciones(TRANSICIONES_DENUNCIA, caso.getEstado().getCodigo()));
        return detalle;
    }

    @Override
    @Transactional
    public void registrarAvanceDenuncia(Integer casoId, RegistrarAvanceRequest request, String direccionIp) {
        Caso caso = casoRepository.bloquearPorId(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("La denuncia indicada no existe"));
        validarEsDenuncia(caso);
        if (Boolean.TRUE.equals(caso.getEstado().getEsFinal())) {
            throw new SolicitudInvalidaException("La denuncia ya concluyó: no admite más gestiones.");
        }

        trasladar(caso, TRANSICIONES_DENUNCIA, "la denuncia", request.getNuevoEstado(), request.getComentario(),
                direccionIp, "DENUNCIAS", "REGISTRAR_AVANCE");
    }

    // FA05 / RN04: se registra personal adicional; nunca se retira (no hay
    // operacion inversa).
    @Override
    @Transactional
    public void agregarInvolucrado(Integer casoId, AgregarInvolucradoRequest request, String direccionIp) {
        Caso caso = casoRepository.bloquearPorId(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("La denuncia indicada no existe"));
        validarEsDenuncia(caso);
        if (Boolean.TRUE.equals(caso.getEstado().getEsFinal())) {
            throw new SolicitudInvalidaException("La denuncia ya concluyó: no se puede registrar personal involucrado.");
        }

        Usuario involucrado = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new SolicitudInvalidaException("El usuario indicado no existe"));
        if (!Boolean.TRUE.equals(involucrado.getActivo()) || "CLIENTE".equals(involucrado.getRol().getCodigo())) {
            throw new SolicitudInvalidaException("Solo se puede registrar como involucrado al personal de SolTec.");
        }
        if (casoInvolucradoRepository.existsByCasoIdAndUsuarioId(casoId, involucrado.getId())) {
            throw new SolicitudInvalidaException(nombreCompleto(involucrado)
                    + " ya está registrado como involucrado en esta denuncia.");
        }

        String motivo = request.getMotivo().trim();
        casoInvolucradoRepository.save(CasoInvolucrado.builder()
                .casoId(casoId)
                .usuarioId(involucrado.getId())
                .motivo(motivo)
                .build());

        Usuario supervisor = usuarioActualProvider.obtener();
        bitacoraService.registrar(supervisor.getId(), direccionIp, "DENUNCIAS", "REGISTRAR_INVOLUCRADO", "caso",
                casoId.toString(),
                "Registro de " + nombreCompleto(involucrado) + " como involucrado en la denuncia " + caso.getNumeroBoleta(),
                Map.of("usuario_id", involucrado.getId(), "motivo", motivo));
    }

    @Override
    public List<PersonalDTO> listarPersonal() {
        return usuarioRepository.listarPersonal().stream()
                .map(p -> PersonalDTO.builder().id(p.getId()).nombre(p.getNombre()).rol(p.getRol()).build())
                .toList();
    }

    // =================================================================
    // Escalamientos
    // =================================================================

    @Override
    public List<EscalamientoResumenDTO> listarEscalamientos() {
        return casoRepository.listarEscalamientos().stream()
                .map(e -> EscalamientoResumenDTO.builder()
                        .id(e.getCasoId())
                        .numeroBoleta(e.getNumeroBoleta())
                        .tipo(e.getTipo())
                        .asunto(e.getAsunto())
                        .cliente(e.getCliente())
                        .escaladaPor(e.getEscaladaPor())
                        .motivoEscalamiento(e.getMotivoEscalamiento())
                        .estadoCodigo(e.getEstadoCodigo())
                        .estado(e.getEstado())
                        .enAtencionSupervisor(e.getSupervisorResponsableId() != null)
                        .supervisorResponsable(e.getSupervisorResponsable())
                        .fechaLimite(aGuatemala(e.getFechaLimite()))
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public CasoSupervisorDetalleDTO obtenerEscalamiento(Integer casoId, String direccionIp) {
        Caso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("La solicitud indicada no existe"));
        validarEsEscalamiento(caso);

        CasoSupervisorDetalleDTO detalle = supervisorService.obtenerDetalle(casoId, direccionIp);
        detalle.setTransicionesPermitidas(Boolean.TRUE.equals(caso.getEstado().getEsFinal())
                ? List.of()
                : transiciones(TRANSICIONES_ESCALAMIENTO, caso.getEstado().getCodigo()));
        return detalle;
    }

    // FA01 / RN04: toda la operacion (exclusion del tecnico, vuelta a la
    // bolsa, seguimiento, bitacora y reasignacion) es fn_devolver_escalamiento;
    // sus RAISE llegan como 400 por el GlobalExceptionHandler.
    @Override
    @Transactional
    public ResultadoDevolucionDTO devolverEscalamiento(Integer casoId, String comentario, String direccionIp) {
        Usuario supervisor = usuarioActualProvider.obtener();
        Integer tecnicoId = casoRepository.devolverEscalamiento(casoId, supervisor.getId(), comentario, direccionIp);

        String tecnico = tecnicoId == null ? null
                : usuarioRepository.findById(tecnicoId).map(this::nombreCompleto).orElse(null);
        return ResultadoDevolucionDTO.builder().tecnicoAsignado(tecnico).build();
    }

    // FA02 / RN05: el supervisor queda como responsable y tecnico_asignado_id
    // sigue en NULL, asi que el caso no computa en la capacidad de nadie.
    @Override
    @Transactional
    public void atenderEscalamiento(Integer casoId, String comentario, String direccionIp) {
        Caso caso = casoRepository.bloquearPorId(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("La solicitud indicada no existe"));
        if (!ESCALADO.equals(caso.getEstado().getCodigo())) {
            throw new SolicitudInvalidaException("La solicitud no se encuentra escalada: no se puede tomar su atención.");
        }
        String texto = exigirTexto(comentario, MENSAJE_SIN_AVANCE);

        Usuario supervisor = usuarioActualProvider.obtener();
        EstadoCaso enProceso = estado("EN_PROCESO");

        // reabrir: el CU-06 llena fecha_cierre al escalar, y el caso vuelve a
        // estar en atencion
        casoRepository.registrarTraslado(casoId, enProceso.getId(), false, null, false,
                false, true, true, supervisor.getId());
        guardarSeguimiento(casoId, supervisor, caso.getEstado(), enProceso, texto);

        bitacoraService.registrar(supervisor.getId(), direccionIp, "ESCALAMIENTOS", "ATENDER", "caso",
                casoId.toString(), "El supervisor toma la atención de la solicitud " + caso.getNumeroBoleta(),
                Map.of("estadoAnterior", ESCALADO, "estadoNuevo", enProceso.getCodigo()));
    }

    @Override
    @Transactional
    public void registrarAvanceEscalamiento(Integer casoId, RegistrarAvanceRequest request, String direccionIp) {
        Caso caso = casoRepository.bloquearPorId(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("La solicitud indicada no existe"));
        validarEsEscalamiento(caso);
        if (Boolean.TRUE.equals(caso.getEstado().getEsFinal())) {
            throw new SolicitudInvalidaException("La solicitud ya concluyó: no admite más gestiones.");
        }

        trasladar(caso, TRANSICIONES_ESCALAMIENTO, "la solicitud", request.getNuevoEstado(), request.getComentario(),
                direccionIp, "ESCALAMIENTOS", "REGISTRAR_AVANCE");
    }

    // =================================================================
    // Sugerencias
    // =================================================================

    @Override
    public List<SugerenciaResumenDTO> listarSugerencias() {
        return casoRepository.listarSugerencias().stream()
                .map(s -> SugerenciaResumenDTO.builder()
                        .id(s.getCasoId())
                        .numeroBoleta(s.getNumeroBoleta())
                        .asunto(s.getAsunto())
                        .cliente(s.getCliente())
                        .estadoCodigo(s.getEstadoCodigo())
                        .estado(s.getEstado())
                        .respondida(s.getEsFinal())
                        .fechaRegistro(aGuatemala(s.getFechaRegistro()))
                        .build())
                .toList();
    }

    // RN04: termina en CERRADO se acoja o no; "acogida" solo queda en el
    // avance y en la bitacora. La respuesta va a caso.solucion, que es lo
    // que el cliente ve en su panel.
    @Override
    @Transactional
    public void responderSugerencia(Integer casoId, ResponderSugerenciaRequest request, String direccionIp) {
        Caso caso = casoRepository.bloquearPorId(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("La sugerencia indicada no existe"));
        if (!"SUGERENCIA".equals(caso.getTipoSolicitud().getCodigo())) {
            throw new SolicitudInvalidaException("La solicitud indicada no es una sugerencia.");
        }
        if (Boolean.TRUE.equals(caso.getEstado().getEsFinal())) {
            throw new SolicitudInvalidaException("La sugerencia ya fue respondida.");
        }
        String respuesta = exigirTexto(request.getRespuesta(), "Escriba la respuesta antes de continuar.");
        boolean acogida = request.getAcogida();

        Usuario supervisor = usuarioActualProvider.obtener();
        EstadoCaso cerrado = estado("CERRADO");

        casoRepository.registrarTraslado(casoId, cerrado.getId(), true, respuesta, true,
                true, false, false, supervisor.getId());
        guardarSeguimiento(casoId, supervisor, caso.getEstado(), cerrado,
                (acogida ? "Sugerencia acogida. " : "Sugerencia no acogida. ") + respuesta);

        bitacoraService.registrar(supervisor.getId(), direccionIp, "SUGERENCIAS", "RESPONDER", "caso",
                casoId.toString(),
                "Respuesta a la sugerencia " + caso.getNumeroBoleta() + (acogida ? " (acogida)" : " (no acogida)"),
                Map.of("acogida", acogida));
    }

    // =================================================================
    // Apoyo
    // =================================================================

    // Traslado comun de denuncias y escalamientos. nuevoCodigo vacio =
    // registrar el avance sin mover el caso (RN09: queda en el historial).
    private void trasladar(Caso caso, Map<String, List<String>> tabla, String etiqueta, String nuevoCodigo,
                           String comentario, String direccionIp, String modulo, String accion) {
        String texto = exigirTexto(comentario, MENSAJE_SIN_AVANCE);
        EstadoCaso actual = caso.getEstado();
        EstadoCaso nuevo = null;

        if (nuevoCodigo != null && !nuevoCodigo.isBlank()) {
            String codigo = nuevoCodigo.trim();
            if (!tabla.getOrDefault(actual.getCodigo(), List.of()).contains(codigo)) {
                String destino = estadoCasoRepository.findByCodigo(codigo).map(EstadoCaso::getNombre).orElse(codigo);
                throw new SolicitudInvalidaException("No se puede trasladar " + etiqueta + " de "
                        + actual.getNombre() + " a " + destino + ".");
            }
            nuevo = estado(codigo);
            boolean resuelto = RESUELTO.equals(codigo);

            // RN07: al resolver, el avance se conserva como la resolucion
            casoRepository.registrarTraslado(caso.getId(), nuevo.getId(), resuelto, texto, resuelto,
                    CONCLUYEN.contains(codigo), false, false, null);
        }

        Usuario supervisor = usuarioActualProvider.obtener();
        guardarSeguimiento(caso.getId(), supervisor, actual, nuevo, texto);

        Map<String, Object> datos = new HashMap<>();
        datos.put("estadoAnterior", actual.getCodigo());
        datos.put("estadoNuevo", nuevo != null ? nuevo.getCodigo() : actual.getCodigo());
        bitacoraService.registrar(supervisor.getId(), direccionIp, modulo, accion, "caso", caso.getId().toString(),
                nuevo != null
                        ? "Avance de " + caso.getNumeroBoleta() + ": " + actual.getCodigo() + " -> " + nuevo.getCodigo()
                        : "Avance de " + caso.getNumeroBoleta() + " sin cambio de estado",
                datos);
    }

    private void guardarSeguimiento(Integer casoId, Usuario autor, EstadoCaso anterior, EstadoCaso nuevo, String texto) {
        seguimientoRepository.save(Seguimiento.builder()
                .casoId(casoId)
                .usuario(autor)
                .estadoAnterior(anterior)
                .estadoNuevo(nuevo)
                .comentario(texto)
                .visibleCliente(false)
                .build());
    }

    private Caso buscarDenuncia(Integer casoId) {
        Caso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("La denuncia indicada no existe"));
        validarEsDenuncia(caso);
        return caso;
    }

    private void validarEsDenuncia(Caso caso) {
        if (!"DENUNCIA".equals(caso.getTipoSolicitud().getCodigo())) {
            throw new SolicitudInvalidaException("La solicitud indicada no es una denuncia.");
        }
    }

    // RN03 del CU Dashboard: el supervisor no modifica la atencion de un
    // caso que tiene un tecnico. Solo actua sobre los ESCALADO y los que el
    // mismo tomo.
    private void validarEsEscalamiento(Caso caso) {
        if (!ESCALADO.equals(caso.getEstado().getCodigo()) && caso.getSupervisorResponsableId() == null) {
            throw new SolicitudInvalidaException("La solicitud no está escalada ni en atención del supervisor.");
        }
    }

    private List<TransicionDTO> transiciones(Map<String, List<String>> tabla, String codigoActual) {
        return tabla.getOrDefault(codigoActual, List.of()).stream()
                .map(codigo -> estadoCasoRepository.findByCodigo(codigo).orElse(null))
                .filter(Objects::nonNull)
                .map(e -> TransicionDTO.builder().codigo(e.getCodigo()).nombre(e.getNombre()).build())
                .toList();
    }

    private EstadoCaso estado(String codigo) {
        return estadoCasoRepository.findByCodigo(codigo)
                .orElseThrow(() -> new IllegalStateException("Estado no configurado: " + codigo));
    }

    private static String exigirTexto(String texto, String mensaje) {
        if (texto == null || texto.isBlank()) {
            throw new SolicitudInvalidaException(mensaje);
        }
        return texto.trim();
    }

    private static OffsetDateTime aGuatemala(Instant instante) {
        return instante == null ? null : instante.atZone(ZONA_GUATEMALA).toOffsetDateTime();
    }

    private String nombreCompleto(Usuario usuario) {
        return usuario.getNombres() + " " + usuario.getApellidos();
    }
}
