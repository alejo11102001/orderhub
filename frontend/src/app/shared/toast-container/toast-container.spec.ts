import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ToastService } from '../../core/toast.service';
import { ToastContainer } from './toast-container';

describe('ToastContainer', () => {
  it('muestra los toasts en una región aria-live y los puede cerrar', () => {
    const fixture = TestBed.createComponent(ToastContainer);
    const toasts = TestBed.inject(ToastService);
    toasts.show('error', 'No se pudo guardar', 0);
    toasts.show('success', 'Guardado', 0);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[aria-live="polite"]')).toBeTruthy();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('No se pudo guardar');
    expect(root.querySelector('[role="status"]')?.textContent).toContain('Guardado');

    (root.querySelector('button.close') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(root.querySelectorAll('.toast').length).toBe(1);
  });
});
