import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { OrderList } from './order-list';
import { Order } from '../../../core/models';
import { ToastService } from '../../../core/toast.service';

const order = (id: number, status: Order['status']): Order => ({
  id,
  status,
  total: 300,
  createdAt: '2026-01-02T10:00:00',
  items: [{ productId: 1, productName: 'Café', quantity: 3, unitPrice: 100 }],
});

const page = (content: Order[]) => ({
  content, totalElements: content.length, totalPages: 1, number: 0, size: 10,
});

describe('OrderList', () => {
  let http: HttpTestingController;

  const render = (orders: Order[]) => {
    const fixture = TestBed.createComponent(OrderList);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/orders') && r.method === 'GET').flush(page(orders));
    fixture.detectChanges();
    return fixture;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('muestra estado coloreado y el nombre de producto de cada línea', () => {
    const fixture = render([order(1, 'PENDING'), order(2, 'PAID'), order(3, 'CANCELLED')]);
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('.badge-warning')?.textContent).toContain('Pendiente');
    expect(root.querySelector('.badge-success')?.textContent).toContain('Pagado');
    expect(root.querySelector('.badge-danger')?.textContent).toContain('Cancelado');
    expect(root.textContent).toContain('Café');
    // solo el pedido PENDING ofrece acciones
    expect(root.querySelectorAll('.actions').length).toBe(1);
  });

  it('pide confirmación antes de cancelar y no llama al backend hasta confirmar', () => {
    const fixture = render([order(1, 'PENDING')]);
    const root = fixture.nativeElement as HTMLElement;

    fixture.componentInstance.ask(1, 'cancel');
    fixture.detectChanges();
    expect(root.textContent).toContain('¿Cancelar este pedido?');
    http.expectNone((r) => r.url.endsWith('/orders/1/cancel'));

    fixture.componentInstance.confirm();
    http.expectOne((r) => r.url.endsWith('/orders/1/cancel')).flush(order(1, 'CANCELLED'));
    http.expectOne((r) => r.url.endsWith('/orders') && r.method === 'GET').flush(page([order(1, 'CANCELLED')]));
    expect(TestBed.inject(ToastService).toasts()[0].message).toContain('cancelado');
  });

  it('descartar la confirmación no hace ninguna petición', () => {
    const fixture = render([order(1, 'PENDING')]);
    fixture.componentInstance.ask(1, 'pay');
    fixture.componentInstance.dismiss();
    fixture.componentInstance.confirm();
    http.expectNone((r) => r.url.endsWith('/orders/1/pay'));
    expect(fixture.componentInstance.confirming()).toBeNull();
  });

  it('muestra el estado vacío sin pedidos', () => {
    const fixture = render([]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aún no tienes pedidos');
  });
});
