package com.example.soltec.service;

import com.example.soltec.dto.AgregarInvolucradoRequest;
import com.example.soltec.dto.CasoSupervisorDetalleDTO;
import com.example.soltec.dto.DenunciaResumenDTO;
import com.example.soltec.dto.EscalamientoResumenDTO;
import com.example.soltec.dto.PersonalDTO;
import com.example.soltec.dto.RegistrarAvanceRequest;
import com.example.soltec.dto.ResponderSugerenciaRequest;
import com.example.soltec.dto.ResultadoDevolucionDTO;
import com.example.soltec.dto.SugerenciaResumenDTO;
import java.util.List;

// Segunda etapa del modulo del Supervisor: atencion de denuncias, revision
// de escalamientos y revision de sugerencias.
public interface SupervisorAtencionService {

    List<DenunciaResumenDTO> listarDenuncias();

    CasoSupervisorDetalleDTO obtenerDenuncia(Integer casoId, String direccionIp);

    void registrarAvanceDenuncia(Integer casoId, RegistrarAvanceRequest request, String direccionIp);

    void agregarInvolucrado(Integer casoId, AgregarInvolucradoRequest request, String direccionIp);

    List<PersonalDTO> listarPersonal();

    List<EscalamientoResumenDTO> listarEscalamientos();

    CasoSupervisorDetalleDTO obtenerEscalamiento(Integer casoId, String direccionIp);

    ResultadoDevolucionDTO devolverEscalamiento(Integer casoId, String comentario, String direccionIp);

    void atenderEscalamiento(Integer casoId, String comentario, String direccionIp);

    void registrarAvanceEscalamiento(Integer casoId, RegistrarAvanceRequest request, String direccionIp);

    List<SugerenciaResumenDTO> listarSugerencias();

    void responderSugerencia(Integer casoId, ResponderSugerenciaRequest request, String direccionIp);
}
