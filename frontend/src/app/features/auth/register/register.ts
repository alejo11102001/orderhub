import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { ToastService } from '../../../core/toast.service';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const { password, confirm } = group.value;
  return password === confirm ? null : { mismatch: true };
}

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: '../login/login.css',
})
export class Register {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private toasts = inject(ToastService);

  error = signal<string | null>(null);
  loading = signal(false);
  submitted = signal(false);

  // Los límites coinciden con RegisterRequest del backend (8..72)
  form = this.fb.nonNullable.group(
    {
      email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirm: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  showError(name: 'email' | 'password' | 'confirm'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  showMismatch(): boolean {
    return (
      this.form.hasError('mismatch') &&
      !this.form.controls.confirm.hasError('required') &&
      (this.form.controls.confirm.touched || this.submitted())
    );
  }

  submit(): void {
    this.submitted.set(true);
    this.error.set(null);
    if (this.form.invalid || this.loading()) {
      return;
    }
    this.loading.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth
      .register(email, password)
      .pipe(switchMap(() => this.auth.login(email, password)))
      .subscribe({
        next: () => {
          this.toasts.success('Cuenta creada. ¡Bienvenido!');
          this.router.navigate(['/products']);
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(
            err.status === 409
              ? 'Ese correo ya está registrado'
              : 'No se pudo crear la cuenta. Revisa los datos e inténtalo de nuevo.',
          );
        },
      });
  }
}
