import { Routes } from '@angular/router';
import { PortalComponent } from './pages/portal/portal.component';
import { LoginComponent } from './pages/login/login.component';
import { RegistroComponent } from './pages/registro/registro.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', component: PortalComponent },
  { path: 'login', component: LoginComponent },
  { path: 'registro', component: RegistroComponent },
  { path: 'cliente', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'tecnico', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'supervisor', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'admin', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'auditor', component: DashboardComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];
