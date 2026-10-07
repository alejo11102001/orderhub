import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { tooManyAttemptsMessage } from './rate-limit';

const err = (retryAfter?: string) =>
  new HttpErrorResponse({
    status: 429,
    headers: retryAfter ? new HttpHeaders({ 'Retry-After': retryAfter }) : undefined,
  });

describe('tooManyAttemptsMessage', () => {
  it('convierte Retry-After a minutos redondeando hacia arriba', () => {
    expect(tooManyAttemptsMessage(err('61'))).toContain('2 minutos');
    expect(tooManyAttemptsMessage(err('30'))).toContain('1 minuto.');
  });

  it('usa un mensaje genérico sin cabecera o con valor inválido', () => {
    expect(tooManyAttemptsMessage(err())).toContain('más tarde');
    expect(tooManyAttemptsMessage(err('abc'))).toContain('más tarde');
  });
});
