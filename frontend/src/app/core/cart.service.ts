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

  quantityOf(productId: number): number {
    return this._lines().find((l) => l.product.id === productId)?.quantity ?? 0;
  }

  /** Agrega una unidad; no supera el stock conocido del producto. */
  add(product: Product): void {
    this._lines.update((lines) => {
      const existing = lines.find((l) => l.product.id === product.id);
      if (existing) {
        if (existing.quantity >= product.stock) {
          return lines;
        }
        return lines.map((l) =>
          l.product.id === product.id ? { ...l, quantity: l.quantity + 1 } : l,
        );
      }
      return [...lines, { product, quantity: 1 }];
    });
  }

  /** Fija la cantidad entre 1 y el stock conocido. Para quitar la línea usa remove(). */
  setQuantity(productId: number, quantity: number): void {
    this._lines.update((lines) =>
      lines.map((l) => {
        if (l.product.id !== productId) {
          return l;
        }
        const max = Math.max(1, l.product.stock);
        return { ...l, quantity: Math.min(Math.max(1, Math.floor(quantity) || 1), max) };
      }),
    );
  }

  remove(productId: number): void {
    this._lines.update((lines) => lines.filter((l) => l.product.id !== productId));
  }

  clear(): void {
    this._lines.set([]);
  }
}