import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';

describe('App shell', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('shows navigation to insights and employees', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const labels = Array.from(fixture.nativeElement.querySelectorAll('nav a.nav-item .label')) as HTMLElement[];

    expect(labels.map((label) => label.textContent?.trim())).toEqual(['Insights', 'Employees']);
  });

  it('shows the FX date once reference data arrives', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    TestBed.inject(HttpTestingController).expectOne('/api/meta').flush({ fxRatesAsOf: '2026-10-01' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.fx-chip').textContent).toContain('FX as of 2026-10-01');
  });

  it('toggles the theme on the document', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const toggle = fixture.nativeElement.querySelector('nav button') as HTMLButtonElement;

    toggle.click();
    fixture.detectChanges();
    TestBed.tick();

    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });
});
