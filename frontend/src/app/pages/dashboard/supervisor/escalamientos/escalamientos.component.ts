import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import {
  CasoSupervisorDetalleDTO,
  EscalamientoResumenDTO,
  SupervisorService,
} from '../../../../core/services/supervisor.service';
import { Vencimiento, describirVencimiento } from '../../../../core/utils/tiempo';
import { DetalleCasoSupervisorComponent } from '../detalle-caso/detalle-caso.component';

const CONCLUYEN = ['CERRADO', 'IMPROCEDENTE'];

// CU Revision de Escalamientos. Desde ESCALADO el supervisor decide entre
// devolver a la bolsa (FA01), atender el mismo (FA02) o declarar
// improcedente (FA03); si la atiende, continua aqui hasta concluirla.
@Component({
  selector: 'app-escalamientos',
  standalone: true,
  imports: [FormsModule, DetalleCasoSupervisorComponent],
  templateUrl: './escalamientos.component.html',
})
export class EscalamientosComponent implements OnInit {
  private readonly supervisorService = inject(SupervisorService);

  @ViewChild(DetalleCasoSupervisorComponent) private modal?: DetalleCasoSupervisorComponent;

  escalamientos: EscalamientoResumenDTO[] = [];
  cargando = true;
  error = false;
  mensajeExito = '';

  seleccionado: EscalamientoResumenDTO | null = null;
  detalle: CasoSupervisorDetalleDTO | null = null;
  avanceTexto = '';
  errorAvance = '';
  guardando = false;

  ngOnInit(): void {
    this.cargarLista(true);
  }

  private cargarLista(esInicial = false, alTerminar?: () => void): void {
    if (esInicial) {
      this.cargando = true;
    }
    this.supervisorService.listarEscalamientos().subscribe({
      next: (escalamientos) => {
        this.escalamientos = escalamientos;
        this.cargando = false;
        alTerminar?.();
      },
      error: () => {
        this.error = true;
        this.cargando = false;
      },
    });
  }

  vencimiento(escalamiento: EscalamientoResumenDTO): Vencimiento {
    return describirVencimiento(escalamiento.fechaLimite);
  }

  get estaEscalada(): boolean {
    return this.seleccionado?.estadoCodigo === 'ESCALADO';
  }

  abrir(escalamiento: EscalamientoResumenDTO): void {
    this.seleccionado = escalamiento;
    this.detalle = null;
    this.avanceTexto = '';
    this.errorAvance = '';
    this.mensajeExito = '';
  }

  cerrar(): void {
    if (this.guardando) {
      return;
    }
    this.seleccionado = null;
    this.detalle = null;
  }

  devolver(): void {
    this.ejecutar((casoId, texto) => this.supervisorService.devolverEscalamiento(casoId, texto), (resultado) => {
      const boleta = this.seleccionado?.numeroBoleta ?? '';
      this.mensajeExito = resultado?.tecnicoAsignado
        ? `La solicitud ${boleta} volvió a la bolsa y el sistema la asignó a ${resultado.tecnicoAsignado}.`
        : `La solicitud ${boleta} volvió a la bolsa. Se asignará en cuanto un técnico tenga cupo; quien la escaló no la recibirá.`;
      this.cargarLista();
      this.cerrar();
    });
  }

  atender(): void {
    this.ejecutar((casoId, texto) => this.supervisorService.atenderEscalamiento(casoId, texto), () => {
      // Sigue abierta: ahora el supervisor continua la atencion desde aqui
      this.cargarLista(false, () => {
        this.seleccionado = this.escalamientos.find((e) => e.id === this.seleccionado?.id) ?? this.seleccionado;
      });
      this.modal?.recargar();
    });
  }

  trasladar(nuevoEstado: string | null): void {
    this.ejecutar(
      (casoId, texto) => this.supervisorService.registrarAvanceEscalamiento(casoId, texto, nuevoEstado),
      () => {
        this.cargarLista();
        if (nuevoEstado && CONCLUYEN.includes(nuevoEstado)) {
          this.mensajeExito = `La solicitud ${this.seleccionado?.numeroBoleta ?? ''} concluyó.`;
          this.cerrar();
        } else {
          this.modal?.recargar();
        }
      }
    );
  }

  // Las tres decisiones y los traslados exigen avance (RN03 / FA04)
  private ejecutar<T>(peticion: (casoId: number, texto: string) => Observable<T>, alTerminar: (resultado: T) => void): void {
    const texto = this.avanceTexto.trim();
    if (!texto) {
      this.errorAvance = 'Escriba el avance antes de continuar.';
      return;
    }
    if (!this.seleccionado) {
      return;
    }

    this.errorAvance = '';
    this.guardando = true;
    peticion(this.seleccionado.id, texto).subscribe({
      next: (resultado) => {
        this.guardando = false;
        this.avanceTexto = '';
        alTerminar(resultado);
      },
      error: (err) => {
        this.guardando = false;
        this.errorAvance = err?.error?.mensaje ?? 'No se pudo registrar la gestión.';
      },
    });
  }
}
