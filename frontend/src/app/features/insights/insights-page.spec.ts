import { Component, input, output } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { CountryInsight, Distribution, InsightsSummary, Money, OutlierCard } from '../../core/api/models';
import { Chart, ChartClick } from '../../shared/charts/chart';
import { InsightsPage } from './insights-page';

/** jsdom has no canvas; chart logic is covered by the option-builder tests. */
@Component({ selector: 'pa-chart', template: '' })
class ChartStub {
  readonly option = input<unknown>();
  readonly ariaLabel = input<string>();
  readonly chartClick = output<ChartClick>();
}

const usd = (minorUnits: number): Money => ({ minorUnits, currency: 'USD', amount: (minorUnits / 100).toFixed(2) });
const inr = (minorUnits: number): Money => ({ minorUnits, currency: 'INR', amount: (minorUnits / 100).toFixed(2) });
const stats = (m: (n: number) => Money, median: number) => ({ min: m(median / 2), max: m(median * 2), average: m(median), median: m(median) });

const SUMMARY: InsightsSummary = {
  asOf: '2026-10-17T08:00:00Z', fxRatesAsOf: '2026-10-01', reportingCurrency: 'USD', headcount: 9708, countries: 8,
  totalPayroll: usd(67968736600), averageSalary: usd(7001312), medianSalary: usd(6089100),
  bands: { below: 507, within: 8432, above: 769, withinPercent: 86.9, averageCompaRatio: 1.0 },
  variablePayTarget: usd(7700000000), totalTargetCash: usd(75668736600), bonusesLast12Months: usd(5120000000),
};

const INDIA: CountryInsight = {
  countryCode: 'IN', countryName: 'India', currency: 'INR', headcount: 2919, payroll: inr(414292000000),
  payrollUsd: usd(4971504000), local: stats(inr, 125300000), usd: stats(usd, 1503600),
  bands: { below: 180, within: 2531, above: 208, withinPercent: 86.7, averageCompaRatio: 1.0 },
  variablePayTargetUsd: usd(500000000), bonusesUsdLast12Months: usd(300000000),
};

const CARD: OutlierCard = {
  id: 'UNDERPAID_GROUP:IN:Operations Analyst', type: 'UNDERPAID_GROUP', severity: 816,
  headline: 'In India, 35 of 123 people in Operations Analyst roles are paid below their pay band',
  detail: 'Average compa-ratio of those below band: 0.77', affected: 35, groupSize: 123,
  filter: { country: 'IN', title: 'Operations Analyst', bandPosition: 'BELOW', sort: 'compaRatio,asc' },
};

const distribution = (currency: string): Distribution => ({ asOf: '', currency, headcount: 1, buckets: [], stats: null });

describe('InsightsPage', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'insights', component: InsightsPage }, { path: 'employees', children: [] }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    TestBed.overrideComponent(InsightsPage, { remove: { imports: [Chart] }, add: { imports: [ChartStub] } });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  async function open(url: string, { failCountries = false } = {}): Promise<void> {
    await harness.navigateByUrl(url, InsightsPage);
    harness.detectChanges();
    http.expectOne('/api/meta').flush({ departments: [{ id: 8, name: 'Operations' }] });
    http.expectOne('/api/insights/summary').flush(SUMMARY);
    const countries = http.expectOne('/api/insights/by-country');
    if (failCountries) {
      countries.flush({ title: 'boom' }, { status: 500, statusText: 'Server Error' });
    } else {
      countries.flush({ asOf: '', fxRatesAsOf: '', data: [INDIA] });
    }
    http.expectOne((req) => req.url === '/api/insights/outliers').flush({ asOf: '', fxRatesAsOf: '', data: [CARD] });
    http.expectOne('/maps/countries-110m.json').flush({ type: 'Topology', objects: { countries: { type: 'GeometryCollection', geometries: [] } }, arcs: [] });
    harness.detectChanges();
  }

  function text(): string {
    return harness.routeNativeElement!.textContent!.replace(/\s+/g, ' ');
  }

  it('shows the headline figures', async () => {
    await open('/insights');
    http.expectOne((req) => req.url === '/api/insights/distribution').flush(distribution('USD'));
    harness.detectChanges();

    expect(text()).toContain('across 8 countries');
    expect(text()).toContain('$756.7M with variable pay at target');
    expect(text()).toContain('Bonuses, last 12 months');
    expect(text()).toContain('507 below');
    expect(text()).toContain('769 above');
  });

  it('links every spotlight card to the directory with its exact filter', async () => {
    await open('/insights');
    http.expectOne((req) => req.url === '/api/insights/distribution').flush(distribution('USD'));
    harness.detectChanges();

    const link = harness.routeNativeElement!.querySelector('a.see') as HTMLAnchorElement;
    expect(link.textContent).toContain('See 35 people');
    expect(link.getAttribute('href'))
      .toBe('/employees?country=IN&title=Operations%20Analyst&bandPosition=BELOW&sort=compaRatio,asc');
  });

  it('selecting a country puts it in the URL and loads its distribution in local currency', async () => {
    await open('/insights');
    http.expectOne((req) => req.url === '/api/insights/distribution' && !req.params.has('country')).flush(distribution('USD'));
    harness.detectChanges();

    (harness.routeNativeElement!.querySelector('.country-button') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/insights?country=IN');
    const request = http.expectOne((req) => req.url === '/api/insights/distribution');
    expect(request.request.params.get('country')).toBe('IN');
    request.flush(distribution('INR'));
    harness.detectChanges();
    expect(text()).toContain('Salary distribution · India');
    expect(text()).toContain('View employees');
  });

  it('a failed card offers a retry while the others still render', async () => {
    await open('/insights', { failCountries: true });
    http.expectOne((req) => req.url === '/api/insights/distribution').flush(distribution('USD'));
    harness.detectChanges();

    expect(text()).toContain("Couldn't load");
    expect(text()).toContain('across 8 countries');
    expect(text()).toContain('See 35 people');
  });
});
