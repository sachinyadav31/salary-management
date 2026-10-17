import { BandPosition, Money, PayBand } from '../../../core/api/models';

/** Decimal places of a currency per ISO 4217 (USD 2, JPY 0), as the browser knows them. */
export function currencyDigits(currency: string): number {
  return new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2;
}

export type ParsedAmount =
  | { ok: true; minorUnits: number; amount: string }
  | { ok: false; error: 'required' | 'invalid' | 'decimals' | 'positive' };

/**
 * Parses what the user typed into exact minor units with string arithmetic (no floating point), mirroring the
 * backend's Money rules: at most the currency's decimal places, strictly positive.
 */
export function parseAmount(input: string | number | null | undefined, currency: string): ParsedAmount {
  const text = String(input ?? '').trim().replace(/,/g, '');
  if (!text) return { ok: false, error: 'required' };
  const match = /^(\d+)(?:\.(\d+))?$/.exec(text);
  if (!match) return { ok: false, error: 'invalid' };
  const digits = currencyDigits(currency);
  const fraction = (match[2] ?? '').replace(/0+$/, '');
  if (fraction.length > digits) return { ok: false, error: 'decimals' };
  const minorUnits = Number(match[1] + fraction.padEnd(digits, '0'));
  if (minorUnits <= 0) return { ok: false, error: 'positive' };
  const amount = digits === 0 ? String(minorUnits) : (minorUnits / 10 ** digits).toFixed(digits);
  return { ok: true, minorUnits, amount };
}

export interface ChangePreview {
  /** Relative to the current salary, one decimal (e.g. 10.0). */
  changePercent: number;
  compaRatio: number | null;
  position: BandPosition | null;
}

/**
 * What a new salary would mean. Compa-ratio uses exactly the backend's integer formula (basis points,
 * rounded half up, then two decimals) so the preview never disagrees with what the server stores.
 */
export function previewChange(current: Money, band: PayBand | null, newMinorUnits: number): ChangePreview {
  const changePercent = Math.round(((newMinorUnits - current.minorUnits) * 1000) / current.minorUnits) / 10;
  if (!band) return { changePercent, compaRatio: null, position: null };
  const mid = band.mid.minorUnits;
  const basisPoints = Math.floor((newMinorUnits * 10000 + Math.floor(mid / 2)) / mid);
  const compaRatio = Math.round(basisPoints / 100) / 100;
  const position: BandPosition =
    newMinorUnits < band.min.minorUnits ? 'BELOW' : newMinorUnits > band.max.minorUnits ? 'ABOVE' : 'WITHIN';
  return { changePercent, compaRatio, position };
}

/** "4 yrs 5 mos", "8 mos", "starts soon". */
export function tenure(hireDate: string, today: Date = new Date()): string {
  const hired = new Date(`${hireDate}T00:00:00Z`);
  let months = (today.getUTCFullYear() - hired.getUTCFullYear()) * 12 + (today.getUTCMonth() - hired.getUTCMonth());
  if (today.getUTCDate() < hired.getUTCDate()) months--;
  if (months < 0) return 'starts soon';
  const years = Math.floor(months / 12);
  const rest = months % 12;
  const parts = [];
  if (years) parts.push(`${years} yr${years === 1 ? '' : 's'}`);
  if (rest || !years) parts.push(`${rest} mo${rest === 1 ? '' : 's'}`);
  return parts.join(' ');
}
