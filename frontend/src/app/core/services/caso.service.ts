import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CasoResumenDTO {
  id: number;
  numeroBoleta: string;
  tipoSolicitud: string;
  asunto: string;
  estado: string;
  fechaRegistro: string;
}

export interface NuevaSolicitudRequest {
  tipoSolicitudId: number;
  ordenServicioId: number | null;
  casoRelacionadoId: number | null;
  asunto: string;
  descripcion: string;
}

export interface CasoCreadoDTO {
  id: number;
  numeroBoleta: string;
  estado: string;
  fechaLimiteResolucion: string | null;
}

export interface AdjuntoDTO {
  id: number;
  nombreArchivo: string;
  tipoMime: string;
  tamanoBytes: number;
}

export interface ServicioRecibidoDTO {
  numeroOrden: string;
  servicio: string;
  fechaServicio: string;
}

export interface CasoRelacionadoDTO {
  id: number;
  numeroBoleta: string;
  asunto: string;
}

export interface CasoDetalleDTO {
  numeroBoleta: string;
  tipo: string;
  estado: string;
  fechaRegistro: string;
  asunto: string;
  descripcion: string;
  servicioRecibido: ServicioRecibidoDTO | null;
  casoRelacionado: CasoRelacionadoDTO | null;
  fechaLimiteResolucion: string | null;
  adjuntos: AdjuntoDTO[];
}

@Injectable({ providedIn: 'root' })
export class CasoService {

  private readonly baseUrl = `${environment.apiUrl}/api/cliente/casos`;

  constructor(private http: HttpClient) {}

  listarMisCasos(): Observable<CasoResumenDTO[]> {
    return this.http.get<CasoResumenDTO[]>(this.baseUrl);
  }

  obtenerDetalle(casoId: number): Observable<CasoDetalleDTO> {
    return this.http.get<CasoDetalleDTO>(`${this.baseUrl}/${casoId}`);
  }

  registrar(datos: NuevaSolicitudRequest): Observable<CasoCreadoDTO> {
    return this.http.post<CasoCreadoDTO>(this.baseUrl, datos);
  }

  adjuntarEvidencia(casoId: number, archivo: File): Observable<unknown> {
    const formData = new FormData();
    formData.append('archivo', archivo);
    return this.http.post(`${this.baseUrl}/${casoId}/adjuntos`, formData);
  }

  descargarAdjunto(casoId: number, adjuntoId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${casoId}/adjuntos/${adjuntoId}`, { responseType: 'blob' });
  }
}
