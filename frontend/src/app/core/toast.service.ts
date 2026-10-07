import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

const DEFAULT_DURATION_MS = 4500;

@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  private _toasts = signal<Toast[]>([]);

  readonly toasts = this._toasts.asReadonly();

  success(message: string): void {
    this.show('success', message);
  }

  error(message: string): void {
    this.show('error', message);
  }

  info(message: string): void {
    this.show('info', message);
  }

  show(kind: ToastKind, message: string, durationMs = DEFAULT_DURATION_MS): void {
    const id = this.nextId++;
    this._toasts.update((list) => [...list, { id, kind, message }]);
    if (durationMs > 0) {
      setTimeout(() => this.dismiss(id), durationMs);
    }
  }

  dismiss(id: number): void {
    this._toasts.update((list) => list.filter((t) => t.id !== id));
  }
}
