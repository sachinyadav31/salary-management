import { Params } from '@angular/router';

/** A palette entry that isn't a person: a place to go or an action. */
export interface PaletteCommand {
  id: string;
  label: string;
  hint: string;
  icon: string;
  /** Extra words it should match, e.g. "dashboard" for Insights. */
  keywords: string;
  /** Navigate here, or run the named action. */
  path?: string;
  queryParams?: Params;
  action?: 'add-employee';
}

export const COMMANDS: PaletteCommand[] = [
  { id: 'insights', label: 'Pay insights', hint: 'Dashboard', icon: 'monitoring', keywords: 'dashboard home map spotlight', path: '/insights' },
  { id: 'employees', label: 'Employees', hint: 'Directory', icon: 'groups', keywords: 'directory people list', path: '/employees' },
  { id: 'below', label: 'People paid below their band', hint: 'Directory filter', icon: 'trending_down', keywords: 'underpaid band low', path: '/employees', queryParams: { bandPosition: 'BELOW' } },
  { id: 'above', label: 'People paid above their band', hint: 'Directory filter', icon: 'trending_up', keywords: 'overpaid band high', path: '/employees', queryParams: { bandPosition: 'ABOVE' } },
  { id: 'add', label: 'Add employee', hint: 'Action', icon: 'person_add', keywords: 'new hire create', action: 'add-employee' },
];

/** Commands whose label or keywords contain every typed word (any order, case-insensitive). */
export function matchCommands(commands: PaletteCommand[], query: string): PaletteCommand[] {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return commands;
  return commands.filter((c) => {
    const text = `${c.label} ${c.keywords}`.toLowerCase();
    return words.every((w) => text.includes(w));
  });
}

/** Arrow-key movement through the results, wrapping at both ends. */
export function moveActive(index: number, delta: number, length: number): number {
  if (length === 0) return -1;
  return (index + delta + length) % length;
}

/** People search starts at 2 characters (an employee code like "E1" or a short name). */
export const MIN_SEARCH = 2;

/** ⌘K on macOS, Ctrl+K elsewhere. */
export function isPaletteShortcut(event: KeyboardEvent): boolean {
  return (event.metaKey || event.ctrlKey) && !event.altKey && !event.shiftKey && event.key.toLowerCase() === 'k';
}
