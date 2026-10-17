import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, tap } from 'rxjs';
import { handlesErrors } from '../api/problem-interceptor';

export interface SignedInUser {
  email: string;
  fullName: string;
}

interface SessionView {
  authenticated: boolean;
  user: SignedInUser | null;
}

/**
 * Who is signed in. The session itself is an HttpOnly cookie the browser manages (JavaScript never sees it);
 * this service only mirrors "who" for the UI. The CSRF token travels as the XSRF-TOKEN cookie, which Angular's
 * HttpClient echoes in the X-XSRF-TOKEN header on every write.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly user = signal<SignedInUser | null>(null);
  readonly signedIn = computed(() => this.user() !== null);

  /**
   * Called once before the app renders. Besides telling us who is signed in, the response issues the CSRF
   * cookie, so it exists before the first POST (the login itself).
   */
  loadSession(): Observable<void> {
    return this.http.get<SessionView>('/api/auth/session').pipe(
      tap((session) => this.user.set(session.authenticated ? session.user : null)),
      map(() => undefined),
      catchError(() => {
        this.user.set(null);
        return of(undefined);
      }),
    );
  }

  /** Errors (401 invalid credentials, 429 locked) are shown by the login form, not the global snackbar. */
  login(email: string, password: string): Observable<SignedInUser> {
    return this.http
      .post<SignedInUser>('/api/auth/login', { email, password }, { context: handlesErrors() })
      .pipe(tap((user) => this.user.set(user)));
  }

  logout(): void {
    this.http
      .post('/api/auth/logout', {}, { context: handlesErrors() })
      .pipe(
        catchError(() => of(null)), // signed out locally either way
        finalize(() => {
          this.user.set(null);
          this.router.navigate(['/login']);
        }),
      )
      .subscribe();
  }

  /** The server says the session is gone (expired, restarted): forget the user and ask them to sign in again. */
  sessionExpired(returnUrl: string): void {
    if (!this.user()) return;
    this.user.set(null);
    this.router.navigate(['/login'], { queryParams: { returnUrl, expired: 1 } });
  }
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}
