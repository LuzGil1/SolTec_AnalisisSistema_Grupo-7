import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SugerenciaResumenDTO, SupervisorService } from '../../../../core/services/supervisor.service';
import { DetalleCasoSupervisorComponent } from '../detalle-caso/detalle-caso.component';

// CU Revision de Sugerencias. Acogida o no, la sugerencia termina en
// CERRADO (RN04); la respuesta es lo unico que el cliente ve.
@Component({
  selector: 'app-sugerencias',
  standalone: true,
  imports: [DatePipe, FormsModule, DetalleCasoSupervisorComponent],
  templateUrl: './sugerencias.component.html',
})
export class SugerenciasComponent implements OnInit {
  private readonly supervisorService = inject(SupervisorService);

  sugerencias: SugerenciaResumenDTO[] = [];
  mostrarRespondidas = false;
  cargando = true;
  error = false;
  mensajeExito = '';

  seleccionada: SugerenciaResumenDTO | null = null;
  respuesta = '';
  errorRespuesta = '';
  guardando = false;

  ngOnInit(): void {
    this.cargarLista(true);
  }

  private cargarLista(esInicial = false): void {
    if (esInicial) {
      this.cargando = true;
    }
    this.supervisorService.listarSugerencias().subscribe({
      next: (sugerencias) => {
        this.sugerencias = sugerencias;
        this.cargando = false;
      },
      error: () => {
        this.error = true;
        this.cargando = false;
      },
    });
  }

  // Ya vienen de la mas reciente a la mas antigua (RN02)
  get visibles(): SugerenciaResumenDTO[] {
    return this.mostrarRespondidas ? this.sugerencias : this.sugerencias.filter((s) => !s.respondida);
  }

  abrir(sugerencia: SugerenciaResumenDTO): void {
    this.seleccionada = sugerencia;
    this.respuesta = '';
    this.errorRespuesta = '';
    this.mensajeExito = '';
  }

  cerrar(): void {
    if (this.guardando) {
      return;
    }
    this.seleccionada = null;
  }

  responder(acogida: boolean): void {
    // RN03 / FA03
    const texto = this.respuesta.trim();
    if (!texto) {
      this.errorRespuesta = 'Escriba la respuesta antes de continuar.';
      return;
    }
    if (!this.seleccionada) {
      return;
    }

    const boleta = this.seleccionada.numeroBoleta;
    this.errorRespuesta = '';
    this.guardando = true;
    this.supervisorService.responderSugerencia(this.seleccionada.id, texto, acogida).subscribe({
      next: () => {
        this.guardando = false;
        this.mensajeExito = `Sugerencia ${boleta} ${acogida ? 'acogida' : 'no acogida'} y cerrada. El cliente ya puede ver la respuesta.`;
        this.cerrar();
        this.cargarLista();
      },
      error: (err) => {
        this.guardando = false;
        this.errorRespuesta = err?.error?.mensaje ?? 'No se pudo registrar la respuesta.';
      },
    });
  }
}
