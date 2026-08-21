import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ServicioPublicoService, ServicioDTO } from '../../core/services/servicio-publico.service';

@Component({
  selector: 'app-portal',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './portal.component.html',
  styleUrl: './portal.component.scss',
})
export class PortalComponent implements OnInit {
  private readonly servicioPublicoService = inject(ServicioPublicoService);

  servicios: ServicioDTO[] = [];
  cargandoServicios = true;
  errorServicios = false;

  ngOnInit(): void {
    this.servicioPublicoService.listarServicios().subscribe({
      next: (servicios) => {
        this.servicios = servicios;
        this.cargandoServicios = false;
      },
      error: () => {
        this.errorServicios = true;
        this.cargandoServicios = false;
      },
    });
  }
}
