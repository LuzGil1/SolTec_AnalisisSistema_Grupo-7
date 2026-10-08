import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdjuntoDTO, ServicioRecibidoDTO } from './caso.service';
import { AvanceDTO, ClienteContactoDTO, TransicionDTO } from './tecnico-caso.service';

export interface ResumenSupervisorDTO {
  denunciasPendientes: number;
  escalamientosPendientes: number;
  solicitudesSinAsignar: number;
}

export interface OpcionFiltroDTO {
  codigo: string;
  nombre: string;
}

export interface FiltrosSupervisorDTO {
  tipos: OpcionFiltroDTO[];
  estados: OpcionFiltroDTO[];
}

export interface FiltrosCasos {
  tipo?: string | null;
  estado?: string | null;
  tecnicoId?: number | null;
  fechaDesde?: string | null;
  fechaHasta?: string | null;
  boleta?: string | null;
}

export interface CasoGeneralDTO {
  id: number;
  numeroBoleta: string;
  tipoCodigo: string;
  tipo: string;
  asunto: string;
  cliente: string;
  tecnicoId: number | null;
  tecnico: string | null;
  estadoCodigo: string;
  estado: string;
  esFinal: boolean;
  prioridad: string;
  fechaRegistro: string;
  fechaLimite: string | null;
}

export interface TecnicoAsignadoDTO {
  id: number;
  nombre: string;
  correo: string;
  telefono: string | null;
}

export interface CasoSupervisorDetalleDTO {
  id: number;
  numeroBoleta: string;
  tipo: string;
  estado: string;
  prioridad: string;
  asunto: string;
  descripcion: string;
  fechaRegistro: string;
  fechaLimiteResolucion: string | null;
  fechaAsignacion: string | null;
  fechaResolucion: string | null;
  fechaCierre: string | null;
  solucion: string | null;
  casoRelacionadoBoleta: string | null;
  cliente: ClienteContactoDTO;
  tecnico: TecnicoAsignadoDTO | null;
  servicioRecibido: ServicioRecibidoDTO | null;
  tecnicoServicio: string | null;
  adjuntos: AdjuntoDTO[];
  avances: AvanceDTO[];
  involucrados: InvolucradoDTO[];
  escaladaPor: string | null;
  motivoEscalamiento: string | null;
  fechaEscalamiento: string | null;
  supervisorResponsable: string | null;
  transicionesPermitidas: TransicionDTO[];
}

export interface InvolucradoDTO {
  usuarioId: number;
  nombre: string;
  rol: string;
  motivo: string | null;
}

export interface PersonalDTO {
  id: number;
  nombre: string;
  rol: string;
}

export interface DenunciaResumenDTO {
  id: number;
  numeroBoleta: string;
  asunto: string;
  cliente: string;
  involucrados: string;
  estadoCodigo: string;
  estado: string;
  fechaRegistro: string;
  fechaLimite: string | null;
}

export interface EscalamientoResumenDTO {
  id: number;
  numeroBoleta: string;
  tipo: string;
  asunto: string;
  cliente: string;
  escaladaPor: string | null;
  motivoEscalamiento: string | null;
  estadoCodigo: string;
  estado: string;
  enAtencionSupervisor: boolean;
  supervisorResponsable: string | null;
  fechaLimite: string | null;
}

export interface SugerenciaResumenDTO {
  id: number;
  numeroBoleta: string;
  asunto: string;
  cliente: string;
  estadoCodigo: string;
  estado: string;
  respondida: boolean;
  fechaRegistro: string;
}

export interface ResultadoDevolucionDTO {
  tecnicoAsignado: string | null;
}

// De donde carga el detalle la ventana emergente: la consulta general es
// de solo lectura; denuncias y escalamientos traen ademas los traslados
// permitidos desde el estado actual.
export type FuenteDetalle = 'general' | 'denuncia' | 'escalamiento';

export interface SolicitudSinAsignarDTO {
  id: number;
  numeroBoleta: string;
  tipo: string;
  asunto: string;
  cliente: string;
  prioridad: string;
  fechaRegistro: string;
  horasEnCola: number;
  fechaLimite: string | null;
}

export interface SituacionTecnicoDTO {
  id: number;
  nombre: string;
  correo: string;
  especialidades: string;
  casosAbiertos: number;
  capacidadMaxima: number;
  disponible: boolean;
  motivoNoDisponible: string | null;
}

export interface SinAsignarDTO {
  horasUmbral: number | null;
  solicitudes: SolicitudSinAsignarDTO[];
  tecnicos: SituacionTecnicoDTO[];
}

