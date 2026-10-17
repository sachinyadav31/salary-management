import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService, isUnauthorized } from './auth';

/**
 * A 401 on any API call means the session ended (idle timeout, server restart on the free tier): send the
 * user to sign in, then back to the page they were on. Login/session calls handle their own 401s.
 */
export const sessionInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (isUnauthorized(error) && !req.url.startsWith('/api/auth/')) {
        auth.sessionExpired(router.url);
      }
      return throwError(() => error);
    }),
  );
};
