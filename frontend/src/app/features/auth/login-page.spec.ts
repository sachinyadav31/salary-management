import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { DEMO_PASSWORD, LoginPage } from './login-page';

describe('LoginPage', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'login', component: LoginPage }, { path: '**', children: [] }], withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  const root = () => harness.routeNativeElement as HTMLElement;

  function type(name: string, value: string): void {
    const input = root().querySelector(`input[formcontrolname=${name}]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submit(): void {
    root().querySelector('form')!.dispatchEvent(new Event('submit'));
    harness.detectChanges();
  }

  it('signs in and returns to the page the user wanted', async () => {
    await harness.navigateByUrl('/login?returnUrl=%2Femployees%2F42', LoginPage);
    type('email', 'hr.admin@acme.example');
    type('password', DEMO_PASSWORD);
    submit();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ email: 'hr.admin@acme.example', password: DEMO_PASSWORD });
    request.flush({ email: 'hr.admin@acme.example', fullName: 'Priya Sharma' });
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/employees/42');
  });

  it('never redirects to another site after sign-in (open redirect)', async () => {
    await harness.navigateByUrl('/login?returnUrl=%2F%2Fevil.example', LoginPage);
    root().querySelector<HTMLButtonElement>('.demo-account')!.click();
    harness.detectChanges();
    submit();
    http.expectOne('/api/auth/login').flush({ email: 'hr.admin@acme.example', fullName: 'Priya Sharma' });
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/insights');
  });

  it('shows the server\'s generic message on failure and clears the password', async () => {
    await harness.navigateByUrl('/login', LoginPage);
    type('email', 'hr.admin@acme.example');
    type('password', 'wrong');
    submit();
    http.expectOne('/api/auth/login').flush({ detail: 'Invalid email or password.' }, { status: 401, statusText: 'Unauthorized' });
    harness.detectChanges();

    expect(root().querySelector('.error')?.textContent).toContain('Invalid email or password.');
    expect((root().querySelector('input[formcontrolname=password]') as HTMLInputElement).value).toBe('');
  });

  it('explains a temporary lockout', async () => {
    await harness.navigateByUrl('/login', LoginPage);
    root().querySelector<HTMLButtonElement>('.demo-account')!.click();
    submit();
    http.expectOne('/api/auth/login').flush(
      { detail: 'Too many failed sign-in attempts. Try again in 5 minute(s).' }, { status: 429, statusText: 'Too Many Requests' });
    harness.detectChanges();

    expect(root().querySelector('.error')?.textContent).toContain('Try again in 5 minute(s)');
  });

  it('fills a demo account in one click and mentions an expired session', async () => {
    await harness.navigateByUrl('/login?expired=1', LoginPage);
    root().querySelector<HTMLButtonElement>('.demo-account')!.click();
    harness.detectChanges();

    expect((root().querySelector('input[formcontrolname=email]') as HTMLInputElement).value).toBe('hr.admin@acme.example');
    expect(root().textContent).toContain('Your session expired');
    http.expectNone('/api/auth/login');
  });
});
