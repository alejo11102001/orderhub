import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  it('guarda el token tras un login correcto', () => {
    auth.login('a@a.com', 'x').subscribe();
    const req = http.expectOne((r) => r.url.endsWith('/auth/login'));
    expect(req.request.method).toBe('POST');
    req.flush({ accessToken: 'abc', tokenType: 'Bearer', expiresInSeconds: 60 });

    expect(auth.getToken()).toBe('abc');
    expect(auth.isLoggedIn()).toBe(true);
  });

  it('no guarda token si el login falla', () => {
    auth.login('a@a.com', 'mala').subscribe({ error: () => {} });
    http.expectOne((r) => r.url.endsWith('/auth/login'))
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(false);
  });

  it('logout borra el token', () => {
    auth.login('a@a.com', 'x').subscribe();
    http.expectOne((r) => r.url.endsWith('/auth/login'))
      .flush({ accessToken: 'abc', tokenType: 'Bearer', expiresInSeconds: 60 });

    auth.logout();

    expect(auth.getToken()).toBeNull();
    expect(sessionStorage.getItem('orderhub_token')).toBeNull();
  });
});