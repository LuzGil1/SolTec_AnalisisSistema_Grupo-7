import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OpcionFiltroDTO } from './supervisor.service';

export interface MetricaDTO {
  clave: string;
  nombre: string;
  corto: string;
  unidad: string;
  decimales: number;
  aditiva: boolean;
  conteo: boolean;
  ignoraPeriodo: boolean;
  agrupacionesPermitidas: string[];
}

export interface AgrupacionDTO {
  clave: string;
  nombre: string;
  temporal: boolean;
}

export interface TipoGraficaDTO {
  clave: string;
  nombre: string;
  forma: string;
  familia: string;
  requiereTiempo: boolean;
  soloAditiva: boolean;
  requiereSecundaria: boolean;
  explorador: boolean;
}

export interface PeriodoDTO {
  clave: string;
  nombre: string;
}

export interface CatalogoDashboardDTO {
  metricas: MetricaDTO[];
  agrupaciones: AgrupacionDTO[];
  formas: string[];
  tiposGrafica: TipoGraficaDTO[];
  periodos: PeriodoDTO[];
  reglas: string[];
}

export interface OpcionesDashboardDTO {
  tipos: OpcionFiltroDTO[];
  prioridades: OpcionFiltroDTO[];
  situaciones: OpcionFiltroDTO[];
  servicios: OpcionFiltroDTO[];
  especialidades: OpcionFiltroDTO[];
  tecnicos: OpcionFiltroDTO[];
  clientes: OpcionFiltroDTO[];
  supervisores: OpcionFiltroDTO[];
  roles: string[];
  boletaEjemplo: string | null;
}

export interface FiltrosDashboard {
  texto?: string | null;
  desde?: string | null;
  hasta?: string | null;
  modoFecha?: string | null;
  tipo?: string | null;
  prioridad?: string | null;
  situacion?: string | null;
  servicioId?: number | null;
  especialidadId?: number | null;
  tecnicoId?: number | null;
  clienteId?: number | null;
  supervisorId?: number | null;
  rolResponsable?: string | null;
  soloVencidas?: boolean;
  soloDentroSla?: boolean;
  soloAbiertas?: boolean;
  incumplioRespuesta?: boolean;
  conPersonalSenalado?: boolean;
  conTecnicoExcluido?: boolean;
  conAdjuntos?: boolean;
  conOrdenServicio?: boolean;
  duplicadasRelacionadas?: boolean;
}

export interface ConsultaDashboard {
  clave?: string;
  forma: string;
  metrica?: string;
  metricaSecundaria?: string;
  agrupacion?: string;
  agrupacionSecundaria?: string;
  agrupacionTerciaria?: string;
  variante?: string;
  boleta?: string;
}

export interface ResultadoConsulta {
  clave: string | null;
  forma: string;
  titulo: string;
  periodo: string;
  datos: any;
  total: number | null;
  error: string | null;
}

export interface ResultadoLote {
  periodo: string;
  totalSolicitudes: number;
  resultados: ResultadoConsulta[];
}

export interface EventoTrazabilidad {
  fecha: string;
  origen: string;
  usuario: string;
  operacion: string;
  detalle: string;
  situacion: string | null;
}

export interface Trazabilidad {
  numeroBoleta: string;
  asunto: string;
  tipo: string;
  prioridad: string;
  situacion: string;
  cliente: string;
  tecnico: string | null;
  fechaRegistro: string;
  fechaLimiteResolucion: string | null;
  cumplioSla: boolean | null;
  eventos: EventoTrazabilidad[];
  integridad: { integra: boolean; mensaje: string };
}

@Injectable({ providedIn: 'root' })
export class DashboardService {

  private readonly baseUrl = `${environment.apiUrl}/api/dashboard`;

  constructor(private http: HttpClient) {}

  obtenerCatalogo(): Observable<CatalogoDashboardDTO> {
    return this.http.get<CatalogoDashboardDTO>(`${this.baseUrl}/catalogo`);
  }

  obtenerOpciones(servicioId: number | null, especialidadId: number | null): Observable<OpcionesDashboardDTO> {
    let params = new HttpParams();
    if (servicioId !== null) {
      params = params.set('servicioId', servicioId);
    }
    if (especialidadId !== null) {
      params = params.set('especialidadId', especialidadId);
    }
    return this.http.get<OpcionesDashboardDTO>(`${this.baseUrl}/opciones`, { params });
  }

  consultarLote(periodo: string, filtros: FiltrosDashboard, consultas: ConsultaDashboard[]): Observable<ResultadoLote> {
    return this.http.post<ResultadoLote>(`${this.baseUrl}/consulta/lote`, { periodo, filtros, consultas });
  }

  consultar(periodo: string, filtros: FiltrosDashboard, consulta: ConsultaDashboard): Observable<ResultadoConsulta> {
    return this.http.post<ResultadoConsulta>(`${this.baseUrl}/consulta`, { ...consulta, periodo, filtros });
  }

  obtenerTrazabilidad(boleta: string): Observable<Trazabilidad> {
    return this.http.get<Trazabilidad>(`${this.baseUrl}/trazabilidad/${encodeURIComponent(boleta)}`);
  }

  exportarExcel(periodo: string, metrica: string, agrupacion: string, filtros: FiltrosDashboard): Observable<Blob> {
    let params = new HttpParams().set('periodo', periodo).set('metrica', metrica).set('agrupacion', agrupacion);
    const claves = Object.keys(filtros) as (keyof FiltrosDashboard)[];
    for (let i = 0; i < claves.length; i++) {
      const valor = filtros[claves[i]];
      if (valor !== null && valor !== undefined && valor !== '' && valor !== false) {
        params = params.set(`filtros.${claves[i]}`, `${valor}`);
      }
    }
    return this.http.get(`${this.baseUrl}/exportar/excel`, { params, responseType: 'blob' });
  }
}
