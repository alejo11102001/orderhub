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
});