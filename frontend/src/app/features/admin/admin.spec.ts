import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { Admin } from './admin';
import { Product } from '../../core/models';

const product = (id: number): Product => ({
  id, name: `P${id}`, description: null, price: 1000, stock: 4, createdAt: '',
});

describe('Admin', () => {
  let http: HttpTestingController;

  const render = (products: Product[] = [product(1)]) => {
    const fixture = TestBed.createComponent(Admin);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/products') && r.method === 'GET').flush({
      content: products, totalElements: products.length, totalPages: 1, number: 0, size: 12,
    });
    fixture.detectChanges();
    return fixture;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('no envía un producto inválido y muestra los errores', () => {
    const fixture = render();
    fixture.componentInstance.form.setValue({ name: '', description: '', price: -1, stock: 1.5 });
    fixture.componentInstance.save();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.field-error').length).toBe(3);
    http.expectNone((r) => r.method !== 'GET');
  });

  it('crea un producto con el cuerpo esperado por el backend', () => {
    const fixture = render();
    fixture.componentInstance.form.setValue({ name: '  Café  ', description: '', price: 12500, stock: 8 });
    fixture.componentInstance.save();

    const req = http.expectOne((r) => r.url.endsWith('/products') && r.method === 'POST');
    expect(req.request.body).toEqual({ name: 'Café', description: null, price: 12500, stock: 8 });
    req.flush({ ...product(2), name: 'Café' });
    http.expectOne((r) => r.url.endsWith('/products') && r.method === 'GET').flush({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 12,
    });
  });

  it('edita con PUT sobre el id del producto', () => {
    const fixture = render([product(7)]);
    fixture.componentInstance.edit(product(7));
    fixture.componentInstance.form.patchValue({ price: 2000 });
    fixture.componentInstance.save();

    const req = http.expectOne((r) => r.url.endsWith('/products/7') && r.method === 'PUT');
    expect(req.request.body.price).toBe(2000);
    req.flush(product(7));
    http.expectOne((r) => r.method === 'GET').flush({
      content: [product(7)], totalElements: 1, totalPages: 1, number: 0, size: 12,
    });
  });

  it('elimina solo tras confirmar', () => {
    const fixture = render([product(3)]);
    fixture.componentInstance.askDelete(3);
    http.expectNone((r) => r.method === 'DELETE');

    fixture.componentInstance.confirmDelete(product(3));
    http.expectOne((r) => r.url.endsWith('/products/3') && r.method === 'DELETE').flush(null);
    http.expectOne((r) => r.method === 'GET').flush({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 12,
    });
  });
});
