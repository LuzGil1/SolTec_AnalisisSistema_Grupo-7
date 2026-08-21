import { Component, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-registro',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './registro.component.html',
  styleUrl: './registro.component.scss',
})
export class RegistroComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  enviando = false;
  errorMensaje = '';

  form = this.fb.nonNullable.group({
    nombres: ['', [Validators.required, Validators.maxLength(100)]],
    apellidos: ['', [Validators.required, Validators.maxLength(100)]],
    correo: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    contrasena: ['', [Validators.required, Validators.minLength(8)]],
    telefono: ['', [Validators.maxLength(20)]],
    nit: ['', [Validators.maxLength(20)]],
    direccion: ['', [Validators.maxLength(250)]],
  });

  get f() {
    return this.form.controls;
  }

  enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMensaje = '';
    this.enviando = true;

    this.authService.registro(this.form.getRawValue()).subscribe({
      next: () => {
        this.enviando = false;
        this.router.navigate(['/login'], { queryParams: { registrado: '1' } });
      },
      error: (error: HttpErrorResponse) => {
        this.enviando = false;
        this.errorMensaje = error.error?.mensaje ?? 'No se pudo completar el registro. Intentá de nuevo.';
      },
    });
  }
}
