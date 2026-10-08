import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AdjuntoDTO } from '../../../../core/services/caso.service';
import { CasoSupervisorDetalleDTO, FuenteDetalle, SupervisorService } from '../../../../core/services/supervisor.service';

// Ventana emergente con el detalle completo de un caso. En la consulta
// general es de solo lectura; las pantallas de denuncias, escalamientos y
// sugerencias proyectan sus acciones dentro (<ng-content>) y usan
// recargar() despues de cada gestion.
@Component({
  selector: 'app-detalle-caso-supervisor',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './detalle-caso.component.html',
})
export class DetalleCasoSupervisorComponent implements OnInit, OnDestroy {
  private readonly supervisorService = inject(SupervisorService);

  @Input({ required: true }) casoId!: number;
  @Input() fuente: FuenteDetalle = 'general';
  @Output() cerrado = new EventEmitter<void>();
  @Output() cargado = new EventEmitter<CasoSupervisorDetalleDTO>();

  detalle: CasoSupervisorDetalleDTO | null = null;
  cargando = true;
  error = false;
  previewUrls: Record<number, string> = {};

  ngOnInit(): void {
    this.recargar();
  }

  ngOnDestroy(): void {
    this.limpiarPreviews();
  }

  recargar(): void {
    this.error = false;
    this.cargando = this.detalle === null;
    this.supervisorService.obtenerDetalleDesde(this.fuente, this.casoId).subscribe({
      next: (detalle) => {
        this.detalle = detalle;
        this.cargando = false;
        this.limpiarPreviews();
        this.cargarPreviewsImagenes(detalle.adjuntos);
        this.cargado.emit(detalle);
      },
      error: () => {
        this.error = true;
        this.cargando = false;
      },
    });
  }

  cerrar(): void {
    this.cerrado.emit();
  }

  esImagen(adjunto: AdjuntoDTO): boolean {
    return adjunto.tipoMime.startsWith('image/');
  }

  descargarAdjunto(adjunto: AdjuntoDTO): void {
    this.supervisorService.descargarAdjunto(this.casoId, adjunto.id).subscribe((blob) => {
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

  private cargarPreviewsImagenes(adjuntos: AdjuntoDTO[]): void {
    adjuntos
      .filter((adjunto) => this.esImagen(adjunto))
      .forEach((adjunto) => {
        this.supervisorService.descargarAdjunto(this.casoId, adjunto.id).subscribe((blob) => {
          this.previewUrls[adjunto.id] = URL.createObjectURL(blob);
        });
      });
  }

  private limpiarPreviews(): void {
    Object.values(this.previewUrls).forEach((url) => URL.revokeObjectURL(url));
    this.previewUrls = {};
  }
}
