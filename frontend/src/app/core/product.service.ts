import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { Page, Product, ProductRequest } from './models';

@Injectable({ providedIn: 'root' })
export class ProductService {
  private http = inject(HttpClient);

  private url = `${environment.apiUrl}/products`;

  list(page = 0, size = 12) {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'id,asc');
    return this.http.get<Page<Product>>(this.url, { params });
  }

  create(request: ProductRequest) {
    return this.http.post<Product>(this.url, request);
  }

  update(id: number, request: ProductRequest) {
    return this.http.put<Product>(`${this.url}/${id}`, request);
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}