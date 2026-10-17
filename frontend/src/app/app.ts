import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { MetaApi } from './core/api/meta-api';

interface PageHeading {
  heading: string;
  subheading: string;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, MatTooltipModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly meta = toSignal(inject(MetaApi).meta$);

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
}
