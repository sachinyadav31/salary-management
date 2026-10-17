import { DatePipe, DecimalPipe } from '@angular/common';
import { A11yModule } from '@angular/cdk/a11y';
import { Component, afterRenderEffect, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, forkJoin, map, merge, startWith, switchMap } from 'rxjs';

import { EmployeeApi } from '../../../core/api/employee-api';
import { EmployeeDetail, SalaryHistoryItem } from '../../../core/api/models';
import { ThemeService } from '../../../core/theme';
import { BandBar } from '../../../shared/band-bar';
import { Chart } from '../../../shared/charts/chart';
import { ChartPalette, resolvePalette } from '../../../shared/charts/palette';
import { CompaBadge } from '../../../shared/compa-badge';
import { countryFlag } from '../../../shared/flag';
import { LOADING, loadable } from '../../../shared/loadable';
import { MoneyPipe } from '../../../shared/money';
import { tenure } from './salary-preview';
import { reasonLabel, timelineOption } from './timeline-option';

export type DrawerMode = 'view' | 'salary' | 'edit';

interface Loaded {
  employee: EmployeeDetail;
  history: SalaryHistoryItem[];
}

/** Employee detail as a routed drawer over the directory (/employees/:id keeps the list's filters). */
@Component({
  selector: 'pa-employee-drawer',
  imports: [
    A11yModule, DatePipe, DecimalPipe, MatButtonModule, MatIconModule, MatTooltipModule,
    BandBar, Chart, CompaBadge, MoneyPipe,
  ],
  templateUrl: './employee-drawer.html',
  styleUrl: './employee-drawer.scss',
  host: { '(document:keydown.escape)': 'onEscape()' },
})
export class EmployeeDrawer {
  /** Route param, bound by the router (withComponentInputBinding). */
  readonly id = input.required<string>();

  private readonly api = inject(EmployeeApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly theme = inject(ThemeService);

  private readonly reload = new Subject<void>();
  /** Replaces the loaded data after a successful write, without a refetch. */
  private readonly replaced = new Subject<Loaded>();

  protected readonly state = toSignal(
    merge(
      loadable(
        merge(toObservable(this.id), this.reload.pipe(map(() => this.id()))).pipe(map(Number)),
        (id) => forkJoin({ employee: this.api.get(id), history: this.api.history(id) }),
      ),
      this.replaced.pipe(map((data) => ({ loading: false, error: false, data }))),
    ),
    { initialValue: LOADING },
  );

  protected readonly employee = computed(() => this.state().data?.employee ?? null);
  protected readonly history = computed(() => this.state().data?.history ?? []);
  protected readonly mode = signal<DrawerMode>('view');

  private readonly palette = signal<ChartPalette | null>(null);
  protected readonly timeline = computed(() => {
    const palette = this.palette();
    const history = this.history();
    return palette && history.length ? timelineOption(history, palette) : null;
  });

  protected readonly flag = countryFlag;
  protected readonly reason = reasonLabel;
  protected readonly tenure = computed(() => {
    const employee = this.employee();
    return employee ? tenure(employee.hireDate) : '';
  });

  constructor() {
    afterRenderEffect(() => {
      this.theme.mode();
      this.palette.set(resolvePalette());
    });
  }

  protected retry(): void {
    this.reload.next();
  }

  protected close(): void {
    this.router.navigate(['..'], { relativeTo: this.route, queryParamsHandling: 'preserve' });
  }

  protected onEscape(): void {
    if (this.mode() !== 'view') {
      this.mode.set('view');
    } else {
      this.close();
    }
  }

  protected initials(employee: EmployeeDetail): string {
    return (employee.firstName[0] + employee.lastName[0]).toUpperCase();
  }

  /** Called by write flows (14c) with the server's latest data. */
  protected accept(data: Loaded): void {
    this.replaced.next(data);
    this.api.notifyChanged(data.employee);
  }
}
