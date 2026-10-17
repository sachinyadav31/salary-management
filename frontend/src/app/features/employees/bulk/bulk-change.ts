import { BonusType, BulkChangeRequest, BulkKind, Meta, SalaryChangeReason } from '../../../core/api/models';
import { parseAmount } from '../detail/salary-preview';
import { DirectoryQuery } from '../directory-query';

export type BonusMode = 'percent' | 'fixed';

export interface BulkFormValue {
  kind: BulkKind;
  percent: string;
  bonusMode: BonusMode;
  amount: string;
  effectiveDate: string;
  reason: SalaryChangeReason;
  bonusType: BonusType;
  note: string;
}

export const BULK_REASONS: SalaryChangeReason[] = ['MERIT', 'ANNUAL_REVIEW', 'MARKET_ADJUSTMENT'];
/** Sign-on bonuses are per-person decisions, so they aren't offered in bulk (the API rejects them too). */
export const BULK_BONUS_TYPES: BonusType[] = ['PERFORMANCE', 'SPOT', 'RETENTION'];

/** Raise up to 50%, bonus up to 100% of base, two decimals at most: the same limits as the API. */
export function parsePercent(input: string, max: number): { ok: true; value: string } | { ok: false; error: string } {
  const text = input.trim();
  if (!text) return { ok: false, error: 'Enter a percentage' };
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(text)) return { ok: false, error: 'A number like 4 or 4.5' };
  const value = Number(text);
  if (value <= 0) return { ok: false, error: 'More than 0%' };
  if (value > max) return { ok: false, error: `At most ${max}%` };
  return { ok: true, value: text };
}

/** Builds the API request, or says what's wrong. `currency` is the selection's one currency, if it has one. */
export function toBulkRequest(value: BulkFormValue, currency: string | null): { ok: true; request: BulkChangeRequest } | { ok: false; error: string } {
  const note = value.note.trim() || null;
  if (!value.effectiveDate) return { ok: false, error: 'Choose a date' };
  if (value.kind === 'RAISE') {
    const percent = parsePercent(value.percent, 50);
    if (!percent.ok) return percent;
    return { ok: true, request: { kind: 'RAISE', percent: percent.value, amount: null, effectiveDate: value.effectiveDate, reason: value.reason, bonusType: null, note } };
  }
  if (value.bonusType === 'SPOT' && !note) return { ok: false, error: 'Say what this spot bonus recognises' };
  if (value.bonusMode === 'fixed') {
    if (!currency) return { ok: false, error: 'A fixed amount needs a single country' };
    const amount = parseAmount(value.amount, currency);
    if (!amount.ok) return { ok: false, error: amount.error === 'decimals' ? 'Too many decimal places' : 'Enter a positive amount, e.g. 25000' };
    return { ok: true, request: { kind: 'BONUS', percent: null, amount: amount.amount, effectiveDate: value.effectiveDate, reason: null, bonusType: value.bonusType, note } };
  }
  const percent = parsePercent(value.percent, 100);
  if (!percent.ok) return percent;
  return { ok: true, request: { kind: 'BONUS', percent: percent.value, amount: null, effectiveDate: value.effectiveDate, reason: null, bonusType: value.bonusType, note } };
}

/** The selection's currency when it is one country, else null (then only % of base works for a bonus). */
export function selectionCurrency(query: DirectoryQuery, meta: Meta | undefined): string | null {
  if (query.countries.length !== 1) return null;
  return meta?.countries.find((c) => c.code === query.countries[0])?.currency ?? null;
}

/** "India · Engineering · L3" — what the bulk change applies to, in words. */
export function describeSelection(query: DirectoryQuery, meta: Meta | undefined): string {
  const parts: string[] = [];
  if (query.countries.length) parts.push(query.countries.map((code) => meta?.countries.find((c) => c.code === code)?.name ?? code).join(', '));
  if (query.departments.length) parts.push(query.departments.map((id) => meta?.departments.find((d) => d.id === id)?.name ?? `#${id}`).join(', '));
  if (query.title) parts.push(query.title);
  if (query.levels.length) parts.push(query.levels.join(', '));
  if (query.bandPosition) parts.push(`${query.bandPosition.toLowerCase()} band`);
  if (query.compaMin !== null) parts.push(`compa ≥ ${query.compaMin}`);
  if (query.compaMax !== null) parts.push(`compa ≤ ${query.compaMax}`);
  if (query.search) parts.push(`“${query.search}”`);
  if (query.status === 'INACTIVE') parts.push('inactive');
  return parts.length ? parts.join(' · ') : 'Everyone';
}
