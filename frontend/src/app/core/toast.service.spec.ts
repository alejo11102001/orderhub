import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let toasts: ToastService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
    toasts = TestBed.inject(ToastService);
  });

  afterEach(() => vi.useRealTimers());

  it('agrega toasts con su tipo', () => {
    toasts.success('listo');
    toasts.error('falló');
    expect(toasts.toasts().map((t) => [t.kind, t.message])).toEqual([
      ['success', 'listo'],
      ['error', 'falló'],
    ]);
  });

  it('asigna ids únicos', () => {
    toasts.info('a');
    toasts.info('b');
    const [a, b] = toasts.toasts();
    expect(a.id).not.toBe(b.id);
  });

  it('se descarta solo tras la duración', () => {
    toasts.show('info', 'temporal', 1000);
    vi.advanceTimersByTime(999);
    expect(toasts.toasts().length).toBe(1);
    vi.advanceTimersByTime(1);
    expect(toasts.toasts().length).toBe(0);
  });

  it('dismiss quita solo el toast indicado', () => {
    toasts.info('a');
    toasts.info('b');
    toasts.dismiss(toasts.toasts()[0].id);
    expect(toasts.toasts().map((t) => t.message)).toEqual(['b']);
  });
});
