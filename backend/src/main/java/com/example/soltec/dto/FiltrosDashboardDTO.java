package com.example.soltec.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

// Todos los filtros son opcionales y se combinan entre si.
@Getter
@Setter
@NoArgsConstructor
public class FiltrosDashboardDTO {

    private String texto;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate desde;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hasta;
    private String modoFecha;
    private String tipo;
    private String prioridad;
    private String situacion;
    private Integer servicioId;
    private Integer especialidadId;
    private Integer tecnicoId;
    private Integer clienteId;
    private Integer supervisorId;
    private String rolResponsable;

    private Boolean soloVencidas;
    private Boolean soloDentroSla;
    private Boolean soloAbiertas;
    private Boolean incumplioRespuesta;
    private Boolean conPersonalSenalado;
    private Boolean conTecnicoExcluido;
    private Boolean conAdjuntos;
    private Boolean conOrdenServicio;
    private Boolean duplicadasRelacionadas;
}
