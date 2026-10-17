import { Component, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, of, switchMap } from 'rxjs';

import { MetaApi } from './core/api/meta-api';
import { AuthService } from './core/auth/auth';

interface PageHeading {
  heading: string;
  subheading: string;
  /** Full-screen page without navigation (login). */
  bare: boolean;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, MatMenuModule, MatTooltipModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly metaApi = inject(MetaApi);

  /** Only once signed in: before that the API answers 401, and a failed shared request would stay failed. */
  protected readonly meta = toSignal(
    toObservable(this.auth.signedIn).pipe(switchMap((signedIn) => (signedIn ? this.metaApi.meta$ : of(undefined)))),
  );

  private readonly route = inject(ActivatedRoute);

  /** Heading of the deepest active route, from its `data`. */
  protected readonly page = toSignal(
    inject(Router).events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => {
        let route = this.route.snapshot;
        while (route.firstChild) {
          route = route.firstChild;
        }
        return route.data as Partial<PageHeading>;
      }),
    ),
    { initialValue: {} as Partial<PageHeading> },
  );

  protected initials(name: string): string {
    return name.split(/\s+/).map((part) => part[0]).slice(0, 2).join('').toUpperCase();
  }
}
