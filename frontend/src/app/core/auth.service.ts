import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthResponse, UserSummary } from './models';

const TOKEN_KEY = 'funbuild.token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly currentUserSignal = signal<UserSummary | null>(null);
  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);
  readonly isAdmin = computed(() => this.currentUserSignal()?.role === 'ADMIN');

  getToken(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  }

  private setToken(token: string): void {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      // Storage unavailable (private browsing, etc.) — the session just won't survive a reload.
    }
  }

  private clearToken(): void {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      // Nothing to clean up if storage isn't available.
    }
  }

  /** Called once at app startup to restore the session from a stored token, if any. */
  restoreSession(): Observable<UserSummary | null> {
    return new Observable((subscriber) => {
      if (!this.getToken()) {
        subscriber.next(null);
        subscriber.complete();
        return;
      }
      this.fetchMe().subscribe({
        next: (user) => {
          subscriber.next(user);
          subscriber.complete();
        },
        error: () => {
          this.clearToken();
          this.currentUserSignal.set(null);
          subscriber.next(null);
          subscriber.complete();
        },
      });
    });
  }

  /** Patches the cached profile after a self-service update (e.g. changing the display name). */
  setCurrentUser(user: UserSummary): void {
    this.currentUserSignal.set(user);
  }

  fetchMe(): Observable<UserSummary> {
    return this.http.get<UserSummary>(`${environment.apiUrl}/api/users/me`).pipe(
      tap((user) => this.currentUserSignal.set(user)),
    );
  }

  register(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/api/auth/register`, { email, password })
      .pipe(tap((res) => this.applyAuthResponse(res)));
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/api/auth/login`, { email, password })
      .pipe(tap((res) => this.applyAuthResponse(res)));
  }

  /** Used by the OAuth2 redirect callback, which only carries a token in the URL fragment. */
  applyToken(token: string): Observable<UserSummary> {
    this.setToken(token);
    return this.fetchMe();
  }

  logout(): void {
    this.clearToken();
    this.currentUserSignal.set(null);
  }

  oauthUrl(provider: 'github' | 'google'): string {
    return `${environment.apiUrl}/oauth2/authorization/${provider}`;
  }

  private applyAuthResponse(res: AuthResponse): void {
    this.setToken(res.token);
    this.currentUserSignal.set(res.user);
  }
}
