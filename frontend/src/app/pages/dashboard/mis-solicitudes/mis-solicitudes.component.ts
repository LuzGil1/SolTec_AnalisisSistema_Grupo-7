import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { CasoService, CasoResumenDTO } from '../../../core/services/caso.service';

@Component({
  selector: 'app-mis-solicitudes',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './mis-solicitudes.component.html',
  styleUrl: './mis-solicitudes.component.scss',
})
export class MisSolicitudesComponent implements OnInit {
  private readonly casoService = inject(CasoService);

  casos: CasoResumenDTO[] = [];
  cargando = true;
  error = false;

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
}
