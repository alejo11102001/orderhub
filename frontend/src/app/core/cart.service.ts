import { Injectable, computed, signal } from '@angular/core';
import { CartLine, Product } from './models';

@Injectable({ providedIn: 'root' })
export class CartService {
  private _lines = signal<CartLine[]>([]);

  readonly lines = this._lines.asReadonly();
  readonly count = computed(() => this._lines().reduce((n, l) => n + l.quantity, 0));
  readonly total = computed(() =>
    this._lines().reduce((sum, l) => sum + l.product.price * l.quantity, 0),
  );

  add(product: Product): void {
    this._lines.update((lines) => {
      const existing = lines.find((l) => l.product.id === product.id);
      if (existing) {
        return lines.map((l) =>
          l.product.id === product.id ? { ...l, quantity: l.quantity + 1 } : l,
        );
      }
      return [...lines, { product, quantity: 1 }];
    });
  }

  remove(productId: number): void {
    this._lines.update((lines) => lines.filter((l) => l.product.id !== productId));
  }

  clear(): void {
    this._lines.set([]);
  }
}