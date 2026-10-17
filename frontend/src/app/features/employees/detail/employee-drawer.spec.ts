import { Component, input } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { firstValueFrom } from 'rxjs';

import { EmployeeApi } from '../../../core/api/employee-api';
import { EmployeeDetail, Money, SalaryHistoryItem } from '../../../core/api/models';
import { Chart } from '../../../shared/charts/chart';
import { EmployeeDrawer } from './employee-drawer';

@Component({ selector: 'pa-chart', template: '' })
class ChartStub {
  readonly option = input<unknown>();
  readonly ariaLabel = input<string>();
}

const gbp = (minorUnits: number): Money => ({ minorUnits, currency: 'GBP', amount: (minorUnits / 100).toFixed(2) });

const ADA: EmployeeDetail = {
  id: 7, employeeCode: 'E00007', firstName: 'Ada', lastName: 'Lovelace', fullName: 'Ada Lovelace',
  email: 'ada@acme.example', countryCode: 'GB', countryName: 'United Kingdom', departmentId: 1, department: 'Engineering',
  jobTitle: 'Software Engineer', level: 'L3', status: 'ACTIVE', hireDate: '2024-01-15',
  salary: gbp(8_000_000), salaryUsd: { minorUnits: 10_160_000, currency: 'USD', amount: '101600.00' },
  payBand: { min: gbp(4_760_000), mid: gbp(5_950_000), max: gbp(7_140_000) }, compaRatio: 1.34, bandPosition: 'ABOVE',
  version: 3, createdAt: '2024-01-15T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z', updatedBy: null,
};

const HISTORY: SalaryHistoryItem[] = [
  { id: 1, amount: gbp(8_000_000), effectiveDate: '2024-01-15', reason: 'HIRE', note: null, status: 'CURRENT', changePercent: null, recordedBy: null },
];

