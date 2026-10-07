import { Component, inject } from '@angular/core';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-toast-container',
  template: `
    <div class="toasts" aria-live="polite" aria-atomic="false">
      @for (t of toasts.toasts(); track t.id) {
        <div class="toast" [class]="'toast toast-' + t.kind" [attr.role]="t.kind === 'error' ? 'alert' : 'status'">
          <span>{{ t.message }}</span>
          <button type="button" class="close" (click)="toasts.dismiss(t.id)" aria-label="Cerrar notificación">
            ×
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .toasts {
      position: fixed;
      right: var(--space-4);
      bottom: var(--space-4);
      left: var(--space-4);
      z-index: 100;
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: var(--space-2);
      pointer-events: none;
    }
    .toast {
      pointer-events: auto;
      display: flex;
      align-items: center;
      gap: var(--space-3);
      max-width: 420px;
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius);
      border-left: 5px solid currentcolor;
      box-shadow: var(--shadow-lg);
      animation: slide-in 0.2s ease-out;
    }
    .toast-success {
      background: var(--success-bg);
      color: var(--success-fg);
    }
    .toast-error {
      background: var(--danger-bg);
      color: var(--danger-fg);
    }
    .toast-info {
      background: var(--info-bg);
      color: var(--info-fg);
    }
    .close {
      margin-left: auto;
      border: 0;
      background: transparent;
      color: inherit;
      font-size: 1.4rem;
      line-height: 1;
      cursor: pointer;
      padding: 0 var(--space-1);
    }
    @keyframes slide-in {
      from {
        opacity: 0;
        transform: translateY(8px);
      }
    }
  `,
})
export class ToastContainer {
  protected toasts = inject(ToastService);
}
