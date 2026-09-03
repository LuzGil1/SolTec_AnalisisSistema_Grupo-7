import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface TipoSolicitudDTO {
  id: number;
  codigo: string;
  nombre: string;
  requiereServicio: boolean;
  permiteEvidencia: boolean;
}

export interface ParametrosDTO {
  maxMbAdjunto: number;
}

@Injectable({ providedIn: 'root' })
export class CatalogoService {

  private readonly baseUrl = `${environment.apiUrl}/api/catalogos`;

  constructor(private http: HttpClient) {}

  listarTiposSolicitud(): Observable<TipoSolicitudDTO[]> {
    return this.http.get<TipoSolicitudDTO[]>(`${this.baseUrl}/tipos-solicitud`);
  }

  obtenerParametros(): Observable<ParametrosDTO> {
    return this.http.get<ParametrosDTO>(`${this.baseUrl}/parametros`);
  }
}
