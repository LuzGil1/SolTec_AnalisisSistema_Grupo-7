import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface OrdenServicioDTO {
  id: number;
  numeroOrden: string;
  servicio: string;
  fechaServicio: string;
  descripcion: string | null;
}

@Injectable({ providedIn: 'root' })
export class OrdenServicioService {

  private readonly baseUrl = `${environment.apiUrl}/api/cliente/ordenes-servicio`;

  constructor(private http: HttpClient) {}

  listarMias(): Observable<OrdenServicioDTO[]> {
    return this.http.get<OrdenServicioDTO[]>(this.baseUrl);
  }
}
