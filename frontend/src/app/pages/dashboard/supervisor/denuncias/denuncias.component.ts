import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CasoSupervisorDetalleDTO,
  DenunciaResumenDTO,
  PersonalDTO,
  SupervisorService,
} from '../../../../core/services/supervisor.service';
import { Vencimiento, describirVencimiento } from '../../../../core/utils/tiempo';
import { DetalleCasoSupervisorComponent } from '../detalle-caso/detalle-caso.component';

const CONCLUYEN = ['CERRADO', 'IMPROCEDENTE'];
const MAX_MOTIVO = 200;

// CU Atencion de Denuncias. La denuncia nunca pasa por un tecnico: el
// supervisor la atiende de punta a punta.
@Component({
  selector: 'app-denuncias',
  standalone: true,
  imports: [FormsModule, DetalleCasoSupervisorComponent],
  templateUrl: './denuncias.component.html',
})
export class DenunciasComponent implements OnInit {
  private readonly supervisorService = inject(SupervisorService);

  @ViewChild(DetalleCasoSupervisorComponent) private modal?: DetalleCasoSupervisorComponent;

  readonly maxMotivo = MAX_MOTIVO;

  denuncias: DenunciaResumenDTO[] = [];
  cargando = true;
  error = false;
  mensajeExito = '';

  casoIdSeleccionado: number | null = null;
  detalle: CasoSupervisorDetalleDTO | null = null;
  avanceTexto = '';
  errorAvance = '';
  guardando = false;

  // Agregar involucrado (FA05)
  personal: PersonalDTO[] = [];
  mostrarFormInvolucrado = false;
  usuarioIdInvolucrado: number | null = null;
  motivoInvolucrado = '';
  errorInvolucrado = '';

  ngOnInit(): void {
    this.cargarLista(true);
    this.supervisorService.listarPersonal().subscribe({ next: (p) => (this.personal = p) });
  }

  private cargarLista(esInicial = false): void {
    if (esInicial) {
      this.cargando = true;
    }
    this.supervisorService.listarDenuncias().subscribe({
      next: (denuncias) => {
        this.denuncias = denuncias;
        this.cargando = false;
      },
      error: () => {
        this.error = true;
        this.cargando = false;
      },
    });
  }

  vencimiento(denuncia: DenunciaResumenDTO): Vencimiento {
    return describirVencimiento(denuncia.fechaLimite);
  }

  abrir(casoId: number): void {
    this.casoIdSeleccionado = casoId;
    this.detalle = null;
    this.avanceTexto = '';
    this.errorAvance = '';
    this.mensajeExito = '';
    this.cerrarFormInvolucrado();
  }

  cerrar(): void {
    if (this.guardando) {
      return;
    }
    this.casoIdSeleccionado = null;
    this.detalle = null;
  }

  // Personal que todavia no esta registrado en esta denuncia
  get personalDisponible(): PersonalDTO[] {
    const yaInvolucrados = new Set((this.detalle?.involucrados ?? []).map((i) => i.usuarioId));
    return this.personal.filter((p) => !yaInvolucrados.has(p.id));
  }

  esConclusion(codigo: string): boolean {
    return CONCLUYEN.includes(codigo);
  }

  registrar(nuevoEstado: string | null): void {
    // RN05 / FA06
    const texto = this.avanceTexto.trim();
    if (!texto) {
      this.errorAvance = 'Escriba el avance antes de continuar.';
      return;
    }
    if (this.casoIdSeleccionado === null) {
      return;
    }

    const casoId = this.casoIdSeleccionado;
    const boleta = this.detalle?.numeroBoleta ?? '';
    this.errorAvance = '';
    this.guardando = true;

    this.supervisorService.registrarAvanceDenuncia(casoId, texto, nuevoEstado).subscribe({
      next: () => {
        this.guardando = false;
        this.avanceTexto = '';
        this.cargarLista();
        if (nuevoEstado && this.esConclusion(nuevoEstado)) {
          this.mensajeExito = `La denuncia ${boleta} concluyó.`;
          this.cerrar();
        } else {
          this.modal?.recargar();
        }
      },
      error: (err) => {
        this.guardando = false;
        this.errorAvance = err?.error?.mensaje ?? 'No se pudo registrar el avance.';
      },
    });
  }

  abrirFormInvolucrado(): void {
    this.mostrarFormInvolucrado = true;
    this.usuarioIdInvolucrado = null;
    this.motivoInvolucrado = '';
    this.errorInvolucrado = '';
  }

  cerrarFormInvolucrado(): void {
    this.mostrarFormInvolucrado = false;
    this.errorInvolucrado = '';
  }

  agregarInvolucrado(): void {
    const motivo = this.motivoInvolucrado.trim();
    if (this.usuarioIdInvolucrado === null) {
      this.errorInvolucrado = 'Seleccione al personal involucrado.';
      return;
    }
    if (!motivo) {
      this.errorInvolucrado = 'Indique el motivo por el cual se registra como involucrado.';
      return;
    }
    if (this.casoIdSeleccionado === null) {
      return;
    }

    this.guardando = true;
    this.supervisorService.agregarInvolucrado(this.casoIdSeleccionado, this.usuarioIdInvolucrado, motivo).subscribe({
      next: () => {
        this.guardando = false;
        this.cerrarFormInvolucrado();
        this.modal?.recargar();
        this.cargarLista();
      },
      error: (err) => {
        this.guardando = false;
        this.errorInvolucrado = err?.error?.mensaje ?? 'No se pudo registrar al involucrado.';
      },
    });
  }
}
