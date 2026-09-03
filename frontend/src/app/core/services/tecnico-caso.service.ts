import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdjuntoDTO, ServicioRecibidoDTO } from './caso.service';

export interface CasoTecnicoResumenDTO {
  id: number;
  numeroBoleta: string;
  tipo: string;
  asunto: string;
  prioridad: string;
  estado: string;
  cliente: string;
  fechaRegistro: string;
  fechaLimiteResolucion: string | null;
}

export interface CargaTecnicoDTO {
  casosAbiertos: number;
  capacidadMaxima: number;
  disponible: boolean;
}

export interface ClienteContactoDTO {
  nombre: string;
  correo: string;
  telefono: string | null;
  direccion: string | null;
}

export interface AvanceDTO {
  fecha: string;
  autor: string;
  comentario: string;
  estadoAnterior: string | null;
  estadoNuevo: string | null;
}

export interface TransicionDTO {
  codigo: string;
  nombre: string;
}

export interface CasoTecnicoDetalleDTO {
  numeroBoleta: string;
  tipo: string;
  estado: string;
  prioridad: string;
  asunto: string;
  descripcion: string;
  fechaRegistro: string;
  fechaLimiteResolucion: string | null;
  cliente: ClienteContactoDTO;
  servicioRecibido: ServicioRecibidoDTO | null;
  adjuntos: AdjuntoDTO[];
  avances: AvanceDTO[];
  transicionesPermitidas: TransicionDTO[];
}

export interface RegistrarAvanceRequest {
  comentario: string;
  nuevoEstado: string | null;
}

@Injectable({ providedIn: 'root' })
export class TecnicoCasoService {

  private readonly baseUrl = `${environment.apiUrl}/api/tecnico`;

  constructor(private http: HttpClient) {}

  listarMisCasos(): Observable<CasoTecnicoResumenDTO[]> {
    return this.http.get<CasoTecnicoResumenDTO[]>(`${this.baseUrl}/casos`);
  }

  obtenerCarga(): Observable<CargaTecnicoDTO> {
    return this.http.get<CargaTecnicoDTO>(`${this.baseUrl}/carga`);
  }

  obtenerDetalle(casoId: number): Observable<CasoTecnicoDetalleDTO> {
    return this.http.get<CasoTecnicoDetalleDTO>(`${this.baseUrl}/casos/${casoId}`);
  }

  descargarAdjunto(casoId: number, adjuntoId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/casos/${casoId}/adjuntos/${adjuntoId}`, { responseType: 'blob' });
  }

  registrarAvance(casoId: number, datos: RegistrarAvanceRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/casos/${casoId}/avance`, datos);
  }
}
