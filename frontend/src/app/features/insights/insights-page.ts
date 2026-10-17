import { DecimalPipe } from '@angular/common';
import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, combineLatest, map, startWith } from 'rxjs';

import { InsightsApi } from '../../core/api/insights-api';
import { MetaApi } from '../../core/api/meta-api';
import { CountryInsight, Money, OutlierCard, OutlierType } from '../../core/api/models';
import { Chart, ChartClick } from '../../shared/charts/chart';
import { ChartPalette, resolvePalette } from '../../shared/charts/palette';
import { WorldMapLoader } from '../../shared/charts/world-map';
import { CompaBadge } from '../../shared/compa-badge';
import { CountUp } from '../../shared/count-up';
import { countryFlag } from '../../shared/flag';
import { LOADING, loadable } from '../../shared/loadable';
import { MoneyPipe, formatMoney } from '../../shared/money';
import { bandHealth, bandSplitPercents, distributionOption, mapOption } from './chart-options';

const OUTLIER_LIMIT = 6;

const OUTLIER_STYLE: Record<OutlierType, { icon: string; tone: string; label: string }> = {
  UNDERPAID_GROUP: { icon: 'trending_down', tone: 'below', label: 'Underpaid group' },
  OVERPAID_GROUP: { icon: 'trending_up', tone: 'above', label: 'Overpaid group' },
  WIDE_SPREAD: { icon: 'unfold_more', tone: 'watch', label: 'Inconsistent pay' },
  DEPARTMENT_LAG: { icon: 'domain', tone: 'below', label: 'Department gap' },
};

const usd = (minorUnits: number): Money => ({ minorUnits, currency: 'USD', amount: (minorUnits / 100).toFixed(2) });

@Component({
  selector: 'pa-insights-page',
  imports: [
    DecimalPipe, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatSelectModule, MatTooltipModule,
    Chart, CompaBadge, CountUp, MoneyPipe,
  ],
  templateUrl: './insights-page.html',
  styleUrl: './insights-page.scss',
})
export class InsightsPage {
  private readonly api = inject(InsightsApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly worldMap = inject(WorldMapLoader);

  protected readonly meta = toSignal(inject(MetaApi).meta$);

  /** Selection lives in the URL (?country=IN&department=1), like the directory's filters. */
  private readonly params = toSignal(this.route.queryParamMap, { requireSync: true });
  protected readonly selectedCountry = computed(() => {
    const code = this.params().get('country');
    return code && /^[A-Z]{2}$/.test(code) ? code : null;
  });
  protected readonly selectedDepartment = computed(() => {
    const id = Number(this.params().get('department'));
    return Number.isInteger(id) && id > 0 ? id : null;
  });

  private readonly retry = new Subject<void>();
  private readonly retry$ = this.retry.pipe(startWith(undefined));

  protected readonly summary = toSignal(loadable(this.retry$, () => this.api.summary()), { initialValue: LOADING });
  protected readonly countries = toSignal(loadable(this.retry$, () => this.api.byCountry()), { initialValue: LOADING });
  protected readonly outliers = toSignal(loadable(this.retry$, () => this.api.outliers(OUTLIER_LIMIT)), { initialValue: LOADING });
  protected readonly distribution = toSignal(
    loadable(
      combineLatest([toObservable(this.selectedCountry), toObservable(this.selectedDepartment), this.retry$]),
      ([country, department]) => this.api.distribution(country, department),
    ),
    { initialValue: LOADING },
  );
  protected readonly mapReady = toSignal(
    loadable(this.retry$, () => this.worldMap.ready$),
    { initialValue: LOADING },
  );

  /** Canvas needs concrete colours; resolved from the CSS tokens once the page has rendered. */
  private readonly palette = signal<ChartPalette | null>(null);

  protected readonly selected = computed(() =>
    this.countries().data?.find((c) => c.countryCode === this.selectedCountry()) ?? null);

  protected readonly mapChart = computed(() => {
    const countries = this.countries().data;
    const palette = this.palette();
    return countries && palette && this.mapReady().data ? mapOption(countries, this.selectedCountry(), palette) : null;
  });

  protected readonly distributionChart = computed(() => {
    const distribution = this.distribution().data;
    const palette = this.palette();
    return distribution && palette ? distributionOption(distribution, palette) : null;
  });

  protected readonly payrollTotal = computed(() =>
    (this.countries().data ?? []).reduce((sum, c) => sum + c.payrollUsd.minorUnits, 0));
  protected readonly maxSeverity = computed(() => Math.max(1, ...(this.outliers().data ?? []).map((o) => o.severity)));
  protected readonly orgSplit = computed(() => {
    const bands = this.summary().data?.bands;
    return bands ? bandSplitPercents(bands) : null;
  });

  protected readonly flag = countryFlag;
  protected readonly split = bandSplitPercents;
  protected readonly health = bandHealth;
  protected readonly outlierStyle = OUTLIER_STYLE;
  protected readonly formatCount = (value: number) => Math.round(value).toLocaleString('en-US');
  protected readonly formatUsdCompact = (value: number) => formatMoney(usd(Math.round(value)), 'compact');
  protected readonly formatUsd = (value: number) => formatMoney(usd(Math.round(value)));
  protected readonly formatPercent = (value: number) => `${value.toFixed(1)}%`;

  constructor() {
    afterNextRender(() => this.palette.set(resolvePalette()));
  }

  protected reload(): void {
    this.retry.next();
  }

  /** Map bubble or table row: select a country; selecting it again clears the selection. */
  protected selectCountry(code: string | null): void {
    const next = code === this.selectedCountry() ? null : code;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { country: next, department: this.selectedDepartment() },
      queryParamsHandling: 'merge',
    });
  }

  protected selectDepartment(id: number | null): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { department: id }, queryParamsHandling: 'merge' });
  }

  protected onMapClick(event: ChartClick): void {
    this.selectCountry(event.name);
  }

  protected payrollShare(country: CountryInsight): number {
    return this.payrollTotal() ? (country.payrollUsd.minorUnits * 100) / this.payrollTotal() : 0;
  }

  protected severityWidth(card: OutlierCard): number {
    return Math.max(6, (card.severity * 100) / this.maxSeverity());
  }

  protected directoryParams(): Record<string, string | number> {
    const params: Record<string, string | number> = {};
    if (this.selectedCountry()) params['country'] = this.selectedCountry()!;
    if (this.selectedDepartment()) params['department'] = this.selectedDepartment()!;
    return params;
  }
}
