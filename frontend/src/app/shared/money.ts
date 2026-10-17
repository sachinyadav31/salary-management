import { Pipe, PipeTransform } from '@angular/core';
import { Money } from '../core/api/models';

/** Each currency is shown the way its users expect: lakh grouping for INR, no decimals for JPY, etc. */
const LOCALE_BY_CURRENCY: Record<string, string> = {
  USD: 'en-US',
  INR: 'en-IN',
  GBP: 'en-GB',
  EUR: 'en-IE',
  CAD: 'en-CA',
  SGD: 'en-SG',
  AUD: 'en-AU',
  JPY: 'en-JP',
};

export type MoneyStyle = 'whole' | 'exact' | 'compact';

const formatters = new Map<string, Intl.NumberFormat>();

/**
 * Display-only formatting of an exact API amount. `whole` (default) drops minor units for clean tables,
 * `exact` keeps them, `compact` abbreviates for KPI cards ($679.7M).
 */
export function formatMoney(money: Money | null | undefined, style: MoneyStyle = 'whole'): string {
  if (!money) {
    return '–';
  }
  const key = `${money.currency}|${style}`;
  let formatter = formatters.get(key);
  if (!formatter) {
    formatter = new Intl.NumberFormat(LOCALE_BY_CURRENCY[money.currency] ?? 'en-US', {
      style: 'currency',
      currency: money.currency,
      ...(style === 'compact'
        ? { notation: 'compact', minimumFractionDigits: 0, maximumFractionDigits: 1 }
        : style === 'whole'
          ? { maximumFractionDigits: 0 }
          : {}),
    });
    formatters.set(key, formatter);
  }
  return formatter.format(Number(money.amount));
}

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(money: Money | null | undefined, style: MoneyStyle = 'whole'): string {
    return formatMoney(money, style);
  }
}
