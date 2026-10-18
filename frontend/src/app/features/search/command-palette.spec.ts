import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';

import { EmployeeSummary, PageResponse } from '../../core/api/models';
import { CommandPalette } from './command-palette';

const MEERA = {
  id: 10001, employeeCode: 'E10001', firstName: 'Meera', lastName: 'Iyer', fullName: 'Meera Iyer', email: 'meera.iyer@acme.example',
  countryCode: 'IN', countryName: 'India', departmentId: 1, department: 'Engineering', jobTitle: 'Software Engineer', level: 'L2',
  status: 'ACTIVE', hireDate: '2026-10-18', salary: { minorUnits: 95000000, currency: 'INR', amount: '950000.00' },
  salaryUsd: { minorUnits: 1140000, currency: 'USD', amount: '11400.00' }, payBand: null, compaRatio: null, bandPosition: null,
} as EmployeeSummary;

describe('CommandPalette', () => {
  let http: HttpTestingController;
  const close = vi.fn();

  beforeEach(() => {
    vi.useFakeTimers();
    close.mockReset();
    TestBed.configureTestingModule({
      imports: [CommandPalette],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: MatDialogRef, useValue: { close } }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => vi.useRealTimers());

  function render() {
    const fixture = TestBed.createComponent(CommandPalette);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const input = el.querySelector('input') as HTMLInputElement;
    const typeText = (text: string) => {
      input.value = text;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      vi.advanceTimersByTime(200);
      fixture.detectChanges();
    };
    const press = (key: string) => {
      input.dispatchEvent(new KeyboardEvent('keydown', { key }));
      fixture.detectChanges();
    };
    return { fixture, el, input, typeText, press };
  }

  it('lists commands before anything is typed', () => {
    const { el } = render();
    expect(el.textContent).toContain('Pay insights');
    expect(el.textContent).toContain('Add employee');
    http.expectNone((r) => r.url === '/api/employees');
  });

  it('searches people across all statuses and opens the chosen one with Enter', () => {
    const { el, input, typeText, press, fixture } = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    typeText('meera');
    const req = http.expectOne((r) => r.url === '/api/employees');
    expect(req.request.params.get('search')).toBe('meera');
    expect(req.request.params.has('status')).toBe(false);
    req.flush({ content: [MEERA], page: 0, size: 8, totalElements: 1, totalPages: 1 } as unknown as PageResponse<EmployeeSummary>);
    fixture.detectChanges();

    expect(el.textContent).toContain('Meera Iyer');
    expect(el.textContent).toContain('E10001');
    expect(input.getAttribute('aria-activedescendant')).toBe('palette-item-0');
    press('Enter');
    expect(close).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/employees', 10001]);
  });

  it('moves with the arrow keys and runs a command', () => {
    const { el, typeText, press } = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    typeText('band');
    expect(el.querySelectorAll('[role=option]')).toHaveLength(2);
    press('ArrowDown');
    expect(el.querySelector('[aria-selected=true]')?.textContent).toContain('above their band');
    press('Enter');
    expect(navigate).toHaveBeenCalledWith(['/employees'], { queryParams: { bandPosition: 'ABOVE' } });
  });

  it('says when nobody matches', () => {
    const { el, typeText, fixture } = render();
    typeText('xqzw');
    http.expectOne((r) => r.url === '/api/employees').flush({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(el.textContent).toContain('No one matches “xqzw”');
  });
});
