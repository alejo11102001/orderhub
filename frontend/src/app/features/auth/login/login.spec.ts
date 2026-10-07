import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { Login } from './login';

describe('Login', () => {
  let http: HttpTestingController;

  const render = () => {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    return fixture;
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('valida en línea al enviar vacío y no llama al backend', () => {
    const fixture = render();
    const root = fixture.nativeElement as HTMLElement;

    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(root.querySelectorAll('.field-error').length).toBe(2);
    expect(root.querySelector('#email')?.getAttribute('aria-invalid')).toBe('true');
    http.expectNone(() => true);
  });

  it('muestra un error accesible con credenciales incorrectas', () => {
    const fixture = render();
    const root = fixture.nativeElement as HTMLElement;
    fixture.componentInstance.form.setValue({ email: 'a@a.com', password: 'mala' });

    fixture.componentInstance.submit();
    fixture.detectChanges();
    expect(root.querySelector('button[type="submit"]')?.classList).toContain('loading');

    http.expectOne((r) => r.url.endsWith('/auth/login'))
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('incorrectos');
    expect(fixture.componentInstance.loading()).toBe(false);
  });
});