describe('EmployeeDrawer', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        // Same router features as the app: the drawer gets `id` from the route via input binding.
        provideRouter([{ path: 'employees', children: [{ path: ':id', component: EmployeeDrawer }] }], withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    TestBed.overrideComponent(EmployeeDrawer, { remove: { imports: [Chart] }, add: { imports: [ChartStub] } });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    document.querySelectorAll('.cdk-overlay-container').forEach((el) => (el.innerHTML = ''));
  });

  async function open(): Promise<void> {
    await harness.navigateByUrl('/employees/7', EmployeeDrawer);
    harness.detectChanges();
    http.expectOne('/api/meta').flush({ departments: [{ id: 1, name: 'Engineering' }], levels: ['L3', 'L4'], jobTitles: [] });
    http.expectOne('/api/employees/7').flush(ADA);
    http.expectOne('/api/employees/7/salary-history').flush(HISTORY);
    http.expectOne('/api/employees/7/career-events').flush([]);
    harness.detectChanges();
  }

  const root = () => harness.routeNativeElement as HTMLElement;
  const text = () => root().textContent!.replace(/\s+/g, ' ');
  const button = (label: string) =>
    Array.from(root().querySelectorAll('button')).find((b) => b.textContent?.includes(label)) as HTMLButtonElement;

  function type(selector: string, value: string): void {
    const field = root().querySelector(selector) as HTMLInputElement;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    harness.detectChanges();
  }

  async function fillSalaryChange(amount: string): Promise<void> {
    button('Record salary change').click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    type('input[formcontrolname=amount]', amount);
    type('input[formcontrolname=effectiveDate]', '2026-10-01');
  }

  function submit(): void {
    (root().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    harness.detectChanges();
  }

  it('shows the employee, their pay and history', async () => {
    await open();

    expect(text()).toContain('Ada Lovelace');
    expect(text()).toContain('£80,000');
    expect(text()).toContain('Hire');
    expect(text()).toContain('From initial data import');
  });

  it('after a promotion, offers to record the raise with the reason pre-selected', async () => {
    await open();
    button('Edit profile').click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    const form = harness.fixture.debugElement.query((d) => d.name === 'pa-profile-form').componentInstance;
    form.form.controls.level.setValue('L4');
    form.form.markAsDirty();
    harness.detectChanges();
    submit();

    const update = http.expectOne('/api/employees/7');
    expect(update.request.method).toBe('PUT');
    update.flush({ ...ADA, level: 'L4', version: 4 });
    http.expectOne('/api/employees/7/career-events').flush([
      { id: 1, type: 'LEVEL_CHANGED', from: 'L3', to: 'L4', occurredAt: '2026-10-17T06:00:00Z', recordedBy: 'Priya Sharma' },
    ]);
    harness.detectChanges();

    expect(text()).toContain('Promoted L3 → L4.');
    expect(text()).toMatch(/Promoted L3 → L4\s*by Priya Sharma/);
    button('Record raise').click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    const salaryForm = harness.fixture.debugElement.query((d) => d.name === 'pa-salary-change-form').componentInstance;
    expect(salaryForm.form.controls.reason.value).toBe('PROMOTION');
  });

  it('previews and records a salary change with the loaded version', async () => {
    await open();
    await fillSalaryChange('88000');

    expect(text()).toContain('+10.0% vs current');
    expect(text()).toContain('above band maximum');

    submit();
    const request = http.expectOne('/api/employees/7/salary-changes');
    expect(request.request.body).toEqual({ amount: '88000.00', effectiveDate: '2026-10-01', reason: 'ANNUAL_REVIEW', note: null, version: 3 });

    const changed = firstValueFrom(TestBed.inject(EmployeeApi).changed$);
    request.flush({ employee: { ...ADA, salary: gbp(8_800_000), version: 4 }, history: HISTORY });
    harness.detectChanges();

    expect(text()).toContain('£88,000');
    expect(root().querySelector('form')).toBeNull();
    expect((await changed).version).toBe(4);
  });

  it('on 409 loads the latest version, shows what changed and keeps the user\'s input', async () => {
    await open();
    await fillSalaryChange('88000');
    submit();
    http.expectOne('/api/employees/7/salary-changes').flush({ title: 'Conflict' }, { status: 409, statusText: 'Conflict' });

    http.expectOne('/api/employees/7').flush({ ...ADA, jobTitle: 'Staff Engineer', level: 'L4', version: 4 });
    http.expectOne('/api/employees/7/salary-history').flush(HISTORY);
    http.expectOne('/api/employees/7/career-events').flush([]);
    harness.detectChanges();

    expect(text()).toContain('was updated by someone else');
    expect(text()).toContain('Job title: Software Engineer → Staff Engineer');
    expect((root().querySelector('input[formcontrolname=amount]') as HTMLInputElement).value).toBe('88000');

    submit();
    expect(http.expectOne('/api/employees/7/salary-changes').request.body.version).toBe(4);
  });

  it('shows server validation errors next to the field', async () => {
    await open();
    await fillSalaryChange('88000');
    submit();
    http.expectOne('/api/employees/7/salary-changes').flush(
      { title: 'Validation failed', errors: { amount: 'must be greater than 0' } }, { status: 400, statusText: 'Bad Request' });
    harness.detectChanges();

    expect(root().querySelector('mat-error')?.textContent).toContain('must be greater than 0');
  });

  it('requires a note for corrections before submitting', async () => {
    await open();
    await fillSalaryChange('79000');
    const component = harness.fixture.debugElement.query((d) => d.name === 'pa-salary-change-form').componentInstance;
    component.form.controls.reason.setValue('CORRECTION');
    harness.detectChanges();

    submit();

    http.expectNone('/api/employees/7/salary-changes');
    expect(text()).toContain('Explain what is being corrected');
  });

  it('deactivates only after confirmation', async () => {
    await open();
    button('Deactivate').click();
    harness.detectChanges();
    await harness.fixture.whenStable();

    const confirmButton = Array.from(document.querySelectorAll('.cdk-overlay-container button'))
      .find((b) => b.textContent?.trim() === 'Deactivate') as HTMLButtonElement;
    expect(document.body.textContent).toContain('Deactivate Ada Lovelace?');
    confirmButton.click();
    harness.detectChanges();
    await harness.fixture.whenStable();

    const request = http.expectOne('/api/employees/7/status');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'INACTIVE', version: 3 });
  });

  it('lets you leave freely when nothing was typed, but asks when there is unsaved input', async () => {
    await open();
    const drawer = harness.routeDebugElement!.componentInstance as EmployeeDrawer;

    expect(await firstValueFrom(drawer.canLeave())).toBe(true);

    await fillSalaryChange('90000');
    const answer = firstValueFrom(drawer.canLeave());
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(document.body.textContent).toContain('Discard changes?');
    (Array.from(document.querySelectorAll('.cdk-overlay-container button')).find((b) => b.textContent?.trim() === 'Keep editing') as HTMLButtonElement).click();
    expect(await answer).toBe(false);
  });
});
