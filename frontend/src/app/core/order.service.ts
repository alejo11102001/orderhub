import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { CartLine, Order, Page } from './models';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private http = inject(HttpClient);
  private url = `${environment.apiUrl}/orders`;

  create(lines: CartLine[]) {
    const items = lines.map((l) => ({ productId: l.product.id, quantity: l.quantity }));
    return this.http.post<Order>(this.url, { items });
  }

  list(page = 0, size = 10) {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Order>>(this.url, { params });
  }

  pay(id: number) {
    return this.http.post<Order>(`${this.url}/${id}/pay`, {});
  }

  cancel(id: number) {
    return this.http.post<Order>(`${this.url}/${id}/cancel`, {});
  }
}