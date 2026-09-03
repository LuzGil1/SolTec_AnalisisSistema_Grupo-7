import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdjuntoDTO } from '../../../core/services/caso.service';
import {
  CargaTecnicoDTO,
  CasoTecnicoDetalleDTO,
  CasoTecnicoResumenDTO,
  TecnicoCasoService,
} from '../../../core/services/tecnico-caso.service';

interface Vencimiento {
  texto: string;
  clase: string;
}

@Component({
  selector: 'app-mis-casos',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './mis-casos.component.html',
  styleUrl: './mis-casos.component.scss',
})
export class MisCasosComponent implements OnInit {
  private readonly tecnicoCasoService = inject(TecnicoCasoService);

  casos: CasoTecnicoResumenDTO[] = [];
  carga: CargaTecnicoDTO | null = null;
  cargando = true;
  error = false;

  casoIdSeleccionado: number | null = null;
  detalleSeleccionado: CasoTecnicoDetalleDTO | null = null;
  detalleCargando = false;
  detalleError = false;
  previewUrls: Record<number, string> = {};

  avanceTexto = '';
  avanceError = '';
  guardandoAvance = false;

  ngOnInit(): void {
    this.cargarListaYCarga(true);
  }

  private cargarListaYCarga(esInicial = false): void {
    if (esInicial) {
      this.cargando = true;
    }
    this.tecnicoCasoService.listarMisCasos().subscribe({
      next: (casos) => {
        this.casos = casos;
        this.cargando = false;
      },
      error: () => {
        this.error = true;
        this.cargando = false;
      },
    });
    this.tecnicoCasoService.obtenerCarga().subscribe({
      next: (carga) => (this.carga = carga),
    });
  }

  get vencenHoy(): number {
    const hoy = new Date();
    return this.casos.filter((c) => {
      if (!c.fechaLimiteResolucion) {
        return false;
      }
      const limite = new Date(c.fechaLimiteResolucion);
      return (
        limite.getTime() >= Date.now() &&
        limite.getFullYear() === hoy.getFullYear() &&
        limite.getMonth() === hoy.getMonth() &&
        limite.getDate() === hoy.getDate()
      );
    }).length;
  }

  get vencidos(): number {
    return this.casos.filter(
      (c) => c.fechaLimiteResolucion !== null && new Date(c.fechaLimiteResolucion).getTime() < Date.now()
    ).length;
  }

  vencimiento(fechaLimite: string | null): Vencimiento {
    if (!fechaLimite) {
      return { texto: 'Sin vencimiento', clase: 'text-brand-text-muted' };
    }

    const diffMs = new Date(fechaLimite).getTime() - Date.now();
    const horas = Math.abs(diffMs) / (1000 * 60 * 60);

    if (diffMs < 0) {
      const dias = Math.floor(horas / 24);
      const texto = dias >= 1
        ? `Vencido hace ${dias} día${dias === 1 ? '' : 's'}`
        : `Vencido hace ${Math.max(1, Math.round(horas))} hora${Math.round(horas) === 1 ? '' : 's'}`;
      return { texto, clase: 'text-red-600 font-semibold' };
    }

    if (horas < 6) {
      const redondeado = Math.max(1, Math.round(horas));
      return { texto: `Vence en ${redondeado} hora${redondeado === 1 ? '' : 's'}`, clase: 'text-amber-600 font-semibold' };
    }

    if (horas < 24) {
      const redondeado = Math.round(horas);
      return { texto: `En ${redondeado} hora${redondeado === 1 ? '' : 's'}`, clase: 'text-brand-text-dark' };
    }

    const dias = Math.round(horas / 24);
    return { texto: `En ${dias} día${dias === 1 ? '' : 's'}`, clase: 'text-brand-text-dark' };
  }

  abrirDetalle(casoId: number): void {
    this.casoIdSeleccionado = casoId;
    this.detalleCargando = true;
    this.detalleError = false;
    this.detalleSeleccionado = null;
    this.avanceTexto = '';
    this.avanceError = '';

    this.tecnicoCasoService.obtenerDetalle(casoId).subscribe({
      next: (detalle) => {
        this.detalleSeleccionado = detalle;
        this.detalleCargando = false;
        this.cargarPreviewsImagenes(casoId, detalle.adjuntos);
      },
      error: () => {
        this.detalleError = true;
        this.detalleCargando = false;
      },
    });
  }

  cerrarDetalle(): void {
    this.casoIdSeleccionado = null;
    this.detalleSeleccionado = null;
    this.detalleError = false;
    this.avanceTexto = '';
    this.avanceError = '';
    this.limpiarPreviews();
  }

  esImagen(adjunto: AdjuntoDTO): boolean {
    return adjunto.tipoMime.startsWith('image/');
  }

  descargarAdjunto(adjunto: AdjuntoDTO): void {
    if (this.casoIdSeleccionado === null) {
      return;
    }
    this.tecnicoCasoService.descargarAdjunto(this.casoIdSeleccionado, adjunto.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = adjunto.nombreArchivo;
      document.body.appendChild(enlace);
      enlace.click();
      document.body.removeChild(enlace);
      URL.revokeObjectURL(url);
    });
  }

  registrarAvance(nuevoEstado: string | null): void {
    if (!this.avanceTexto.trim()) {
      this.avanceError = 'Escriba el avance antes de continuar.';
      return;
    }
    if (this.casoIdSeleccionado === null) {
      return;
    }

    const casoId = this.casoIdSeleccionado;
    this.avanceError = '';
    this.guardandoAvance = true;

    this.tecnicoCasoService.registrarAvance(casoId, { comentario: this.avanceTexto.trim(), nuevoEstado }).subscribe({
      next: () => {
        this.avanceTexto = '';
        this.guardandoAvance = false;
        this.recargarDetalle(casoId);
        this.cargarListaYCarga();
      },
      error: (err) => {
        this.guardandoAvance = false;
        this.avanceError = err?.error?.mensaje ?? 'No se pudo registrar el avance.';
      },
    });
  }

  private recargarDetalle(casoId: number): void {
    this.tecnicoCasoService.obtenerDetalle(casoId).subscribe({
      next: (detalle) => {
        this.detalleSeleccionado = detalle;
        this.limpiarPreviews();
        this.cargarPreviewsImagenes(casoId, detalle.adjuntos);
      },
    });
  }

  private cargarPreviewsImagenes(casoId: number, adjuntos: AdjuntoDTO[]): void {
    adjuntos
      .filter((adjunto) => this.esImagen(adjunto))
      .forEach((adjunto) => {
        this.tecnicoCasoService.descargarAdjunto(casoId, adjunto.id).subscribe((blob) => {
          this.previewUrls[adjunto.id] = URL.createObjectURL(blob);
        });
      });
  }

  private limpiarPreviews(): void {
    Object.values(this.previewUrls).forEach((url) => URL.revokeObjectURL(url));
    this.previewUrls = {};
  }
}
