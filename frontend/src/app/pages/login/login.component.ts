import { Component, OnInit, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  enviando = false;
  errorMensaje = '';
  registroExitoso = false;

  ngOnInit(): void {
    this.registroExitoso = this.route.snapshot.queryParamMap.get('registrado') === '1';
  }

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
