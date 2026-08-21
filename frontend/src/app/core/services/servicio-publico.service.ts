import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ServicioDTO {
  id: number;
  nombre: string;
  descripcion: string;
}

@Injectable({ providedIn: 'root' })
export class ServicioPublicoService {

  private readonly baseUrl = `${environment.apiUrl}/api/publico`;

  constructor(private http: HttpClient) {}

  listarServicios(): Observable<ServicioDTO[]> {
    return this.http.get<ServicioDTO[]>(`${this.baseUrl}/servicios`);
  }
}
