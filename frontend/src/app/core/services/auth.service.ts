import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface LoginRequest {
  correo: string;
  contrasena: string;
}

export interface LoginResponse {
  token: string;
  rol: string;
  nombre: string;
}

export interface RegistroRequest {
  nombres: string;
  apellidos: string;
  correo: string;
  contrasena: string;
  telefono: string;
  nit: string;
  direccion: string;
}

export interface RegistroResponse {
  id: number;
  nombres: string;
  apellidos: string;
  correo: string;
  rol: string;
}

const TOKEN_KEY = 'soltec_token';
const ROL_KEY = 'soltec_rol';
const NOMBRE_KEY = 'soltec_nombre';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly baseUrl = `${environment.apiUrl}/api/auth`;

  constructor(private http: HttpClient) {}

  login(datos: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, datos).pipe(
      tap((respuesta) => {
        localStorage.setItem(TOKEN_KEY, respuesta.token);
        localStorage.setItem(ROL_KEY, respuesta.rol);
        localStorage.setItem(NOMBRE_KEY, respuesta.nombre);
      })
    );
  }

  registro(datos: RegistroRequest): Observable<RegistroResponse> {
    return this.http.post<RegistroResponse>(`${this.baseUrl}/registro`, datos);
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROL_KEY);
    localStorage.removeItem(NOMBRE_KEY);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getRol(): string | null {
    return localStorage.getItem(ROL_KEY);
  }

  getNombre(): string | null {
    return localStorage.getItem(NOMBRE_KEY);
  }

  estaAutenticado(): boolean {
    return !!this.getToken();
  }

  // A donde redirigir despues del login, segun el rol del token
  rutaSegunRol(rol: string): string {
    const rutas: Record<string, string> = {
      CLIENTE: '/cliente',
      TECNICO: '/tecnico',
      SUPERVISOR: '/supervisor',
      ADMIN: '/admin',
      AUDITOR: '/auditor',
    };
    return rutas[rol] ?? '/login';
  }
}
