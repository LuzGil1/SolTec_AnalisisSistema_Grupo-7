import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AdjuntoDTO, CasoDetalleDTO, CasoResumenDTO, CasoService } from '../../../core/services/caso.service';

@Component({
  selector: 'app-mis-solicitudes',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './mis-solicitudes.component.html',
  styleUrl: './mis-solicitudes.component.scss',
})
export class MisSolicitudesComponent implements OnInit {
  private readonly casoService = inject(CasoService);

  @Output() irANuevaSolicitud = new EventEmitter<void>();

  casos: CasoResumenDTO[] = [];
  cargando = true;
  error = false;

  casoIdSeleccionado: number | null = null;
  detalleSeleccionado: CasoDetalleDTO | null = null;
  detalleCargando = false;
  detalleError = false;
  previewUrls: Record<number, string> = {};

  ngOnInit(): void {
    this.casoService.listarMisCasos().subscribe({
      next: (casos) => {
        this.casos = casos;
        this.cargando = false;
      },
      error: () => {
        this.error = true;
        this.cargando = false;
      },
    });
  }

  abrirDetalle(casoId: number): void {
    this.casoIdSeleccionado = casoId;
    this.detalleCargando = true;
    this.detalleError = false;
    this.detalleSeleccionado = null;

    this.casoService.obtenerDetalle(casoId).subscribe({
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
    this.limpiarPreviews();
  }

  esImagen(adjunto: AdjuntoDTO): boolean {
    return adjunto.tipoMime.startsWith('image/');
  }

  descargarAdjunto(adjunto: AdjuntoDTO): void {
    if (this.casoIdSeleccionado === null) {
      return;
    }
    this.casoService.descargarAdjunto(this.casoIdSeleccionado, adjunto.id).subscribe((blob) => {
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

  private cargarPreviewsImagenes(casoId: number, adjuntos: AdjuntoDTO[]): void {
    adjuntos.filter((adjunto) => this.esImagen(adjunto)).forEach((adjunto) => {
      this.casoService.descargarAdjunto(casoId, adjunto.id).subscribe((blob) => {
        this.previewUrls[adjunto.id] = URL.createObjectURL(blob);
      });
    });
  }

  private limpiarPreviews(): void {
    Object.values(this.previewUrls).forEach((url) => URL.revokeObjectURL(url));
    this.previewUrls = {};
  }
}
