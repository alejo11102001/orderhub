import { HttpErrorResponse } from '@angular/common/http';

/** Mensaje amigable para un 429; usa Retry-After (segundos) si el servidor lo envía. */
export function tooManyAttemptsMessage(err: HttpErrorResponse): string {
  const seconds = Number(err.headers?.get('Retry-After'));
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return 'Demasiados intentos fallidos. Inténtalo de nuevo más tarde.';
  }
  const minutes = Math.ceil(seconds / 60);
  return `Demasiados intentos fallidos. Inténtalo de nuevo en ${minutes} ${minutes === 1 ? 'minuto' : 'minutos'}.`;
}
