import { COMMANDS, isPaletteShortcut, matchCommands, moveActive } from './palette';

describe('palette helpers', () => {
  it('matches commands by label or keywords, every word, any order', () => {
    expect(matchCommands(COMMANDS, '').length).toBe(COMMANDS.length);
    expect(matchCommands(COMMANDS, 'dashboard').map((c) => c.id)).toEqual(['insights']);
    expect(matchCommands(COMMANDS, 'band below').map((c) => c.id)).toEqual(['below']);
    expect(matchCommands(COMMANDS, 'HIRE').map((c) => c.id)).toEqual(['add']);
    expect(matchCommands(COMMANDS, 'zzz')).toEqual([]);
  });

  it('moves through results and wraps around', () => {
    expect(moveActive(0, 1, 3)).toBe(1);
    expect(moveActive(2, 1, 3)).toBe(0);
    expect(moveActive(0, -1, 3)).toBe(2);
    expect(moveActive(0, 1, 0)).toBe(-1);
  });

  it('recognises Cmd+K and Ctrl+K only', () => {
    const key = (init: KeyboardEventInit) => new KeyboardEvent('keydown', init);
    expect(isPaletteShortcut(key({ key: 'k', metaKey: true }))).toBe(true);
    expect(isPaletteShortcut(key({ key: 'K', ctrlKey: true }))).toBe(true);
    expect(isPaletteShortcut(key({ key: 'k' }))).toBe(false);
    expect(isPaletteShortcut(key({ key: 'k', metaKey: true, shiftKey: true }))).toBe(false);
  });
});
