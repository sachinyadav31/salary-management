
import { Meta } from '../../../core/api/models';
import { DEFAULT_QUERY } from '../directory-query';
import { BulkFormValue, describeSelection, parsePercent, selectionCurrency, toBulkRequest } from './bulk-change';

const META = {
  countries: [{ code: 'IN', name: 'India', currency: 'INR' }, { code: 'JP', name: 'Japan', currency: 'JPY' }],
  departments: [{ id: 1, name: 'Engineering' }],
} as Meta;

const form = (patch: Partial<BulkFormValue>): BulkFormValue => ({
  kind: 'RAISE', percent: '4.5', bonusMode: 'percent', amount: '', effectiveDate: '2027-04-01', reason: 'MERIT',
  bonusType: 'PERFORMANCE', note: '', ...patch,
});

describe('bulk change helpers', () => {
  it('parses percentages with the API limits', () => {
    expect(parsePercent('4.5', 50)).toEqual({ ok: true, value: '4.5' });
    expect(parsePercent('0', 50)).toMatchObject({ ok: false, error: 'More than 0%' });
    expect(parsePercent('51', 50)).toMatchObject({ ok: false, error: 'At most 50%' });
    expect(parsePercent('4.555', 50)).toMatchObject({ ok: false });
    expect(parsePercent('', 50)).toMatchObject({ ok: false, error: 'Enter a percentage' });
  });

  it('builds raise and bonus requests', () => {
    expect(toBulkRequest(form({ note: '  FY27 merit ' }), null)).toEqual({
      ok: true,
      request: { kind: 'RAISE', percent: '4.5', amount: null, effectiveDate: '2027-04-01', reason: 'MERIT', bonusType: null, note: 'FY27 merit' },
    });
    expect(toBulkRequest(form({ kind: 'BONUS', percent: '10' }), null)).toMatchObject({
      ok: true, request: { kind: 'BONUS', percent: '10', amount: null, bonusType: 'PERFORMANCE', reason: null },
    });
    expect(toBulkRequest(form({ kind: 'BONUS', bonusMode: 'fixed', amount: '50,000' }), 'INR')).toMatchObject({
      ok: true, request: { percent: null, amount: '50000.00' },
    });
  });

  it('explains what is missing', () => {
    expect(toBulkRequest(form({ kind: 'BONUS', bonusMode: 'fixed', amount: '500' }), null)).toMatchObject({ ok: false, error: 'A fixed amount needs a single country' });
    expect(toBulkRequest(form({ kind: 'BONUS', bonusType: 'SPOT', percent: '5' }), null)).toMatchObject({ ok: false, error: 'Say what this spot bonus recognises' });
    expect(toBulkRequest(form({ kind: 'BONUS', bonusMode: 'fixed', amount: '1.5' }), 'JPY')).toMatchObject({ ok: false, error: 'Too many decimal places' });
  });

  it('describes the selection and finds its single currency', () => {
    const query = { ...DEFAULT_QUERY, countries: ['IN'], departments: [1], levels: ['L3' as const] };
    expect(describeSelection(query, META)).toBe('India · Engineering · L3');
    expect(describeSelection(DEFAULT_QUERY, META)).toBe('Everyone');
    expect(selectionCurrency(query, META)).toBe('INR');
    expect(selectionCurrency({ ...query, countries: ['IN', 'JP'] }, META)).toBeNull();
  });
});
