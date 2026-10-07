import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { CartService } from '../../../core/cart.service';
import { OrderService } from '../../../core/order.service';
import { ToastService } from '../../../core/toast.service';
import { CartLine } from '../../../core/models';

@Component({
  selector: 'app-cart',
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './cart.html',
  styleUrl: './cart.css',
})
export class Cart {
  cart = inject(CartService);
  private orders = inject(OrderService);
  private router = inject(Router);
  private toasts = inject(ToastService);

  error = signal<string | null>(null);
  loading = signal(false);

  increment(line: CartLine): void {
    this.cart.setQuantity(line.product.id, line.quantity + 1);
  }

  decrement(line: CartLine): void {
    this.cart.setQuantity(line.product.id, line.quantity - 1);
  }

  remove(line: CartLine): void {
    this.cart.remove(line.product.id);
    this.toasts.info(`${line.product.name} eliminado del carrito`);
  }

  checkout(): void {
    this.loading.set(true);
    this.error.set(null);
    this.orders.create(this.cart.lines()).subscribe({
      next: () => {
        this.cart.clear();
        this.toasts.success('Pedido creado. Ya puedes pagarlo desde "Mis pedidos".');
        this.router.navigate(['/orders']);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 409
            ? 'Algún producto ya no tiene stock suficiente. Ajusta las cantidades o quítalo del carrito e inténtalo de nuevo.'
            : 'No se pudo crear el pedido. Inténtalo de nuevo en un momento.',
        );
      },
    });
  }
}
