import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ResultadoCambioTecnicoDTO, SituacionTecnicoDTO, SupervisorService } from '../../../../core/services/supervisor.service';

type Accion = 'noDisponible' | 'disponible' | 'capacidad';

const MENSAJE_CAPACIDAD_INVALIDA = 'La capacidad debe ser un número entero mayor a cero.';
const MAX_MOTIVO = 200;

// CU Administracion de Tecnicos: solo disponibilidad y capacidad (RN05).
@Component({
  selector: 'app-tecnicos',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './tecnicos.component.html',
})
export class TecnicosComponent implements OnInit {
  private readonly supervisorService = inject(SupervisorService);

  readonly maxMotivo = MAX_MOTIVO;

  tecnicos: SituacionTecnicoDTO[] = [];
  cargando = true;
  error = false;
  mensajeExito = '';

  // Modal abierto
  accion: Accion | null = null;
  seleccionado: SituacionTecnicoDTO | null = null;
  motivo = '';
  capacidad: number | null = null;
  errorModal = '';
  guardando = false;

  ngOnInit(): void {
    this.cargar(true);
  }

  private cargar(esInicial = false): void {
    if (esInicial) {
      this.cargando = true;
    }
    this.supervisorService.listarTecnicos().subscribe({
      next: (tecnicos) => {
        this.tecnicos = tecnicos;
        this.cargando = false;
      },
      error: () => {
        this.error = true;
        this.cargando = false;
      },
    });
  }

  porcentajeCarga(tecnico: SituacionTecnicoDTO): number {
    return Math.min(100, Math.round((tecnico.casosAbiertos / tecnico.capacidadMaxima) * 100));
  }

  abrir(accion: Accion, tecnico: SituacionTecnicoDTO): void {
    this.accion = accion;
    this.seleccionado = tecnico;
    this.motivo = '';
    this.capacidad = accion === 'capacidad' ? tecnico.capacidadMaxima : null;
    this.errorModal = '';
    this.mensajeExito = '';
  }

  cerrarModal(): void {
    if (this.guardando) {
      return;
    }
    this.accion = null;
    this.seleccionado = null;
  }

  confirmarDisponibilidad(): void {
    if (!this.seleccionado || !this.accion) {
      return;
    }
    const disponible = this.accion === 'disponible';
    const motivo = this.motivo.trim();

    // RN02: sin motivo no se permite continuar
    if (!disponible && !motivo) {
      this.errorModal = 'Indique el motivo por el cual el técnico no estará disponible.';
      return;
    }

    this.ejecutar(this.supervisorService.cambiarDisponibilidad(this.seleccionado.id, disponible, disponible ? null : motivo));
  }

  confirmarCapacidad(): void {
    if (!this.seleccionado) {
      return;
    }
    // FA04: entero mayor a cero; el backend valida lo mismo
    const valor = this.capacidad;
    if (valor === null || !Number.isInteger(valor) || valor < 1) {
      this.errorModal = MENSAJE_CAPACIDAD_INVALIDA;
      return;
    }
    this.ejecutar(this.supervisorService.cambiarCapacidad(this.seleccionado.id, valor));
  }

  get reduceCapacidad(): boolean {
    return !!this.seleccionado && this.capacidad !== null && this.capacidad < this.seleccionado.casosAbiertos;
  }

  private ejecutar(peticion: ReturnType<SupervisorService['cambiarCapacidad']>): void {
    const accion = this.accion;
    this.errorModal = '';
    this.guardando = true;

    peticion.subscribe({
      next: (resultado) => {
        this.guardando = false;
        this.mensajeExito = this.describirResultado(accion, resultado);
        this.accion = null;
        this.seleccionado = null;
        this.cargar();
      },
      error: (err) => {
        this.guardando = false;
        this.errorModal = err?.error?.mensaje ?? 'No se pudo aplicar el cambio.';
      },
    });
  }

  private describirResultado(accion: Accion | null, r: ResultadoCambioTecnicoDTO): string {
    const nombre = r.tecnico.nombre;
    const casos = (n: number) => `${n} caso${n === 1 ? '' : 's'}`;

    if (accion === 'noDisponible') {
      return r.casosLiberados > 0
        ? `${nombre} quedó no disponible. ${casos(r.casosLiberados)} regresaron a la bolsa para redistribuirse.`
        : `${nombre} quedó no disponible. No tenía casos pendientes que devolver a la bolsa.`;
    }
    if (accion === 'disponible') {
      return r.casosAsignados > 0
        ? `${nombre} está disponible de nuevo y recibió ${casos(r.casosAsignados)} de la bolsa.`
        : `${nombre} está disponible de nuevo.`;
    }
    return r.casosAsignados > 0
      ? `Capacidad de ${nombre} actualizada a ${r.tecnico.capacidadMaxima}. Se asignaron ${casos(r.casosAsignados)} de la bolsa.`
      : `Capacidad de ${nombre} actualizada a ${r.tecnico.capacidadMaxima}.`;
  }
}
