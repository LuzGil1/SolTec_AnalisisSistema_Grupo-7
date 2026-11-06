import { Component, Input, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import { DashboardService, Trazabilidad } from '../../../../core/services/dashboard.service';
import { CONSTRUCTORES } from '../dashboard-graficas/opciones-graficas';
import { echarts } from '../dashboard-graficas/tema-echarts';

// Historia de una solicitud. Se usa como opcion del menu y como grafica
// dentro del dashboard.
@Component({
  selector: 'app-trazabilidad',
  standalone: true,
  imports: [FormsModule, DatePipe, NgxEchartsDirective],
  providers: [provideEchartsCore({ echarts })],
  templateUrl: './trazabilidad.component.html',
  styleUrl: './trazabilidad.component.scss',
})
export class TrazabilidadComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);

  // true cuando ocupa la pantalla completa: muestra su propio encabezado
  @Input() pantalla = false;

  readonly opcionesInicio = { renderer: 'svg' as const };
  readonly sinOpciones = {};

  boleta = '';
  traza: Trazabilidad | null = null;
  error = '';
  private instancia: any = null;

  ngOnInit(): void {
    this.dashboardService.obtenerOpciones(null, null).subscribe({
      next: (opciones) => {
        if (opciones.boletaEjemplo) {
          this.boleta = opciones.boletaEjemplo;
          this.buscar();
        }
      },
    });
  }

  alIniciar(instancia: any): void {
    this.instancia = instancia;
    this.dibujar();
  }

  buscar(): void {
    const boleta = this.boleta.trim();
    if (boleta === '') {
      return;
    }
    this.dashboardService.obtenerTrazabilidad(boleta).subscribe({
      next: (traza) => {
        this.traza = traza;
        this.error = '';
        this.dibujar();
      },
      error: (err) => {
        this.traza = null;
        this.error = err?.error?.mensaje ?? 'No se pudo consultar la solicitud.';
      },
    });
  }

  private dibujar(): void {
    if (!this.instancia || !this.traza) {
      return;
    }
    try {
      this.instancia.setOption(CONSTRUCTORES['linea_tiempo'](this.traza, { unidad: '', nombre: '' }), true);
    } catch (e) {
      console.error('No se pudo dibujar la línea de tiempo:', e);
    }
  }
}
