import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { CatalogoService, TipoSolicitudDTO } from '../../../core/services/catalogo.service';
import { OrdenServicioService, OrdenServicioDTO } from '../../../core/services/orden-servicio.service';
import { CasoService, CasoCreadoDTO, CasoResumenDTO, NuevaSolicitudRequest } from '../../../core/services/caso.service';

@Component({
  selector: 'app-nueva-solicitud',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './nueva-solicitud.component.html',
  styleUrl: './nueva-solicitud.component.scss',
})
export class NuevaSolicitudComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly catalogoService = inject(CatalogoService);
  private readonly ordenServicioService = inject(OrdenServicioService);
  private readonly casoService = inject(CasoService);

  @Output() irAMisSolicitudes = new EventEmitter<void>();
  @Output() cancelado = new EventEmitter<void>();

  tipos: TipoSolicitudDTO[] = [];
  ordenes: OrdenServicioDTO[] = [];
  casosPrevios: CasoResumenDTO[] = [];
  tipoSeleccionado: TipoSolicitudDTO | null = null;

  cargandoCatalogos = true;
  errorCatalogos = false;
  enviando = false;
  errorMensaje = '';
  archivoSeleccionado: File | null = null;
  archivoErrorMensaje = '';
  boletaConfirmada: CasoCreadoDTO | null = null;

  form = this.fb.group({
    tipoSolicitudId: this.fb.control<number | null>(null, Validators.required),
    ordenServicioId: this.fb.control<number | null>(null),
    casoRelacionadoId: this.fb.control<number | null>(null),
    asunto: this.fb.control('', [Validators.required, Validators.maxLength(150)]),
    descripcion: this.fb.control('', Validators.required),
  });

  ngOnInit(): void {
    forkJoin({
      tipos: this.catalogoService.listarTiposSolicitud(),
      ordenes: this.ordenServicioService.listarMias(),
      casos: this.casoService.listarMisCasos(),
    }).subscribe({
      next: ({ tipos, ordenes, casos }) => {
        this.tipos = tipos;
        this.ordenes = ordenes;
        this.casosPrevios = casos;
        this.cargandoCatalogos = false;
      },
      error: () => {
        this.errorCatalogos = true;
        this.cargandoCatalogos = false;
      },
    });
  }

  get asunto() {
    return this.form.controls.asunto;
  }

  get descripcion() {
    return this.form.controls.descripcion;
  }

  get ordenServicioId() {
    return this.form.controls.ordenServicioId;
  }

  get casoRelacionadoId() {
    return this.form.controls.casoRelacionadoId;
  }

  etiquetaOrden(orden: OrdenServicioDTO): string {
    const [anio, mes, dia] = orden.fechaServicio.split('-');
    return `${orden.numeroOrden} - ${orden.servicio} - ${dia}/${mes}/${anio}`;
  }

  onTipoChange(): void {
    const id = this.form.controls.tipoSolicitudId.value;
    this.tipoSeleccionado = this.tipos.find((t) => t.id === id) ?? null;

    this.ordenServicioId.setValue(null);
    this.casoRelacionadoId.setValue(null);
    this.archivoSeleccionado = null;
    this.archivoErrorMensaje = '';

    this.ordenServicioId.clearValidators();
    this.casoRelacionadoId.clearValidators();

    if (this.tipoSeleccionado?.codigo === 'DENUNCIA') {
      this.ordenServicioId.setValidators([Validators.required]);
    }
    if (this.tipoSeleccionado?.requiereCasoPrevio) {
      this.casoRelacionadoId.setValidators([Validators.required]);
    }

    this.ordenServicioId.updateValueAndValidity();
    this.casoRelacionadoId.updateValueAndValidity();
  }

  onArchivoSeleccionado(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    this.archivoSeleccionado = input.files?.[0] ?? null;
    this.archivoErrorMensaje = '';
  }

  enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.enviando = true;
    this.errorMensaje = '';

    const valores = this.form.getRawValue();
    const payload: NuevaSolicitudRequest = {
      tipoSolicitudId: valores.tipoSolicitudId!,
      ordenServicioId: valores.ordenServicioId,
      casoRelacionadoId: valores.casoRelacionadoId,
      asunto: valores.asunto!,
      descripcion: valores.descripcion!,
    };

    this.casoService.registrar(payload).subscribe({
      next: (creado) => this.subirEvidenciaSiCorresponde(creado),
      error: (error: HttpErrorResponse) => {
        this.enviando = false;
        this.errorMensaje = error.error?.mensaje ?? 'No se pudo registrar la solicitud. Intentá de nuevo.';
      },
    });
  }

  private subirEvidenciaSiCorresponde(creado: CasoCreadoDTO): void {
    if (!this.archivoSeleccionado || !this.tipoSeleccionado?.permiteEvidencia) {
      this.enviando = false;
      this.boletaConfirmada = creado;
      return;
    }

    this.casoService.adjuntarEvidencia(creado.id, this.archivoSeleccionado).subscribe({
      next: () => {
        this.enviando = false;
        this.boletaConfirmada = creado;
      },
      error: (error: HttpErrorResponse) => {
        this.enviando = false;
        this.boletaConfirmada = creado;
        this.archivoErrorMensaje = error.error?.mensaje
          ?? 'El archivo no pudo adjuntarse. Verificá que el formato sea válido y que no exceda el tamaño máximo permitido.';
      },
    });
  }

  cancelar(): void {
    if (confirm('¿Deseás cancelar? La información ingresada no se guardará.')) {
      this.cancelado.emit();
    }
  }
}
