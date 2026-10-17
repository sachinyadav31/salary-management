import { TestBed } from '@angular/core/testing';
import { Money, PayBand } from '../core/api/models';
import { BandBar, bandBarGeometry } from './band-bar';
import { CompaBadge } from './compa-badge';
import { countryFlag } from './flag';
import { formatMoney } from './money';

const money = (minorUnits: number, currency: string, amount: string): Money => ({ minorUnits, currency, amount });

describe('formatMoney', () => {
  it('formats each currency in its own locale, without minor units by default', () => {
    expect(formatMoney(money(10850000, 'USD', '108500.00'))).toBe('$108,500');
    expect(formatMoney(money(125300000, 'INR', '1253000.00'))).toBe('₹12,53,000');
    expect(formatMoney(money(7140000, 'JPY', '7140000'))).toBe('¥7,140,000');
    expect(formatMoney(money(5430000, 'GBP', '54300.00'))).toBe('£54,300');
    expect(formatMoney(money(7520000, 'EUR', '75200.00'))).toBe('€75,200');
  });

  it('keeps minor units when exact, and abbreviates when compact', () => {
    expect(formatMoney(money(12345, 'USD', '123.45'), 'exact')).toBe('$123.45');
    expect(formatMoney(money(67968736600, 'USD', '679687366.00'), 'compact')).toBe('$679.7M');
  });

  it('shows a dash for missing values', () => {
    expect(formatMoney(null)).toBe('–');
  });
});

describe('countryFlag', () => {
  it('turns ISO codes into flag emoji', () => {
    expect(countryFlag('IN')).toBe('🇮🇳');
    expect(countryFlag('jp')).toBe('🇯🇵');
    expect(countryFlag('X')).toBe('');
  });
});

describe('bandBarGeometry', () => {
  // band 80..120, track 70..130
  const band: PayBand = { min: money(80, 'USD', '0.80'), mid: money(100, 'USD', '1.00'), max: money(120, 'USD', '1.20') };

  it('places the band in the middle two thirds of the track', () => {
    const g = bandBarGeometry(100, band);
    expect(g.bandStart).toBeCloseTo(16.67, 1);
    expect(g.bandEnd).toBeCloseTo(83.33, 1);
    expect(g.mid).toBeCloseTo(50, 5);
    expect(g.marker).toBeCloseTo(50, 5);
    expect(g.clamped).toBe(false);
  });

  it('puts below-band salaries left of the band, above-band right of it', () => {
    expect(bandBarGeometry(75, band).marker).toBeLessThan(bandBarGeometry(80, band).bandStart);
    expect(bandBarGeometry(125, band).marker).toBeGreaterThan(bandBarGeometry(120, band).bandEnd);
  });

  it('clamps extreme outliers to the edge and flags them', () => {
    expect(bandBarGeometry(10, band)).toMatchObject({ marker: 0, clamped: true });
    expect(bandBarGeometry(500, band)).toMatchObject({ marker: 100, clamped: true });
  });
});

describe('BandBar and CompaBadge', () => {
  it('describe themselves for screen readers', () => {
    const bar = TestBed.createComponent(BandBar);
    bar.componentRef.setInput('salary', money(2530000, 'GBP', '25300.00'));
    bar.componentRef.setInput('band', {
      min: money(2800000, 'GBP', '28000.00'), mid: money(3500000, 'GBP', '35000.00'), max: money(4200000, 'GBP', '42000.00'),
    });
    bar.componentRef.setInput('position', 'BELOW');
    bar.detectChanges();
    expect(bar.nativeElement.querySelector('[role=img]').getAttribute('aria-label'))
      .toBe('£25,300, band £28,000 to £42,000, below band');

    const badge = TestBed.createComponent(CompaBadge);
    badge.componentRef.setInput('compa', 0.72);
    badge.componentRef.setInput('position', 'BELOW');
    badge.detectChanges();
    const pill = badge.nativeElement.querySelector('.pill');
    expect(pill.textContent.trim()).toBe('0.72');
    expect(pill.getAttribute('aria-label')).toBe('Compa-ratio 0.72: 28% below band midpoint');
  });
});
