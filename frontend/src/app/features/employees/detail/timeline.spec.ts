import { CareerEvent, SalaryHistoryItem } from '../../../core/api/models';
import { buildTimeline, careerTitle, isPromotion } from './timeline';

const salary = (id: number, date: string): SalaryHistoryItem => ({
  id, amount: { minorUnits: 1, currency: 'INR', amount: '0.01' }, effectiveDate: date, reason: 'ANNUAL_REVIEW',
  note: null, status: 'PAST', changePercent: null, recordedBy: null,
});
const event = (id: number, type: CareerEvent['type'], at: string, from: string | null = null, to: string | null = null): CareerEvent =>
  ({ id, type, from, to, occurredAt: at, recordedBy: 'Priya Sharma' });

describe('career timeline', () => {
  it('tells promotions from other level changes', () => {
    expect(isPromotion('L2', 'L3')).toBe(true);
    expect(isPromotion('L4', 'L3')).toBe(false);
    expect(careerTitle(event(1, 'LEVEL_CHANGED', '2026-10-17T06:00:00Z', 'L2', 'L3')).title).toBe('Promoted L2 → L3');
    expect(careerTitle(event(1, 'LEVEL_CHANGED', '2026-10-17T06:00:00Z', 'L4', 'L3')).title).toBe('Level changed L4 → L3');
    expect(careerTitle(event(1, 'DEPARTMENT_CHANGED', '2026-10-17T06:00:00Z', 'Engineering', 'Product')).title)
      .toBe('Moved to Product (from Engineering)');
    expect(careerTitle(event(1, 'DEACTIVATED', '2026-10-17T06:00:00Z')).title).toBe('Deactivated');
  });

  it('merges salary changes and career events newest first, salary first on the same day', () => {
    const timeline = buildTimeline(
      [salary(1, '2025-01-19'), salary(2, '2026-04-01'), salary(3, '2026-10-17')],
      [event(10, 'LEVEL_CHANGED', '2026-10-17T05:00:00Z', 'L2', 'L3'), event(11, 'TITLE_CHANGED', '2026-06-02T09:00:00Z', 'A', 'B')],
    );

    expect(timeline.map((t) => (t.kind === 'salary' ? `salary ${t.item.id}` : t.title))).toEqual([
      'salary 3', 'Promoted L2 → L3', 'Title: A → B', 'salary 2', 'salary 1',
    ]);
  });

  it('places bonuses between salary changes and career events on the same day, with unique keys', () => {
    const bonus = { id: 3, type: 'SPOT' as const, amount: { minorUnits: 1, currency: 'INR', amount: '0.01' },
      awardedOn: '2026-10-17', note: 'Great work', recordedBy: 'Priya Sharma' };

    const timeline = buildTimeline([salary(3, '2026-10-17')], [event(3, 'VARIABLE_TARGET_CHANGED', '2026-10-17T05:00:00Z', '10%', '15%')], [bonus]);

    expect(timeline.map((t) => (t.kind === 'salary' ? 'salary' : t.title))).toEqual(['salary', 'Spot bonus', 'Variable pay target 10% → 15%']);
    expect(new Set(timeline.map((t) => t.key)).size).toBe(3);
  });
});
