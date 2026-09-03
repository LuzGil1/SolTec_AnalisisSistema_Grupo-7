package com.example.soltec.service;

import com.example.soltec.config.UsuarioActualProvider;
import com.example.soltec.dto.AdjuntoArchivoDTO;
import com.example.soltec.dto.AdjuntoDTO;
import com.example.soltec.dto.CasoCreadoDTO;
import com.example.soltec.dto.CasoDetalleDTO;
import com.example.soltec.dto.CasoResumenDTO;
import com.example.soltec.dto.NuevaSolicitudRequest;
import com.example.soltec.dto.ServicioRecibidoDTO;
import com.example.soltec.entity.Adjunto;
import com.example.soltec.entity.Caso;
import com.example.soltec.entity.OrdenServicio;
import com.example.soltec.entity.TipoSolicitud;
import com.example.soltec.entity.Usuario;
import com.example.soltec.exception.AccesoNoAutorizadoException;
import com.example.soltec.exception.SolicitudInvalidaException;
import com.example.soltec.repository.AdjuntoRepository;
import com.example.soltec.repository.CasoRepository;
import com.example.soltec.repository.OrdenServicioRepository;
import com.example.soltec.repository.TipoSolicitudRepository;
import com.example.soltec.storage.AlmacenamientoService;
import com.example.soltec.storage.ArchivoGuardado;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CasoServiceImpl implements CasoService {

    private static final String CODIGO_DENUNCIA = "DENUNCIA";
    private static final String MENSAJE_ARCHIVO_INVALIDO =
            "El archivo no pudo adjuntarse. Verifique que el formato sea válido y que no exceda el tamaño máximo permitido.";

    private final CasoRepository casoRepository;
    private final TipoSolicitudRepository tipoSolicitudRepository;
    private final OrdenServicioRepository ordenServicioRepository;
    private final AdjuntoRepository adjuntoRepository;
    private final ParametroService parametroService;
    private final AlmacenamientoService almacenamientoService;
    private final BitacoraService bitacoraService;
    private final UsuarioActualProvider usuarioActualProvider;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<CasoResumenDTO> listarDelClienteActual() {
        Integer clienteId = usuarioActualProvider.obtener().getId();
        return casoRepository.findByClienteIdOrderByFechaRegistroDesc(clienteId).stream()
                .map(this::aResumen)
                .toList();
    }

    @Override
    @Transactional
    public CasoCreadoDTO registrar(NuevaSolicitudRequest request, String direccionIp) {
        Usuario usuario = usuarioActualProvider.obtener();
        Integer clienteId = usuario.getId();

        TipoSolicitud tipo = tipoSolicitudRepository.findById(request.getTipoSolicitudId())
                .filter(TipoSolicitud::getActivo)
                .orElseThrow(() -> new SolicitudInvalidaException("El tipo de solicitud indicado no existe o esta inactivo"));

        // RN05: la denuncia exige indicar el servicio al que se refiere.
        // Esta es la unica validacion de negocio que no cubre un trigger.
        if (CODIGO_DENUNCIA.equals(tipo.getCodigo()) && request.getOrdenServicioId() == null) {
            throw new SolicitudInvalidaException("Debe seleccionar el servicio al que se refiere la denuncia.");
        }

        Caso caso = Caso.builder()
                .clienteId(clienteId)
                .tipoSolicitud(tipo)
                .asunto(request.getAsunto())
                .descripcion(request.getDescripcion())
                .build();

        if (request.getOrdenServicioId() != null) {
            OrdenServicio orden = ordenServicioRepository.findById(request.getOrdenServicioId())
                    .orElseThrow(() -> new SolicitudInvalidaException("La orden de servicio indicada no existe"));
            caso.setOrdenServicio(orden);
            caso.setServicio(orden.getServicio());
        }

        // El INSERT dispara los triggers de la base: numero_boleta, ruteo por
        // tipo (estado_id), prioridad por defecto y fechas limite de SLA.
        // El conflicto de interes en denuncias tambien lo arma un trigger.
        caso = casoRepository.save(caso);
        entityManager.refresh(caso);

        bitacoraService.registrar(clienteId, direccionIp, "CASOS", "REGISTRO_SOLICITUD", "caso",
                caso.getId().toString(),
                "Registro de solicitud " + caso.getNumeroBoleta() + " (" + tipo.getNombre() + ")",
                Map.of("numeroBoleta", caso.getNumeroBoleta(), "tipo", tipo.getCodigo()));

        return CasoCreadoDTO.builder()
                .id(caso.getId())
                .numeroBoleta(caso.getNumeroBoleta())
                .estado(caso.getEstado().getNombre())
                .fechaLimiteResolucion(caso.getFechaLimiteResolucion())
                .build();
    }

    @Override
    @Transactional
    public AdjuntoDTO adjuntarEvidencia(Integer casoId, MultipartFile archivo) throws IOException {
        Usuario usuario = usuarioActualProvider.obtener();
        Caso caso = casoRepository.findByIdAndClienteId(casoId, usuario.getId())
                .orElseThrow(() -> new SolicitudInvalidaException("El caso indicado no existe o no le pertenece"));

        validarArchivo(archivo);

        ArchivoGuardado guardado = almacenamientoService.guardar(archivo, caso.getId());

        Adjunto adjunto = Adjunto.builder()
                .casoId(caso.getId())
                .nombreArchivo(archivo.getOriginalFilename())
                .tipoMime(guardado.tipoMimeDetectado())
                .tamanoBytes(archivo.getSize())
                .ruta(guardado.ruta())
                .hashSha256(guardado.hashSha256())
                .usuarioId(usuario.getId())
                .build();
        adjunto = adjuntoRepository.save(adjunto);
        entityManager.refresh(adjunto);

        return AdjuntoDTO.builder()
                .id(adjunto.getId())
                .nombreArchivo(adjunto.getNombreArchivo())
                .tipoMime(adjunto.getTipoMime())
                .tamanoBytes(adjunto.getTamanoBytes())
                .fechaCarga(adjunto.getFechaCarga())
                .build();
    }

    private void validarArchivo(MultipartFile archivo) {
        if (archivo == null) {
            throw new SolicitudInvalidaException("Debe seleccionar un archivo");
        }

        // El rechazo de archivos vacios o menores a 1 KB vive en
        // AlmacenamientoService.guardar(), que es quien lee el contenido real.
        String tipoMime = archivo.getContentType();
        boolean formatoValido = tipoMime != null && (tipoMime.startsWith("image/")
                || tipoMime.startsWith("audio/")
                || tipoMime.startsWith("video/")
                || tipoMime.equals("application/pdf"));
        if (!formatoValido) {
            throw new SolicitudInvalidaException(MENSAJE_ARCHIVO_INVALIDO);
        }

        if (archivo.getSize() > parametroService.obtenerMaxBytesAdjunto()) {
            throw new SolicitudInvalidaException(parametroService.mensajeArchivoExcedeTamano());
        }
    }

    @Override
    @Transactional
    public CasoDetalleDTO obtenerDetalle(Integer casoId, String direccionIp) {
        Usuario usuario = usuarioActualProvider.obtener();
        Caso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("La solicitud indicada no existe"));

        validarPertenencia(caso, usuario, direccionIp, "caso", casoId.toString(),
                "Intento de acceso no autorizado al detalle de la solicitud " + casoId);

        List<AdjuntoDTO> adjuntos = adjuntoRepository.findByCasoIdOrderByIdAsc(caso.getId()).stream()
                .map(this::aAdjuntoDTO)
                .toList();

        return CasoDetalleDTO.builder()
                .numeroBoleta(caso.getNumeroBoleta())
                .tipo(caso.getTipoSolicitud().getNombre())
                .estado(caso.getEstado().getNombre())
                .fechaRegistro(caso.getFechaRegistro())
                .asunto(caso.getAsunto())
                .descripcion(caso.getDescripcion())
                .servicioRecibido(aServicioRecibido(caso.getOrdenServicio()))
                .fechaLimiteResolucion(caso.getFechaLimiteResolucion())
                .adjuntos(adjuntos)
                .solucion(caso.getSolucion())
                .build();
    }

    @Override
    @Transactional
    public AdjuntoArchivoDTO descargarAdjunto(Integer casoId, Integer adjuntoId, String direccionIp) throws IOException {
        Usuario usuario = usuarioActualProvider.obtener();
        Caso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("La solicitud indicada no existe"));

        validarPertenencia(caso, usuario, direccionIp, "adjunto", adjuntoId.toString(),
                "Intento de acceso no autorizado a un adjunto de la solicitud " + casoId);

        Adjunto adjunto = adjuntoRepository.findByIdAndCasoId(adjuntoId, casoId)
                .orElseThrow(() -> new SolicitudInvalidaException("El adjunto indicado no existe"));

        Resource recurso = almacenamientoService.recuperar(adjunto.getRuta());

        return AdjuntoArchivoDTO.builder()
                .nombreArchivo(adjunto.getNombreArchivo())
                .tipoMime(adjunto.getTipoMime())
                .recurso(recurso)
                .build();
    }

    // RN de seguridad, no cubierta por trigger: un cliente solo puede ver sus
    // propias solicitudes. El intento sobre un caso ajeno queda en bitacora.
    private void validarPertenencia(Caso caso, Usuario usuario, String direccionIp,
                                     String entidad, String entidadId, String descripcion) {
        if (caso.getClienteId().equals(usuario.getId())) {
            return;
        }
        bitacoraService.registrarAudita(usuario.getId(), direccionIp, "CASOS", "ACCESO_DENEGADO", entidad, entidadId,
                descripcion, Map.of("casoId", caso.getId()));
        throw new AccesoNoAutorizadoException("No tiene autorización para consultar esta solicitud.");
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

    private CasoResumenDTO aResumen(Caso caso) {
        return CasoResumenDTO.builder()
                .id(caso.getId())
                .numeroBoleta(caso.getNumeroBoleta())
                .tipoSolicitud(caso.getTipoSolicitud().getNombre())
                .asunto(caso.getAsunto())
                .estado(caso.getEstado().getNombre())
                .fechaRegistro(caso.getFechaRegistro())
                .build();
    }
}
