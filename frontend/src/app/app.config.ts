import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { problemInterceptor } from './core/api/problem-interceptor';
import { AuthService } from './core/auth/auth';
import { sessionInterceptor } from './core/auth/session-interceptor';
import { firstValueFrom } from 'rxjs';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    // Spring Security's csrf.spa() uses these names; Angular echoes the cookie on POST/PUT/PATCH/DELETE.
    provideHttpClient(
      withInterceptors([sessionInterceptor, problemInterceptor]),
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
    ),
    // Know who is signed in (and get the CSRF cookie) before the first route is evaluated.
    provideAppInitializer(() => firstValueFrom(inject(AuthService).loadSession())),
    // Self-hosted Material Symbols (Rounded) instead of Google's icon CDN.
    provideAppInitializer(() => {
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-rounded');
    }),
  ],
};
