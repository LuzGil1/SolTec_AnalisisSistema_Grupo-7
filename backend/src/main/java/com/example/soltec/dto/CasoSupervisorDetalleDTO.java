package com.example.soltec.dto;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Detalle completo para el supervisor: a diferencia del cliente, ve el
// tecnico asignado, el tecnico que presto el servicio y todos los avances
// (incluidos los internos, visible_cliente = false).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CasoSupervisorDetalleDTO {

    private Integer id;
    private String numeroBoleta;
    private String tipo;
    private String estado;
    private String prioridad;
    private String asunto;
    private String descripcion;
    private OffsetDateTime fechaRegistro;
    private OffsetDateTime fechaLimiteResolucion;
    private OffsetDateTime fechaAsignacion;
    private OffsetDateTime fechaResolucion;
    private OffsetDateTime fechaCierre;
    private String solucion;
    private String casoRelacionadoBoleta;
    private ClienteContactoDTO cliente;
    private TecnicoAsignadoDTO tecnico;
    private ServicioRecibidoDTO servicioRecibido;
    private String tecnicoServicio;
    private List<AdjuntoDTO> adjuntos;
    private List<AvanceDTO> avances;
    private List<InvolucradoDTO> involucrados;

    // Solo para solicitudes escaladas (RN02 del CU Revision de Escalamientos)
    private String escaladaPor;
    private String motivoEscalamiento;
    private OffsetDateTime fechaEscalamiento;
    private String supervisorResponsable;

    // Traslados que el supervisor puede hacer desde el estado actual. Vacio
    // en la consulta general (/casos/{id}), que es de solo lectura.
    private List<TransicionDTO> transicionesPermitidas;
}
