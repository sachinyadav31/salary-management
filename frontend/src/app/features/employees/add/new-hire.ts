import { HttpErrorResponse } from '@angular/common/http';

import { JobLevel, PayBand, PayBandSummary, ProblemDetail } from '../../../core/api/models';
import { problemMessage } from '../../../core/api/problem-interceptor';

const isoDate = (date: Date) => date.toISOString().slice(0, 10);

/** Same window as the backend's HireDates: 1950 up to one year ahead (a signed offer can start later). */
export function hireDateBounds(today: Date = new Date()): { min: string; max: string } {
  const max = new Date(today);
  max.setFullYear(max.getFullYear() + 1);
  return { min: '1950-01-01', max: isoDate(max) };
}

export function hireDateError(value: string, today: Date = new Date()): string | null {
  if (!value) return 'Required';
  const { min, max } = hireDateBounds(today);
  if (value < min) return 'Not before 1950';
  if (value > max) return 'At most one year ahead';
  return null;
}

export function bandFor(bands: PayBandSummary[] | undefined, countryCode: string | null, level: JobLevel): PayBand | null {
  return bands?.find((b) => b.countryCode === countryCode && b.level === level)?.band ?? null;
}

/**
 * Where a server error belongs: field errors (400) next to their fields, a duplicate email (409) next to the email
 * field, anything else above the buttons.
 */
export function createErrors(error: HttpErrorResponse): Record<string, string> {
  const problem = error.error as ProblemDetail | null;
  if (error.status === 400 && problem?.errors) return problem.errors;
  if (error.status === 409 && /email/i.test(problem?.detail ?? '')) return { email: 'Someone already has this email' };
  return { _form: problemMessage(error) };
}
