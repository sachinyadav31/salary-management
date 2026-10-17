import { EmployeeDetail, Money, PayBand, SalaryHistoryItem } from '../../../core/api/models';
import { ChartPalette } from '../../../shared/charts/palette';
import { profileChanges } from './profile-diff';
import { parseAmount, previewChange, tenure } from './salary-preview';
import { axisFormatter, timelineOption } from './timeline-option';

const gbp = (minorUnits: number): Money => ({ minorUnits, currency: 'GBP', amount: (minorUnits / 100).toFixed(2) });
const BAND: PayBand = { min: gbp(2_800_000), mid: gbp(3_500_000), max: gbp(4_200_000) };

describe('parseAmount', () => {
  it('converts typed amounts to exact minor units without floating point', () => {
    expect(parseAmount('95000.1', 'GBP')).toEqual({ ok: true, minorUnits: 9_500_010, amount: '95000.10' });
    expect(parseAmount('1,25,000', 'INR')).toEqual({ ok: true, minorUnits: 12_500_000, amount: '125000.00' });
    expect(parseAmount(7140000, 'JPY')).toEqual({ ok: true, minorUnits: 7_140_000, amount: '7140000' });
    expect(parseAmount('0.29', 'USD')).toEqual({ ok: true, minorUnits: 29, amount: '0.29' }); // 0.29 * 100 is 28.999... as a float
  });

  it('rejects what the backend would reject', () => {
    expect(parseAmount('', 'GBP')).toEqual({ ok: false, error: 'required' });
    expect(parseAmount('12abc', 'GBP')).toEqual({ ok: false, error: 'invalid' });
    expect(parseAmount('-5', 'GBP')).toEqual({ ok: false, error: 'invalid' });
    expect(parseAmount('10.001', 'GBP')).toEqual({ ok: false, error: 'decimals' });
    expect(parseAmount('100.5', 'JPY')).toEqual({ ok: false, error: 'decimals' });
    expect(parseAmount('100.00', 'JPY').ok).toBe(true);
    expect(parseAmount('0', 'GBP')).toEqual({ ok: false, error: 'positive' });
  });
});

describe('previewChange', () => {
  it('shows the % change, the new compa-ratio and where it lands in the band', () => {
    expect(previewChange(gbp(2_530_000), BAND, 2_783_000)).toEqual({ changePercent: 10, compaRatio: 0.8, position: 'BELOW' });
    expect(previewChange(gbp(2_530_000), BAND, 3_500_000)).toEqual({ changePercent: 38.3, compaRatio: 1, position: 'WITHIN' });
    expect(previewChange(gbp(4_000_000), BAND, 4_300_000)).toMatchObject({ position: 'ABOVE', compaRatio: 1.23 });
  });

  it('rounds compa-ratio exactly like the backend (half up at the basis-point boundary)', () => {
    // US L3 midpoint 119,000.00: 115,424.05 -> 0.969950 -> 9700 bp -> 0.97
    const mid: PayBand = { min: { ...gbp(9_520_000), currency: 'USD' }, mid: { ...gbp(11_900_000), currency: 'USD' }, max: { ...gbp(14_280_000), currency: 'USD' } };
    expect(previewChange({ ...gbp(10_000_000), currency: 'USD' }, mid, 11_542_405).compaRatio).toBe(0.97);
  });

  it('works without a band', () => {
    expect(previewChange(gbp(100), null, 150)).toEqual({ changePercent: 50, compaRatio: null, position: null });
  });
});

describe('tenure', () => {
  const today = new Date('2026-10-17T00:00:00Z');
  it('describes time since hire', () => {
    expect(tenure('2022-05-11', today)).toBe('4 yrs 5 mos');
    expect(tenure('2025-10-17', today)).toBe('1 yr');
    expect(tenure('2026-02-20', today)).toBe('7 mos');
    expect(tenure('2026-10-01', today)).toBe('0 mos');
    expect(tenure('2026-11-01', today)).toBe('starts soon');
  });
});

describe('profileChanges', () => {
  it('lists only the fields someone else changed', () => {
    const before = { fullName: 'Ada Lovelace', email: 'ada@acme.test', department: 'Engineering', jobTitle: 'Engineer',
      level: 'L3', status: 'ACTIVE', salary: gbp(8_000_000) } as EmployeeDetail;
    const after = { ...before, jobTitle: 'Staff Engineer', level: 'L4', salary: gbp(8_800_000) } as EmployeeDetail;

    expect(profileChanges(before, after)).toEqual([
      { field: 'Job title', from: 'Engineer', to: 'Staff Engineer' },
      { field: 'Level', from: 'L3', to: 'L4' },
      { field: 'Salary', from: '£80,000', to: '£88,000' },
    ]);
  });
});

describe('timelineOption', () => {
  const palette = { accent: 'cyan', surface: 'navy', border: 'b', text: 't', muted: 'm' } as ChartPalette;
  const item = (id: number, date: string, minor: number, status: SalaryHistoryItem['status']): SalaryHistoryItem =>
    ({ id, amount: gbp(minor), effectiveDate: date, reason: 'ANNUAL_REVIEW', note: null, status, changePercent: null });

  it('draws effective pay solid and scheduled changes dashed, oldest first', () => {
    const history = [item(3, '2027-04-01', 9_000_000, 'SCHEDULED'), item(2, '2025-04-01', 8_800_000, 'CURRENT'),
      item(1, '2024-01-15', 8_000_000, 'PAST')];

    const [effective, scheduled] = timelineOption(history, palette, '2026-10-17')['series'] as
      { data: { value: [string, number] }[]; lineStyle: { type: string } }[];

    expect(effective.data.map((d) => d.value))
      .toEqual([['2024-01-15', 80000], ['2025-04-01', 88000], ['2026-10-17', 88000]]);
    expect(effective.lineStyle.type).toBe('solid');
    expect(scheduled.data.map((d) => d.value[0])).toEqual(['2025-04-01', '2027-04-01']);
    expect(scheduled.lineStyle.type).toBe('dashed');
  });

  it('turns a single hire record into a line up to today, with nothing dashed', () => {
    const [effective, scheduled] = timelineOption([item(1, '2024-01-15', 8_000_000, 'CURRENT')], palette, '2026-10-17')['series'] as
      { data: { value: [string, number] }[] }[];
    expect(effective.data.map((d) => d.value)).toEqual([['2024-01-15', 80000], ['2026-10-17', 80000]]);
    expect(scheduled.data).toEqual([]);
  });
});

describe('axisFormatter', () => {
  it('keeps nearby ticks distinguishable', () => {
    const jpy = axisFormatter('JPY');
    expect([6_500_000, 6_550_000, 6_600_000].map(jpy)).toEqual(['¥6.5M', '¥6.55M', '¥6.6M']);
  });
});
