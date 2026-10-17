import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { EmployeeApi } from '../../core/api/employee-api';
import { EmployeeDetail, EmployeeSummary, Meta, PageResponse } from '../../core/api/models';
import { EmployeesPage } from './employees-page';

const META: Meta = {
  countries: [{ code: 'IN', name: 'India', currency: 'INR' }],
  departments: [{ id: 8, name: 'Operations' }],
  jobTitles: ['Operations Analyst'],
  levels: ['L1', 'L2', 'L3', 'L4', 'L5', 'L6'],
  statuses: ['ACTIVE', 'INACTIVE'],
  reportingCurrency: 'USD',
  fxRatesAsOf: '2026-10-01',
};

const TOM: EmployeeSummary = {
  id: 201, employeeCode: 'E00201', firstName: 'Tom', lastName: 'Agarwal', fullName: 'Tom Agarwal',
  email: 'tom.agarwal.201@acme.example', countryCode: 'IN', countryName: 'India', departmentId: 8,
  department: 'Operations', jobTitle: 'Operations Analyst', level: 'L2', status: 'ACTIVE', hireDate: '2020-05-01',
  salary: { minorUnits: 76000000, currency: 'INR', amount: '760000.00' },
  salaryUsd: { minorUnits: 912000, currency: 'USD', amount: '9120.00' },
  payBand: {
    min: { minorUnits: 83200000, currency: 'INR', amount: '832000.00' },
    mid: { minorUnits: 104000000, currency: 'INR', amount: '1040000.00' },
    max: { minorUnits: 124800000, currency: 'INR', amount: '1248000.00' },
  },
  compaRatio: 0.73,
  bandPosition: 'BELOW',
};

@Component({ selector: 'pa-drawer-stub', template: '' })
class DrawerStub {}

const page = (content: EmployeeSummary[], totalElements = content.length): PageResponse<EmployeeSummary> =>
  ({ content, page: 0, size: 25, totalElements, totalPages: Math.ceil(totalElements / 25) });

describe('EmployeesPage', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'employees', component: EmployeesPage, children: [{ path: ':id', component: DrawerStub }] }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  async function open(url: string): Promise<void> {
    await harness.navigateByUrl(url, EmployeesPage);
    harness.detectChanges();
    http.expectOne('/api/meta').flush(META);
  }

  function expectSearch(): TestRequest {
    harness.detectChanges();
    return http.expectOne((req) => req.url === '/api/employees');
  }

  it('loads active employees by name on first visit and renders them', async () => {
    await open('/employees');

    const request = expectSearch();
    expect(request.request.params.toString()).toBe('status=ACTIVE&sort=name,asc&page=0&size=25');
    request.flush(page([TOM], 35));
    harness.detectChanges();

    const text = harness.routeNativeElement!.textContent!;
    expect(text).toContain('Tom Agarwal');
    expect(text).toContain('₹7,60,000');
    expect(text).toContain('≈ $9,120');
    expect(text).toContain('35 employees');
  });

  it('turns an outlier deep link into the matching API query', async () => {
    await open('/employees?country=IN&title=Operations%20Analyst&bandPosition=BELOW&sort=compaRatio,asc');

    const params = expectSearch().request.params;
    expect(params.getAll('country')).toEqual(['IN']);
    expect(params.get('title')).toBe('Operations Analyst');
    expect(params.get('bandPosition')).toBe('BELOW');
    expect(params.get('sort')).toBe('compaRatio,asc');
  });

  it('applies a quick filter through the URL and goes back to the first page', async () => {
    await open('/employees?page=3');
    expectSearch().flush(page([TOM], 200));
    harness.detectChanges();

    const below = Array.from(harness.routeNativeElement!.querySelectorAll('.quick-chip'))
      .find((chip) => chip.textContent?.trim() === 'Below band') as HTMLButtonElement;
    below.click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/employees?bandPosition=BELOW');
    const params = expectSearch().request.params;
    expect(params.get('bandPosition')).toBe('BELOW');
    expect(params.get('page')).toBe('0');
  });

  it('exports exactly the current filters', async () => {
    await open('/employees?country=IN&bandPosition=BELOW&page=2');
    expectSearch().flush(page([TOM]));
    harness.detectChanges();

    const link = harness.routeNativeElement!.querySelector('a[download]') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/api/employees/export.csv?country=IN&status=ACTIVE&bandPosition=BELOW&sort=name,asc');
  });

  it('offers a retry when loading fails', async () => {
    await open('/employees');
    expectSearch().flush({ title: 'boom' }, { status: 500, statusText: 'Server Error' });
    harness.detectChanges();

    expect(harness.routeNativeElement!.textContent).toContain("Couldn't load employees");
  });

  it('opens an employee as a child route, keeping the current filters', async () => {
    await open('/employees?country=IN&bandPosition=BELOW');
    expectSearch().flush(page([TOM]));
    harness.detectChanges();

    (harness.routeNativeElement!.querySelector('tr.clickable') as HTMLElement).click();
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/employees/201?country=IN&bandPosition=BELOW');
    http.expectNone((req) => req.url === '/api/employees'); // the list is not refetched
    expect(harness.routeNativeElement!.querySelector('tr.open')).not.toBeNull();
  });

  it('refreshes the current page after an employee is saved in the drawer', async () => {
    await open('/employees?country=IN&page=2');
    expectSearch().flush(page([TOM], 100));
    harness.detectChanges();

    TestBed.inject(EmployeeApi).notifyChanged({ ...TOM, version: 2 } as EmployeeDetail);

    const params = expectSearch().request.params;
    expect(params.getAll('country')).toEqual(['IN']);
    expect(params.get('page')).toBe('2');
  });
});
