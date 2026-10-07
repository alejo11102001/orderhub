import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { Cart } from './cart';
import { CartService } from '../../../core/cart.service';
import { Product } from '../../../core/models';

const product = (id: number, price: number, stock = 10): Product => ({
  id, name: `P${id}`, description: null, price, stock, createdAt: '',
});

describe('Cart', () => {
  let http: HttpTestingController;
  let cart: CartService;

  const render = () => {
    const fixture = TestBed.createComponent(Cart);
    fixture.detectChanges();
    return fixture;
  };
  const button = (root: HTMLElement, label: string) =>
    root.querySelector(`button[aria-label="${label}"]`) as HTMLButtonElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    cart = TestBed.inject(CartService);
  });

  it('muestra el estado vacío', () => {
    const fixture = render();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Tu carrito está vacío');
  });

  it('los botones +/− cambian la cantidad y respetan los límites', () => {
    cart.add(product(1, 100, 2));
    const fixture = render();
    const root = fixture.nativeElement as HTMLElement;

    expect(button(root, 'Disminuir cantidad').disabled).toBe(true);
    button(root, 'Aumentar cantidad').click();
    fixture.detectChanges();

    expect(cart.quantityOf(1)).toBe(2);
    expect(button(root, 'Aumentar cantidad').disabled).toBe(true);

    button(root, 'Disminuir cantidad').click();
    fixture.detectChanges();
    expect(cart.quantityOf(1)).toBe(1);
  });

  it('quitar elimina la línea', () => {
    cart.add(product(1, 100));
    const fixture = render();
    button(fixture.nativeElement, 'Quitar P1 del carrito').click();
    fixture.detectChanges();
    expect(cart.count()).toBe(0);
  });

  it('muestra un mensaje amigable ante un 409 y reactiva el botón', () => {
    cart.add(product(1, 100));
    const fixture = render();
    const root = fixture.nativeElement as HTMLElement;

    fixture.componentInstance.checkout();
    fixture.detectChanges();
    expect(fixture.componentInstance.loading()).toBe(true);

    http.expectOne((r) => r.url.endsWith('/orders'))
      .flush({ detail: 'Insufficient stock' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('stock suficiente');
    expect(fixture.componentInstance.loading()).toBe(false);
    expect(cart.count()).toBe(1);
  });
});
