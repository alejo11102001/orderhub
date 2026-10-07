export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export type Role = 'ADMIN' | 'CUSTOMER';

export interface ProductRequest {
  name: string;
  description: string | null;
  price: number;
  stock: number;
}

export interface Product {
  id: number;
  name: string;
  description: string | null;
  price: number;
  stock: number;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface OrderItem {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
}

export type OrderStatus = 'PENDING' | 'PAID' | 'CANCELLED';

export interface Order {
  id: number;
  status: OrderStatus;
  total: number;
  createdAt: string;
  items: OrderItem[];
}

export interface CartLine {
  product: Product;
  quantity: number;
}