import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { ProductService } from '../../../core/product.service';
import { Page, Product } from '../../../core/models';

@Component({
  selector: 'app-product-list',
  imports: [CurrencyPipe],
  templateUrl: './product-list.html',
  styleUrl: './product-list.css',
})
export class ProductList {
  private service = inject(ProductService);

  products = signal<Product[]>([]);
  page = signal(0);
  totalPages = signal(0);

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    this.service.list(page).subscribe((res: Page<Product>) => {
      this.products.set(res.content);
      this.page.set(res.number);
      this.totalPages.set(res.totalPages);
    });
  }
}