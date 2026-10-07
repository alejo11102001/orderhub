import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { OrderService } from '../../../core/order.service';
import { ToastService } from '../../../core/toast.service';
import { Order, OrderStatus } from '../../../core/models';
import { Pager } from '../../../shared/pager/pager';

export type OrderAction = 'pay' | 'cancel';

export const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: 'Pendiente',
  PAID: 'Pagado',
  CANCELLED: 'Cancelado',
};

export const STATUS_BADGE: Record<OrderStatus, string> = {
  PENDING: 'badge-warning',
  PAID: 'badge-success',
  CANCELLED: 'badge-danger',
};

@Component({
  selector: 'app-order-list',
  imports: [CurrencyPipe, DatePipe, Pager],
  templateUrl: './order-list.html',
  styleUrl: './order-list.css',
})
export class OrderList {
  private service = inject(OrderService);
  private toasts = inject(ToastService);

  protected statusLabel = STATUS_LABEL;
  protected statusBadge = STATUS_BADGE;

  orders = signal<Order[]>([]);
  page = signal(0);
  totalPages = signal(0);
  loading = signal(true);
  loadError = signal(false);
  /** Pedido y acción pendientes de confirmar (confirmación en línea). */
  confirming = signal<{ id: number; action: OrderAction } | null>(null);
  busyId = signal<number | null>(null);

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.service.list(page).subscribe({
      next: (res) => {
        this.orders.set(res.content);
        this.page.set(res.number);
        this.totalPages.set(res.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  ask(id: number, action: OrderAction): void {
    this.confirming.set({ id, action });
  }

  dismiss(): void {
    this.confirming.set(null);
  }

  isConfirming(id: number, action: OrderAction): boolean {
    const c = this.confirming();
    return c?.id === id && c.action === action;
  }

  confirm(): void {
    const pending = this.confirming();
    if (!pending) {
      return;
    }
    const { id, action } = pending;
    this.confirming.set(null);
    this.busyId.set(id);
    const request = action === 'pay' ? this.service.pay(id) : this.service.cancel(id);
    request.subscribe({
      next: () => {
        this.busyId.set(null);
        this.toasts.success(action === 'pay' ? `Pedido #${id} pagado` : `Pedido #${id} cancelado`);
        this.load(this.page());
      },
      error: () => {
        this.busyId.set(null);
        this.toasts.error('La operación no está permitida para este pedido');
        this.load(this.page());
      },
    });
  }
}
