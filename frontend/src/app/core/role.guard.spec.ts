import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { roleGuard } from './role.guard';

/** JWT sin firma válida: el frontend solo lee los claims, el backend valida la firma. */
function tokenWith(claims: object): string {
  const b64 = (o: object) =>
    btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.sig`;
}

describe('roleGuard', () => {
  const run = () =>
    TestBed.runInInjectionContext(() =>
      roleGuard('ADMIN')({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
  });

  it('manda a /login sin sesión', () => {
    const result = run() as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login');
  });

  it('manda a /products si el rol no coincide', () => {
    sessionStorage.setItem('orderhub_token', tokenWith({ sub: 'a@a.com', role: 'CUSTOMER' }));
    const result = run() as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/products');
  });

  it('permite el paso al rol requerido', () => {
    sessionStorage.setItem('orderhub_token', tokenWith({ sub: 'root@a.com', role: 'ADMIN' }));
    expect(run()).toBe(true);
  });
});
