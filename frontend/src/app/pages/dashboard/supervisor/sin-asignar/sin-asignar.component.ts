import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import {
  SinAsignarDTO,
  SituacionTecnicoDTO,
  SolicitudSinAsignarDTO,
  SupervisorService,
} from '../../../../core/services/supervisor.service';
import { Vencimiento, describirEspera, describirVencimiento } from '../../../../core/utils/tiempo';
import { DetalleCasoSupervisorComponent } from '../detalle-caso/detalle-caso.component';

interface SituacionTexto {
  texto: string;
  clase: string;
}

// CU Solicitudes sin asignar: consulta. El supervisor NO asigna a mano
// (RN04); la unica medida que puede tomar es sobre los tecnicos (FA02).
@Component({
  selector: 'app-sin-asignar',
  standalone: true,
  imports: [DetalleCasoSupervisorComponent],
  templateUrl: './sin-asignar.component.html',
})
export class SinAsignarComponent implements OnInit {
  private readonly supervisorService = inject(SupervisorService);

  @Output() irA = new EventEmitter<string>();

  datos: SinAsignarDTO | null = null;
  cargando = true;
  error = false;

  casoIdSeleccionado: number | null = null;

  ngOnInit(): void {
    this.supervisorService.listarSinAsignar().subscribe({
      next: (datos) => {
        this.datos = datos;
        this.cargando = false;
      },
      error: () => {
        this.error = true;
        this.cargando = false;
      },
    });
  }

  espera(solicitud: SolicitudSinAsignarDTO): string {
    return describirEspera(solicitud.horasEnCola);
  }

  vencimiento(solicitud: SolicitudSinAsignarDTO): Vencimiento {
    return describirVencimiento(solicitud.fechaLimite);
  }

  porcentajeCarga(tecnico: SituacionTecnicoDTO): number {
    return Math.min(100, Math.round((tecnico.casosAbiertos / tecnico.capacidadMaxima) * 100));
  }

  // Por que este tecnico no esta recibiendo las solicitudes pendientes
  situacion(tecnico: SituacionTecnicoDTO): SituacionTexto {
    if (!tecnico.disponible) {
      return { texto: 'No disponible', clase: 'bg-fuchsia-100 text-fuchsia-700' };
    }
    if (tecnico.casosAbiertos >= tecnico.capacidadMaxima) {
      return { texto: 'Sin cupo', clase: 'bg-amber-100 text-amber-700' };
    }
    const libres = tecnico.capacidadMaxima - tecnico.casosAbiertos;
    return { texto: `${libres} cupo${libres === 1 ? '' : 's'} libre${libres === 1 ? '' : 's'}`, clase: 'bg-emerald-100 text-emerald-700' };
  }

  get tecnicosConCupo(): number {
    return (this.datos?.tecnicos ?? []).filter((t) => t.disponible && t.casosAbiertos < t.capacidadMaxima).length;
  }

  abrirDetalle(casoId: number): void {
    this.casoIdSeleccionado = casoId;
  }

  cerrarDetalle(): void {
    this.casoIdSeleccionado = null;
  }
}
