import { Component, DestroyRef, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';
import {
  CasoGeneralDTO,
  FiltrosCasos,
  OpcionFiltroDTO,
  ResumenSupervisorDTO,
  SituacionTecnicoDTO,
  SupervisorService,
} from '../../../../core/services/supervisor.service';
import { Vencimiento, describirVencimiento } from '../../../../core/utils/tiempo';
import { DetalleCasoSupervisorComponent } from '../detalle-caso/detalle-caso.component';

interface Tarjeta {
  titulo: string;
  destino: string;
  valor: (resumen: ResumenSupervisorDTO) => number;
}

const FILTROS_VACIOS: FiltrosCasos = {
  tipo: null,
  estado: null,
  tecnicoId: null,
  fechaDesde: null,
  fechaHasta: null,
  boleta: null,
};

@Component({
  selector: 'app-inicio-supervisor',
  standalone: true,
  imports: [FormsModule, DetalleCasoSupervisorComponent],
  templateUrl: './inicio-supervisor.component.html',
})
export class InicioSupervisorComponent implements OnInit {
  private readonly supervisorService = inject(SupervisorService);
  private readonly destroyRef = inject(DestroyRef);

  // Cada tarjeta lleva a la opcion del menu lateral con su listado
  @Output() irA = new EventEmitter<string>();

  readonly tarjetas: Tarjeta[] = [
    { titulo: 'Denuncias pendientes', destino: 'Denuncias', valor: (r) => r.denunciasPendientes },
    { titulo: 'Escalamientos pendientes', destino: 'Escalamientos', valor: (r) => r.escalamientosPendientes },
    { titulo: 'Solicitudes sin asignar', destino: 'Sin asignar', valor: (r) => r.solicitudesSinAsignar },
  ];

  resumen: ResumenSupervisorDTO | null = null;
  tipos: OpcionFiltroDTO[] = [];
  estados: OpcionFiltroDTO[] = [];
  tecnicos: SituacionTecnicoDTO[] = [];

  filtros: FiltrosCasos = { ...FILTROS_VACIOS };
  casos: CasoGeneralDTO[] = [];
  cargando = true;
  error = '';

  casoIdSeleccionado: number | null = null;

  // Cada cambio de filtro pasa por aqui: el debounce evita una consulta
  // por tecla en la busqueda por boleta, y switchMap descarta respuestas
  // viejas si el usuario sigue cambiando filtros.
  private readonly recarga$ = new Subject<number>();

  ngOnInit(): void {
    this.supervisorService.obtenerResumen().subscribe({ next: (r) => (this.resumen = r) });
    this.supervisorService.obtenerFiltros().subscribe({
      next: (f) => {
        this.tipos = f.tipos;
        this.estados = f.estados;
      },
    });
    this.supervisorService.listarTecnicos().subscribe({ next: (t) => (this.tecnicos = t) });

    this.recarga$
      .pipe(
        debounceTime(300),
        switchMap(() => {
          this.cargando = true;
          // El error se atrapa por peticion: si llegara al subscribe, el
          // stream moriria y los filtros dejarian de responder.
          return this.supervisorService.listarCasos(this.filtros).pipe(
            catchError((err) => {
              this.error = err?.error?.mensaje ?? 'No se pudo cargar el listado de solicitudes.';
              return of(null);
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((casos) => {
        if (casos) {
          this.casos = casos;
          this.error = '';
        } else {
          this.casos = [];
        }
        this.cargando = false;
      });

    this.recargar();
  }

  recargar(): void {
    this.recarga$.next(Date.now());
  }

  limpiarFiltros(): void {
    this.filtros = { ...FILTROS_VACIOS };
    this.recargar();
  }

  get hayFiltros(): boolean {
    return Object.values(this.filtros).some((v) => v !== null && v !== undefined && `${v}`.trim() !== '');
  }

  vencimiento(caso: CasoGeneralDTO): Vencimiento {
    return describirVencimiento(caso.fechaLimite, caso.esFinal);
  }

  abrirDetalle(casoId: number): void {
    this.casoIdSeleccionado = casoId;
  }

  cerrarDetalle(): void {
    this.casoIdSeleccionado = null;
  }
}
