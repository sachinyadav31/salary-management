import { CareerEvent, SalaryHistoryItem } from '../../../core/api/models';

export type TimelineItem =
  | { kind: 'salary'; date: string; item: SalaryHistoryItem }
  | { kind: 'career'; date: string; event: CareerEvent; title: string; icon: string };

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
    case 'DEPARTMENT_CHANGED':
      return { title: `Moved to ${event.to} (from ${event.from})`, icon: 'move_up' };
    case 'DEACTIVATED':
      return { title: 'Deactivated', icon: 'person_off' };
    case 'REACTIVATED':
      return { title: 'Reactivated', icon: 'person_check' };
  }
}

/**
 * Salary changes and career events in one list, newest first. Salary items are dated by when they take effect,
 * career events by when they happened. On the same day, salary changes come first.
 */
export function buildTimeline(history: SalaryHistoryItem[], events: CareerEvent[]): TimelineItem[] {
  const items: TimelineItem[] = [
    ...history.map((item): TimelineItem => ({ kind: 'salary', date: item.effectiveDate, item })),
    ...events.map((event): TimelineItem => ({ kind: 'career', date: event.occurredAt.slice(0, 10), event, ...careerTitle(event) })),
  ];
  const rank = (item: TimelineItem) => (item.kind === 'salary' ? 0 : 1);
  return items
    .map((item, index) => ({ item, index }))
    .sort((a, b) => b.item.date.localeCompare(a.item.date) || rank(a.item) - rank(b.item) || a.index - b.index)
    .map(({ item }) => item);
}
