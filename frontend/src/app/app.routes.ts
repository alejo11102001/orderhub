import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { ProductList } from './features/catalog/product-list/product-list';
import { Cart } from './features/orders/cart/cart';
import { OrderList } from './features/orders/order-list/order-list';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'products', component: ProductList },
  { path: 'cart', component: Cart, canActivate: [authGuard] },
  { path: 'orders', component: OrderList, canActivate: [authGuard] },
  { path: '', redirectTo: 'products', pathMatch: 'full' },
];