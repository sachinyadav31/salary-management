/** ISO 3166 alpha-2 code to flag emoji (regional indicator symbols); no image assets needed. */
export function countryFlag(code: string | null | undefined): string {
  if (!code || !/^[A-Za-z]{2}$/.test(code)) {
    return '';
  }
  const base = 0x1f1e6 - 'A'.charCodeAt(0);
  return String.fromCodePoint(...code.toUpperCase().split('').map((c) => base + c.charCodeAt(0)));
}
