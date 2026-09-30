import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { Router } from '@angular/router';
import { CartService } from '../../../core/cart.service';
import { OrderService } from '../../../core/order.service';

@Component({
  selector: 'app-cart',
  imports: [CurrencyPipe],
  templateUrl: './cart.html',
  styleUrl: './cart.css',
})
export class Cart {
  cart = inject(CartService);
  private orders = inject(OrderService);
  private router = inject(Router);

  error = signal<string | null>(null);
  loading = signal(false);

  checkout(): void {
    this.loading.set(true);
    this.error.set(null);
    this.orders.create(this.cart.lines()).subscribe({
      next: () => {
        this.cart.clear();
        this.router.navigate(['/orders']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(
          err.status === 409 ? 'Stock insuficiente para algún producto' : 'No se pudo crear el pedido',
        );
      },
    });
  }
}