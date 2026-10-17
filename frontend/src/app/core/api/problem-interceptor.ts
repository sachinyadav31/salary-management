import { HttpContext, HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { ProblemDetail } from './models';

/**
 * Set on requests whose errors the caller presents itself (e.g. a form showing field errors or a
 * conflict banner), so the user isn't told twice.
 */
export const HANDLES_ERRORS = new HttpContextToken<boolean>(() => false);

export function handlesErrors(): HttpContext {
  return new HttpContext().set(HANDLES_ERRORS, true);
}

/** Turns RFC 7807 problem details (and network failures) into one readable message. */
export function problemMessage(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'Cannot reach the server. Check your connection and try again.';
  }
  const problem = error.error as ProblemDetail | null;
  if (problem?.errors && Object.keys(problem.errors).length > 0) {
    return Object.entries(problem.errors).map(([field, message]) => `${field}: ${message}`).join(' · ');
  }
  return problem?.detail ?? problem?.title ?? `Request failed (${error.status})`;
}

/**
 * Shows every API error in a snackbar, then rethrows so callers can still react
 * (e.g. a form highlighting fields, or a 409 prompting a reload).
 */
export const problemInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && !req.context.get(HANDLES_ERRORS)) {
        snackBar.open(problemMessage(error), 'Dismiss', { duration: 6000, panelClass: 'pa-snack-error' });
      }
      return throwError(() => error);
    }),
  );
};
