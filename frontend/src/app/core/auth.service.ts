import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { Role, TokenResponse } from './models';

const TOKEN_KEY = 'orderhub_token';

interface Claims {
  sub?: string;
  role?: string;
}

/** Lee los claims sin verificar la firma: solo para UX; el backend valida el token. */
function decodeClaims(token: string | null): Claims {
  if (!token) {
    return {};
  }
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(payload), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as Claims;
  } catch {
    return {};
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  private token = signal<string | null>(sessionStorage.getItem(TOKEN_KEY));

  readonly isLoggedIn = computed(() => this.token() !== null);
  private claims = computed(() => decodeClaims(this.token()));
  readonly email = computed(() => this.claims().sub ?? null);
  readonly role = computed<Role | null>(() => {
    const role = this.claims().role;
    return role === 'ADMIN' || role === 'CUSTOMER' ? role : null;
  });
  readonly isAdmin = computed(() => this.role() === 'ADMIN');

  getToken(): string | null {
    return this.token();
  }

  login(email: string, password: string) {
    return this.http
      .post<TokenResponse>(`${environment.apiUrl}/auth/login`, { email, password })
      .pipe(tap((res) => this.saveToken(res.accessToken)));
  }

  register(email: string, password: string) {
    return this.http.post<void>(`${environment.apiUrl}/auth/register`, { email, password });
  }

  logout(): void {
    sessionStorage.removeItem(TOKEN_KEY);
    this.token.set(null);
  }

  private saveToken(token: string): void {
    sessionStorage.setItem(TOKEN_KEY, token);
    this.token.set(token);
  }
}