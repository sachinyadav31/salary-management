/** Concrete colours for canvas drawing, resolved from the app's CSS tokens. */
export interface ChartPalette {
  text: string;
  muted: string;
  border: string;
  surface: string;
  surface2: string;
  accent: string;
  accent2: string;
  below: string;
  within: string;
  above: string;
  watch: string;
}

const TOKENS: Record<keyof ChartPalette, string> = {
  text: '--pa-text',
  muted: '--pa-muted',
  border: '--pa-border',
  surface: '--pa-surface',
  surface2: '--pa-surface-2',
  accent: '--pa-accent',
  accent2: '--pa-accent-2',
  below: '--pa-below',
  within: '--pa-within',
  above: '--pa-above',
  watch: '--pa-watch',
};

/**
 * A custom property can hold any CSS colour syntax (hex, color-mix(), …) and reads back as that raw text.
 * Painting a probe element with `color: var(--token)` and reading the computed colour always yields a
 * concrete rgb() value the canvas can use, so the tokens stay the single source of truth.
 */
export function resolvePalette(doc: Document = document): ChartPalette {
  const probe = doc.createElement('span');
  probe.style.display = 'none';
  doc.body.appendChild(probe);
  try {
    const palette = {} as ChartPalette;
    for (const [key, token] of Object.entries(TOKENS) as [keyof ChartPalette, string][]) {
      probe.style.color = `var(${token})`;
      palette[key] = getComputedStyle(probe).color;
    }
    return palette;
  } finally {
    probe.remove();
  }
}
