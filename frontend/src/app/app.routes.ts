import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { Register } from './features/auth/register/register';
import { ProductList } from './features/catalog/product-list/product-list';
import { Cart } from './features/orders/cart/cart';
import { OrderList } from './features/orders/order-list/order-list';
import { Admin } from './features/admin/admin';
import { authGuard } from './core/auth.guard';
import { roleGuard } from './core/role.guard';

export const routes: Routes = [
  { path: 'login', component: Login, title: 'Iniciar sesión · OrderHub' },
  { path: 'register', component: Register, title: 'Crear cuenta · OrderHub' },
  { path: 'products', component: ProductList, title: 'Catálogo · OrderHub' },
  { path: 'cart', component: Cart, canActivate: [authGuard], title: 'Carrito · OrderHub' },
  { path: 'orders', component: OrderList, canActivate: [authGuard], title: 'Mis pedidos · OrderHub' },
  {
    path: 'admin',
    component: Admin,
    canActivate: [roleGuard('ADMIN')],
    title: 'Administración · OrderHub',
  },
  { path: '', redirectTo: 'products', pathMatch: 'full' },
  { path: '**', redirectTo: 'products' },
];