export interface ResultadoCambioTecnicoDTO {
  tecnico: SituacionTecnicoDTO;
  casosLiberados: number;
  casosAsignados: number;
}

@Injectable({ providedIn: 'root' })
export class SupervisorService {

  private readonly baseUrl = `${environment.apiUrl}/api/supervisor`;

  constructor(private http: HttpClient) {}

  obtenerResumen(): Observable<ResumenSupervisorDTO> {
    return this.http.get<ResumenSupervisorDTO>(`${this.baseUrl}/resumen`);
  }

  obtenerFiltros(): Observable<FiltrosSupervisorDTO> {
    return this.http.get<FiltrosSupervisorDTO>(`${this.baseUrl}/filtros`);
  }

  listarCasos(filtros: FiltrosCasos): Observable<CasoGeneralDTO[]> {
    let params = new HttpParams();
    Object.entries(filtros).forEach(([clave, valor]) => {
      if (valor !== null && valor !== undefined && `${valor}`.trim() !== '') {
        params = params.set(clave, `${valor}`.trim());
      }
    });
    return this.http.get<CasoGeneralDTO[]>(`${this.baseUrl}/casos`, { params });
  }

  descargarAdjunto(casoId: number, adjuntoId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/casos/${casoId}/adjuntos/${adjuntoId}`, { responseType: 'blob' });
  }

  listarSinAsignar(): Observable<SinAsignarDTO> {
    return this.http.get<SinAsignarDTO>(`${this.baseUrl}/sin-asignar`);
  }

  listarTecnicos(): Observable<SituacionTecnicoDTO[]> {
    return this.http.get<SituacionTecnicoDTO[]>(`${this.baseUrl}/tecnicos`);
  }

  cambiarDisponibilidad(tecnicoId: number, disponible: boolean, motivo: string | null): Observable<ResultadoCambioTecnicoDTO> {
    return this.http.put<ResultadoCambioTecnicoDTO>(`${this.baseUrl}/tecnicos/${tecnicoId}/disponibilidad`, { disponible, motivo });
  }

  cambiarCapacidad(tecnicoId: number, capacidadMaxima: number): Observable<ResultadoCambioTecnicoDTO> {
    return this.http.put<ResultadoCambioTecnicoDTO>(`${this.baseUrl}/tecnicos/${tecnicoId}/capacidad`, { capacidadMaxima });
  }

  obtenerDetalleDesde(fuente: FuenteDetalle, casoId: number): Observable<CasoSupervisorDetalleDTO> {
    const ruta = { general: 'casos', denuncia: 'denuncias', escalamiento: 'escalamientos' }[fuente];
    return this.http.get<CasoSupervisorDetalleDTO>(`${this.baseUrl}/${ruta}/${casoId}`);
  }

  // ----- Denuncias -----

  listarDenuncias(): Observable<DenunciaResumenDTO[]> {
    return this.http.get<DenunciaResumenDTO[]>(`${this.baseUrl}/denuncias`);
  }

  registrarAvanceDenuncia(casoId: number, comentario: string, nuevoEstado: string | null): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/denuncias/${casoId}/avance`, { comentario, nuevoEstado });
  }

  agregarInvolucrado(casoId: number, usuarioId: number, motivo: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/denuncias/${casoId}/involucrados`, { usuarioId, motivo });
  }

  listarPersonal(): Observable<PersonalDTO[]> {
    return this.http.get<PersonalDTO[]>(`${this.baseUrl}/personal`);
  }

  // ----- Escalamientos -----

  listarEscalamientos(): Observable<EscalamientoResumenDTO[]> {
    return this.http.get<EscalamientoResumenDTO[]>(`${this.baseUrl}/escalamientos`);
  }

  devolverEscalamiento(casoId: number, comentario: string): Observable<ResultadoDevolucionDTO> {
    return this.http.post<ResultadoDevolucionDTO>(`${this.baseUrl}/escalamientos/${casoId}/devolver`, { comentario });
  }

  atenderEscalamiento(casoId: number, comentario: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/escalamientos/${casoId}/atender`, { comentario });
  }

  registrarAvanceEscalamiento(casoId: number, comentario: string, nuevoEstado: string | null): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/escalamientos/${casoId}/avance`, { comentario, nuevoEstado });
  }

  // ----- Sugerencias -----

  listarSugerencias(): Observable<SugerenciaResumenDTO[]> {
    return this.http.get<SugerenciaResumenDTO[]>(`${this.baseUrl}/sugerencias`);
  }

  responderSugerencia(casoId: number, respuesta: string, acogida: boolean): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/sugerencias/${casoId}/responder`, { respuesta, acogida });
  }
}
