import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { ProductList, stockLevel } from './product-list';
import { Product } from '../../../core/models';
import { CartService } from '../../../core/cart.service';

const product = (id: number, stock: number): Product => ({
  id, name: `Producto ${id}`, description: 'desc', price: 15000, stock, createdAt: '',
});

describe('stockLevel', () => {
  it('clasifica agotado, bajo y normal', () => {
    expect(stockLevel(0)).toBe('out');
    expect(stockLevel(1)).toBe('low');
    expect(stockLevel(5)).toBe('low');
    expect(stockLevel(6)).toBe('ok');
  });
});

describe('ProductList', () => {
  let http: HttpTestingController;

  const render = (products: Product[]) => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.skeleton').length).toBeGreaterThan(0);
    http.expectOne((r) => r.url.endsWith('/products')).flush({
      content: products, totalElements: products.length, totalPages: 1, number: 0, size: 12,
    });
    fixture.detectChanges();
    return fixture;
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('muestra skeleton y luego tarjetas con indicador de stock', () => {
    const fixture = render([product(1, 20), product(2, 3), product(3, 0)]);
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelectorAll('.product').length).toBe(3);
    expect(root.querySelector('.badge-warning')?.textContent).toContain('Quedan 3');
    expect(root.querySelector('.badge-danger')?.textContent).toContain('Agotado');
    const buttons = Array.from(root.querySelectorAll<HTMLButtonElement>('.product button'));
    expect(buttons.map((b) => b.disabled)).toEqual([false, false, true]);
  });

  it('muestra el estado vacío sin productos', () => {
    const fixture = render([]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aún no hay productos');
  });

  it('muestra error con opción de reintentar', () => {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/products')).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).toBeTruthy();
  });

  it('sin sesión, "Agregar" redirige al login y no toca el carrito', () => {
    const fixture = render([product(1, 20)]);
    const navigate = TestBed.inject(Router).navigate;
    let target: unknown[] = [];
    TestBed.inject(Router).navigate = ((commands: unknown[]) => {
      target = commands;
      return Promise.resolve(true);
    }) as typeof navigate;

    fixture.componentInstance.add(product(1, 20));

    expect(target).toEqual(['/login']);
    expect(TestBed.inject(CartService).count()).toBe(0);
  });

  it('deshabilita agregar cuando todo el stock ya está en el carrito', () => {
    const p = product(1, 1);
    TestBed.inject(CartService).add(p);
    const fixture = render([p]);
    expect(fixture.componentInstance.unavailable(p)).toBe(true);
  });
});
