import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
import { AuthService } from './core/auth/auth';

describe('App shell', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  function render(signedIn: boolean) {
    if (signedIn) TestBed.inject(AuthService).user.set({ email: 'hr.admin@acme.example', fullName: 'Priya Sharma' });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    return fixture;
  }

  it('shows navigation to insights and employees', () => {
    const labels = Array.from(render(true).nativeElement.querySelectorAll('nav a.nav-item .label')) as HTMLElement[];

    expect(labels.map((label) => label.textContent?.trim())).toEqual(['Insights', 'Employees']);
  });

  it('shows the FX date once reference data arrives', () => {
    const fixture = render(true);

    TestBed.inject(HttpTestingController).expectOne('/api/meta').flush({ fxRatesAsOf: '2026-10-01' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.fx-chip').textContent).toContain('FX as of 2026-10-01');
  });

  it('does not call the API while signed out', () => {
    render(false);

    TestBed.inject(HttpTestingController).expectNone('/api/meta');
  });

  it('shows the signed-in user in the account menu', () => {
    const avatar = render(true).nativeElement.querySelector('.user-avatar') as HTMLElement;

    expect(avatar.textContent?.trim()).toBe('PS');
  });
});
