import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { CartService } from './cart.service';
import { Product } from './models';

const product = (id: number, price: number): Product => ({
  id, name: `P${id}`, description: null, price, stock: 10, createdAt: '',
});

describe('CartService', () => {
  let cart: CartService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    cart = TestBed.inject(CartService);
  });

  it('agrega un producto nuevo con cantidad 1', () => {
    cart.add(product(1, 100));
    expect(cart.lines().length).toBe(1);
    expect(cart.count()).toBe(1);
  });

  it('incrementa la cantidad si el producto ya existe', () => {
    cart.add(product(1, 100));
    cart.add(product(1, 100));
    expect(cart.lines().length).toBe(1);
    expect(cart.count()).toBe(2);
  });

  it('calcula el total', () => {
    cart.add(product(1, 100));
    cart.add(product(1, 100));
    cart.add(product(2, 50));
    expect(cart.total()).toBe(250);
  });

  it('quita un producto y vacía el carrito', () => {
    cart.add(product(1, 100));
    cart.add(product(2, 50));
    cart.remove(1);
    expect(cart.lines().map((l) => l.product.id)).toEqual([2]);
    cart.clear();
    expect(cart.count()).toBe(0);
  });

  it('no supera el stock al agregar', () => {
    const p = { ...product(1, 100), stock: 2 };
    cart.add(p);
    cart.add(p);
    cart.add(p);
    expect(cart.quantityOf(1)).toBe(2);
  });

  it('setQuantity fija la cantidad dentro de 1..stock', () => {
    const p = { ...product(1, 100), stock: 5 };
    cart.add(p);
    cart.setQuantity(1, 4);
    expect(cart.quantityOf(1)).toBe(4);
    cart.setQuantity(1, 99);
    expect(cart.quantityOf(1)).toBe(5);
    cart.setQuantity(1, 0);
    expect(cart.quantityOf(1)).toBe(1);
    cart.setQuantity(1, Number.NaN);
    expect(cart.quantityOf(1)).toBe(1);
  });

  it('setQuantity recalcula el total y no afecta otras líneas', () => {
    cart.add(product(1, 100));
    cart.add(product(2, 50));
    cart.setQuantity(1, 3);
    expect(cart.total()).toBe(350);
    expect(cart.quantityOf(2)).toBe(1);
  });
});
