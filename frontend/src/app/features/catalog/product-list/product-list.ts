import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { Router } from '@angular/router';
import { ProductService } from '../../../core/product.service';
import { Page, Product } from '../../../core/models';
import { CartService } from '../../../core/cart.service';
import { AuthService } from '../../../core/auth.service';
import { ToastService } from '../../../core/toast.service';
import { Pager } from '../../../shared/pager/pager';

export const LOW_STOCK_THRESHOLD = 5;

export type StockLevel = 'out' | 'low' | 'ok';

export function stockLevel(stock: number): StockLevel {
  if (stock <= 0) {
    return 'out';
  }
  return stock <= LOW_STOCK_THRESHOLD ? 'low' : 'ok';
}

@Component({
  selector: 'app-product-list',
  imports: [CurrencyPipe, Pager],
  templateUrl: './product-list.html',
  styleUrl: './product-list.css',
})
export class ProductList {
  private service = inject(ProductService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private toasts = inject(ToastService);

  cart = inject(CartService);
  products = signal<Product[]>([]);
  page = signal(0);
  totalPages = signal(0);
  loading = signal(true);
  error = signal(false);
  skeletons = Array.from({ length: 8 });

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.error.set(false);
    this.service.list(page).subscribe({
      next: (res: Page<Product>) => {
        this.products.set(res.content);
        this.page.set(res.number);
        this.totalPages.set(res.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  level(p: Product): StockLevel {
    return stockLevel(p.stock);
  }

  /** Ya no se pueden agregar más unidades: sin stock o todo el stock ya está en el carrito. */
  unavailable(p: Product): boolean {
    return p.stock <= 0 || this.cart.quantityOf(p.id) >= p.stock;
  }

  add(p: Product): void {
    if (!this.auth.isLoggedIn()) {
      this.toasts.info('Inicia sesión para agregar productos al carrito');
      this.router.navigate(['/login']);
      return;
    }
    this.cart.add(p);
    this.toasts.success(`${p.name} agregado al carrito`);
  }
}
