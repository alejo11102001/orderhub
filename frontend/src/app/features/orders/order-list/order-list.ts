import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { OrderService } from '../../../core/order.service';
import { Order } from '../../../core/models';

@Component({
  selector: 'app-order-list',
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './order-list.html',
  styleUrl: './order-list.css',
})
export class OrderList {
  private service = inject(OrderService);

  orders = signal<Order[]>([]);
  page = signal(0);
  totalPages = signal(0);
  error = signal<string | null>(null);

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    this.service.list(page).subscribe((res) => {
      this.orders.set(res.content);
      this.page.set(res.number);
      this.totalPages.set(res.totalPages);
    });
  }

  pay(id: number): void {
    this.act(this.service.pay(id));
  }

  cancel(id: number): void {
    this.act(this.service.cancel(id));
  }

  private act(request: ReturnType<OrderService['pay']>): void {
    this.error.set(null);
    request.subscribe({
      next: () => this.load(this.page()),
      error: () => this.error.set('La operación no está permitida para este pedido'),
    });
  }
}