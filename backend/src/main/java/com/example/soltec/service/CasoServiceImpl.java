package com.example.soltec.service;

import com.example.soltec.config.UsuarioActualProvider;
import com.example.soltec.dto.AdjuntoDTO;
import com.example.soltec.dto.CasoCreadoDTO;
import com.example.soltec.dto.CasoResumenDTO;
import com.example.soltec.dto.NuevaSolicitudRequest;
import com.example.soltec.entity.Adjunto;
import com.example.soltec.entity.Caso;
import com.example.soltec.entity.OrdenServicio;
import com.example.soltec.entity.Parametro;
import com.example.soltec.entity.TipoSolicitud;
import com.example.soltec.entity.Usuario;
import com.example.soltec.exception.SolicitudInvalidaException;
import com.example.soltec.repository.AdjuntoRepository;
import com.example.soltec.repository.CasoRepository;
import com.example.soltec.repository.OrdenServicioRepository;
import com.example.soltec.repository.ParametroRepository;
import com.example.soltec.repository.TipoSolicitudRepository;
import com.example.soltec.storage.AlmacenamientoService;
import com.example.soltec.storage.ArchivoGuardado;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CasoServiceImpl implements CasoService {

    private static final String CODIGO_DENUNCIA = "DENUNCIA";
    private static final String CLAVE_MAX_MB_ADJUNTO = "MAX_MB_ADJUNTO";
    private static final String MENSAJE_ARCHIVO_INVALIDO =
            "El archivo no pudo adjuntarse. Verifique que el formato sea válido y que no exceda el tamaño máximo permitido.";

    private final CasoRepository casoRepository;
    private final TipoSolicitudRepository tipoSolicitudRepository;
    private final OrdenServicioRepository ordenServicioRepository;
    private final AdjuntoRepository adjuntoRepository;
    private final ParametroRepository parametroRepository;
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

        // RN08: el caso relacionado debe existir y ser del mismo cliente.
        // El trigger solo exige que no venga vacio; la pertenencia se valida aqui.
        if (request.getCasoRelacionadoId() != null) {
            casoRepository.findByIdAndClienteId(request.getCasoRelacionadoId(), clienteId)
                    .orElseThrow(() -> new SolicitudInvalidaException(
                            "El caso relacionado no existe o no pertenece a este cliente"));
        }

        Caso caso = Caso.builder()
                .clienteId(clienteId)
                .tipoSolicitud(tipo)
                .casoRelacionadoId(request.getCasoRelacionadoId())
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
                .tipoMime(archivo.getContentType())
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
        if (archivo == null || archivo.isEmpty()) {
            throw new SolicitudInvalidaException("Debe seleccionar un archivo");
        }

        String tipoMime = archivo.getContentType();
        boolean formatoValido = tipoMime != null && (tipoMime.startsWith("image/")
                || tipoMime.startsWith("audio/")
                || tipoMime.startsWith("video/")
                || tipoMime.equals("application/pdf"));
        if (!formatoValido) {
            throw new SolicitudInvalidaException(MENSAJE_ARCHIVO_INVALIDO);
        }

        BigDecimal maxMb = parametroRepository.findByClave(CLAVE_MAX_MB_ADJUNTO)
                .map(Parametro::getValor)
                .orElse(BigDecimal.TEN);
        long maxBytes = maxMb.multiply(BigDecimal.valueOf(1024L * 1024L)).longValue();
        if (archivo.getSize() > maxBytes) {
            throw new SolicitudInvalidaException(MENSAJE_ARCHIVO_INVALIDO);
        }
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
