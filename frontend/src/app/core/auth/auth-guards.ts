import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth';

/** App pages need a session; otherwise go to login and come back afterwards. */
export const signedInGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.signedIn() || inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** The login page is only for signed-out visitors. */
export const signedOutGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return !auth.signedIn() || inject(Router).createUrlTree(['/insights']);
};
