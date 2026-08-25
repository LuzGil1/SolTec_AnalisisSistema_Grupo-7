import { Component, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  enviando = false;
  errorMensaje = '';

  form = this.fb.nonNullable.group({
    correo: ['', [Validators.required, Validators.email]],
    contrasena: ['', [Validators.required]],
  });

  get correo() {
    return this.form.controls.correo;
  }

  get contrasena() {
    return this.form.controls.contrasena;
  }

  enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMensaje = '';
    this.enviando = true;

    this.authService.login(this.form.getRawValue()).subscribe({
      next: (respuesta) => {
        this.enviando = false;
        this.router.navigateByUrl(this.authService.rutaSegunRol(respuesta.rol));
      },
      error: (error: HttpErrorResponse) => {
        this.enviando = false;
        this.errorMensaje = error.error?.mensaje ?? 'No se pudo iniciar sesión. Intentá de nuevo.';
      },
    });
  }
}
