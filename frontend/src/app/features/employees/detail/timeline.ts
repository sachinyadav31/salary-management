import { Bonus, BonusType, CareerEvent, SalaryHistoryItem } from '../../../core/api/models';

/** `key` is unique across kinds (ids of different tables can collide), for @for tracking. */
export type TimelineItem =
  | { kind: 'salary'; key: string; date: string; item: SalaryHistoryItem }
  | { kind: 'career'; key: string; date: string; event: CareerEvent; title: string; icon: string }
  | { kind: 'bonus'; key: string; date: string; bonus: Bonus; title: string };

const BONUS_LABEL: Record<BonusType, string> = {
  PERFORMANCE: 'Performance bonus',
  SPOT: 'Spot bonus',
  SIGN_ON: 'Sign-on bonus',
  RETENTION: 'Retention bonus',
};

export function bonusLabel(type: BonusType): string {
  return BONUS_LABEL[type];
}

const LEVELS = ['L1', 'L2', 'L3', 'L4', 'L5', 'L6'];

/** True when a level change moves someone up. */
export function isPromotion(from: string | null, to: string | null): boolean {
  return LEVELS.indexOf(to ?? '') > LEVELS.indexOf(from ?? '');
}

export function careerTitle(event: CareerEvent): { title: string; icon: string } {
  switch (event.type) {
    case 'LEVEL_CHANGED':
      return isPromotion(event.from, event.to)
        ? { title: `Promoted ${event.from} → ${event.to}`, icon: 'trending_up' }
        : { title: `Level changed ${event.from} → ${event.to}`, icon: 'swap_vert' };
    case 'TITLE_CHANGED':
      return { title: `Title: ${event.from} → ${event.to}`, icon: 'badge' };
    case 'VARIABLE_TARGET_CHANGED':
      return { title: `Variable pay target ${event.from} → ${event.to}`, icon: 'percent' };
    case 'DEPARTMENT_CHANGED':
      return { title: `Moved to ${event.to} (from ${event.from})`, icon: 'move_up' };
    case 'DEACTIVATED':
      return { title: 'Deactivated', icon: 'person_off' };
    case 'REACTIVATED':
      return { title: 'Reactivated', icon: 'person_check' };
  }
}

/**
 * Salary changes, bonuses and career events in one list, newest first. Salary items are dated by when they take
 * effect, bonuses by their award date, career events by when they happened. On the same day: salary, bonus, career.
 */
export function buildTimeline(history: SalaryHistoryItem[], events: CareerEvent[], bonuses: Bonus[] = []): TimelineItem[] {
  const items: TimelineItem[] = [
    ...history.map((item): TimelineItem => ({ kind: 'salary', key: `salary-${item.id}`, date: item.effectiveDate, item })),
    ...bonuses.map((bonus): TimelineItem =>
      ({ kind: 'bonus', key: `bonus-${bonus.id}`, date: bonus.awardedOn, bonus, title: bonusLabel(bonus.type) })),
    ...events.map((event): TimelineItem =>
      ({ kind: 'career', key: `career-${event.id}`, date: event.occurredAt.slice(0, 10), event, ...careerTitle(event) })),
  ];
  const rank = (item: TimelineItem) => (item.kind === 'salary' ? 0 : item.kind === 'bonus' ? 1 : 2);
  return items
    .map((item, index) => ({ item, index }))
    .sort((a, b) => b.item.date.localeCompare(a.item.date) || rank(a.item) - rank(b.item) || a.index - b.index)
    .map(({ item }) => item);
}
