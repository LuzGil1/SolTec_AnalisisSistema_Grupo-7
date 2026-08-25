package com.example.soltec.service;

import com.example.soltec.dto.AdjuntoDTO;
import com.example.soltec.dto.CasoCreadoDTO;
import com.example.soltec.dto.CasoResumenDTO;
import com.example.soltec.dto.NuevaSolicitudRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface CasoService {

    List<CasoResumenDTO> listarDelClienteActual();

    CasoCreadoDTO registrar(NuevaSolicitudRequest request, String direccionIp);

    AdjuntoDTO adjuntarEvidencia(Integer casoId, MultipartFile archivo) throws IOException;
}
