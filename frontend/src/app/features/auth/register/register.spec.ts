import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { Register } from './register';

describe('Register', () => {
  let http: HttpTestingController;

  const render = () => {
    const fixture = TestBed.createComponent(Register);
    fixture.detectChanges();
    return fixture;
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: 'products', children: [] }])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('rechaza contraseñas cortas y que no coinciden sin llamar al backend', () => {
    const fixture = render();
    fixture.componentInstance.form.setValue({ email: 'a@a.com', password: 'corta', confirm: 'otra' });
    fixture.componentInstance.submit();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('entre 8 y 72');
    expect(text).toContain('no coinciden');
    http.expectNone(() => true);
  });

  it('registra y luego inicia sesión automáticamente', () => {
    const fixture = render();
    fixture.componentInstance.form.setValue({
      email: 'nuevo@a.com', password: 'Password123!', confirm: 'Password123!',
    });
    fixture.componentInstance.submit();

    http.expectOne((r) => r.url.endsWith('/auth/register')).flush(null, { status: 201, statusText: 'Created' });
    http.expectOne((r) => r.url.endsWith('/auth/login'))
      .flush({ accessToken: 'abc', tokenType: 'Bearer', expiresInSeconds: 60 });

    expect(sessionStorage.getItem('orderhub_token')).toBe('abc');
  });

  it('muestra un error accesible si el correo ya existe', () => {
    const fixture = render();
    fixture.componentInstance.form.setValue({
      email: 'ya@a.com', password: 'Password123!', confirm: 'Password123!',
    });
    fixture.componentInstance.submit();
    http.expectOne((r) => r.url.endsWith('/auth/register'))
      .flush({}, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent)
      .toContain('ya está registrado');
  });
});
