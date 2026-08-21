import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

interface ConfigRol {
  etiqueta: string;
  color: string;
  colorTexto: string;
  menu: string[];
}

const CONFIG_ROLES: Record<string, ConfigRol> = {
  CLIENTE: { etiqueta: 'Cliente', color: '#c084fc', colorTexto: '#ffffff', menu: ['Inicio', 'Mis solicitudes', 'Nueva solicitud', 'Mi perfil'] },
  TECNICO: { etiqueta: 'Técnico de soporte', color: '#a855f7', colorTexto: '#ffffff', menu: ['Inicio', 'Mis casos', 'Solicitar siguiente caso', 'Mi perfil'] },
  SUPERVISOR: { etiqueta: 'Supervisor', color: '#7e22ce', colorTexto: '#ffffff', menu: ['Inicio', 'Denuncias', 'Escalamientos', 'Reportes'] },
  ADMIN: { etiqueta: 'Administrador', color: '#4c1d95', colorTexto: '#ffffff', menu: ['Inicio', 'Usuarios', 'Catálogos', 'Parámetros'] },
  AUDITOR: { etiqueta: 'Auditor', color: '#e9d5ff', colorTexto: '#4c1d95', menu: ['Inicio', 'Bitácora', 'Reporte de auditoría'] },
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  rol = this.authService.getRol() ?? 'CLIENTE';
  nombre = this.authService.getNombre() ?? '';
  config = CONFIG_ROLES[this.rol] ?? CONFIG_ROLES['CLIENTE'];

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
    this.router.navigateByUrl('/login');
  }
}
