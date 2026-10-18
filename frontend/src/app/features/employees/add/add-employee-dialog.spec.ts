import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';

import { Meta, PayBandSummary } from '../../../core/api/models';
import { AddEmployeeDialog } from './add-employee-dialog';

const META: Meta = {
  countries: [{ code: 'IN', name: 'India', currency: 'INR' }, { code: 'JP', name: 'Japan', currency: 'JPY' }],
  departments: [{ id: 1, name: 'Engineering' }],
  jobTitles: ['Software Engineer'],
  levels: ['L1', 'L2', 'L3', 'L4', 'L5', 'L6'],
  statuses: ['ACTIVE', 'INACTIVE'],
  reportingCurrency: 'USD',
  fxRatesAsOf: '2026-10-01',
};
const inr = (minorUnits: number) => ({ minorUnits, currency: 'INR', amount: (minorUnits / 100).toFixed(2) });
const BANDS: PayBandSummary[] = [{
  countryCode: 'IN', countryName: 'India', level: 'L2', band: { min: inr(1_000_000_00), mid: inr(1_250_000_00), max: inr(1_500_000_00) },
  headcount: 1, below: 0, within: 1, above: 0,
}];

describe('AddEmployeeDialog', () => {
  let http: HttpTestingController;
  const close = vi.fn();

  beforeEach(() => {
    close.mockReset();
    TestBed.configureTestingModule({
      imports: [AddEmployeeDialog],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MatDialogRef, useValue: { close } }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(AddEmployeeDialog);
    fixture.detectChanges();
    http.expectOne('/api/meta').flush(META);
    http.expectOne('/api/pay-bands').flush(BANDS);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const type = (label: string, value: string) => {
      const input = [...el.querySelectorAll('mat-form-field')].find((f) => f.textContent?.includes(label))!.querySelector('input')!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const form = (fixture.componentInstance as unknown as { form: { patchValue(v: object): void } }).form;
    return { fixture, el, type, form };
  }

  function fill(r: ReturnType<typeof render>, salary: string) {
    r.type('First name', 'Meera');
    r.type('Last name', 'Iyer');
    r.type('Work email', 'meera.iyer@acme.example');
    r.form.patchValue({ countryCode: 'IN', departmentId: 1, level: 'L2', hireDate: '2026-11-02' });
    r.type('Job title', 'Software Engineer');
    r.type('Starting salary', salary);
    r.fixture.detectChanges();
  }

  it('shows where the starting salary lands in the band, in the country currency', () => {
    const r = render();
    fill(r, '9,50,000');

    expect(r.el.textContent).toContain('Starting salary (INR, per year)');
    expect(r.el.textContent).toMatch(/compa-ratio\s*0\.76\s*· below band/);
  });

  it('creates the employee with an exact amount and closes with them', () => {
    const r = render();
    fill(r, '1250000');
    (r.el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const req = http.expectOne('/api/employees');
    expect(req.request.body).toEqual({
      firstName: 'Meera', lastName: 'Iyer', email: 'meera.iyer@acme.example', countryCode: 'IN', departmentId: 1,
      jobTitle: 'Software Engineer', level: 'L2', hireDate: '2026-11-02', salaryAmount: '1250000.00',
    });
    req.flush({ id: 10001, employeeCode: 'E10001' });
    expect(close).toHaveBeenCalledWith(expect.objectContaining({ employeeCode: 'E10001' }));
  });

  it('shows a duplicate email next to the email field and keeps the dialog open', () => {
    const r = render();
    fill(r, '1250000');
    (r.el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    http.expectOne('/api/employees').flush({ detail: 'An employee with email meera.iyer@acme.example already exists' },
      { status: 409, statusText: 'Conflict' });
    r.fixture.detectChanges();

    expect(r.el.querySelector('mat-error')?.textContent).toContain('Someone already has this email');
    expect(close).not.toHaveBeenCalled();
  });

  it('does not submit an incomplete form', () => {
    const r = render();
    (r.el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    r.fixture.detectChanges();

    http.expectNone('/api/employees');
    expect(r.el.querySelectorAll('mat-error').length).toBeGreaterThan(3);
  });
});
