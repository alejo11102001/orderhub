import { Component, computed, input, output } from '@angular/core';

@Component({
  selector: 'app-pager',
  template: `
    @if (totalPages() > 1) {
      <nav class="pager" aria-label="Paginación">
        <button type="button" class="btn btn-sm" (click)="go(page() - 1)" [disabled]="!hasPrev()">
          ← Anterior
        </button>
        <span aria-live="polite">Página {{ page() + 1 }} de {{ totalPages() }}</span>
        <button type="button" class="btn btn-sm" (click)="go(page() + 1)" [disabled]="!hasNext()">
          Siguiente →
        </button>
      </nav>
    }
  `,
  styles: `
    .pager {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: var(--space-4);
      margin-top: var(--space-5);
      color: var(--muted);
      font-size: var(--text-sm);
    }
  `,
})
export class Pager {
  /** Página actual, base 0. */
  page = input.required<number>();
  totalPages = input.required<number>();
  pageChange = output<number>();

  hasPrev = computed(() => this.page() > 0);
  hasNext = computed(() => this.page() + 1 < this.totalPages());

  go(target: number): void {
    if (target >= 0 && target < this.totalPages() && target !== this.page()) {
      this.pageChange.emit(target);
    }
  }
}
