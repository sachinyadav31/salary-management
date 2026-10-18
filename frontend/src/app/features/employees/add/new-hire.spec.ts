import { HttpErrorResponse } from '@angular/common/http';

import { PayBandSummary } from '../../../core/api/models';
import { bandFor, createErrors, hireDateBounds, hireDateError } from './new-hire';

const TODAY = new Date('2026-10-18T09:00:00Z');
const inr = (minorUnits: number) => ({ minorUnits, currency: 'INR', amount: (minorUnits / 100).toFixed(2) });
const BANDS: PayBandSummary[] = [{
  countryCode: 'IN', countryName: 'India', level: 'L3', band: { min: inr(1_200_000_00), mid: inr(1_500_000_00), max: inr(1_800_000_00) },
  headcount: 1, below: 0, within: 1, above: 0,
}];

describe('new hire helpers', () => {
  it('allows hire dates from 1950 to one year ahead, like the server', () => {
    expect(hireDateBounds(TODAY)).toEqual({ min: '1950-01-01', max: '2027-10-18' });
    expect(hireDateError('2027-10-18', TODAY)).toBeNull();
    expect(hireDateError('2027-10-19', TODAY)).toBe('At most one year ahead');
    expect(hireDateError('1949-12-31', TODAY)).toBe('Not before 1950');
    expect(hireDateError('', TODAY)).toBe('Required');
  });

  it('finds the band for a country and level', () => {
    expect(bandFor(BANDS, 'IN', 'L3')?.mid.amount).toBe('1500000.00');
    expect(bandFor(BANDS, 'IN', 'L4')).toBeNull();
    expect(bandFor(undefined, 'IN', 'L3')).toBeNull();
  });

  it('puts server errors next to the right field', () => {
    const problem = (status: number, error: object) => new HttpErrorResponse({ status, error });
    expect(createErrors(problem(400, { errors: { salaryAmount: 'must be greater than 0' } }))).toEqual({ salaryAmount: 'must be greater than 0' });
    expect(createErrors(problem(409, { detail: 'An employee with email a@acme.example already exists' }))).toEqual({ email: 'Someone already has this email' });
    expect(createErrors(problem(400, { detail: 'Hire date can be at most 1 year ahead' }))).toEqual({ _form: 'Hire date can be at most 1 year ahead' });
  });
});
