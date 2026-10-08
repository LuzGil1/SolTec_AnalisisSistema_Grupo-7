import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { NuevaSolicitudComponent } from './nueva-solicitud/nueva-solicitud.component';
import { MisSolicitudesComponent } from './mis-solicitudes/mis-solicitudes.component';
import { MisCasosComponent } from './mis-casos/mis-casos.component';
import { InicioSupervisorComponent } from './supervisor/inicio-supervisor/inicio-supervisor.component';
import { DenunciasComponent } from './supervisor/denuncias/denuncias.component';
import { EscalamientosComponent } from './supervisor/escalamientos/escalamientos.component';
import { SugerenciasComponent } from './supervisor/sugerencias/sugerencias.component';
import { SinAsignarComponent } from './supervisor/sin-asignar/sin-asignar.component';
import { TecnicosComponent } from './supervisor/tecnicos/tecnicos.component';

interface ConfigRol {
  etiqueta: string;
  color: string;
  colorTexto: string;
  menu: string[];
}

const CONFIG_ROLES: Record<string, ConfigRol> = {
  CLIENTE: { etiqueta: 'Cliente', color: '#c084fc', colorTexto: '#ffffff', menu: ['Mis solicitudes', 'Nueva solicitud'] },
  TECNICO: { etiqueta: 'Técnico de soporte', color: '#a855f7', colorTexto: '#ffffff', menu: ['Inicio', 'Mis casos'] },
  SUPERVISOR: { etiqueta: 'Supervisor', color: '#7e22ce', colorTexto: '#ffffff', menu: ['Inicio', 'Denuncias', 'Escalamientos', 'Sin asignar', 'Sugerencias', 'Técnicos'] },
  ADMIN: { etiqueta: 'Administrador', color: '#4c1d95', colorTexto: '#ffffff', menu: ['Inicio', 'Usuarios', 'Catálogos', 'Parámetros'] },
  AUDITOR: { etiqueta: 'Auditor', color: '#e9d5ff', colorTexto: '#4c1d95', menu: ['Inicio', 'Bitácora', 'Reporte de auditoría'] },
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    NuevaSolicitudComponent,
    MisSolicitudesComponent,
    MisCasosComponent,
    InicioSupervisorComponent,
    DenunciasComponent,
    EscalamientosComponent,
    SugerenciasComponent,
    SinAsignarComponent,
    TecnicosComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  rol = this.authService.getRol() ?? 'CLIENTE';
  nombre = this.authService.getNombre() ?? '';
  config = CONFIG_ROLES[this.rol] ?? CONFIG_ROLES['CLIENTE'];

  vistaActual = signal(this.config.menu[0] ?? 'Inicio');

  seleccionar(opcion: string): void {
    this.vistaActual.set(opcion);
  }

  get iniciales(): string {
    return this.nombre
      .split(' ')
      .filter((parte) => parte.length > 0)
      .slice(0, 2)
      .map((parte) => parte[0]?.toUpperCase())
      .join('');
  }

  cerrarSesion(): void {
    this.authService.logout();
    this.router.navigateByUrl('/');
  }
}
