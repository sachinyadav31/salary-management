import { Component, computed, input } from '@angular/core';
import { BandPosition, Money, PayBand } from '../core/api/models';
import { formatMoney } from './money';

/** The track spans the band plus this share of its width on each side, so outliers sit visibly outside. */
const PADDING = 0.25;

export interface BandBarGeometry {
  /** Band start/end and midpoint as percentages of the track. */
  bandStart: number;
  bandEnd: number;
  mid: number;
  /** Where the salary sits, clamped to the track. */
  marker: number;
  /** True when the salary lies beyond the padded track and the marker is pinned to an edge. */
  clamped: boolean;
}

export function bandBarGeometry(salaryMinor: number, band: PayBand): BandBarGeometry {
  const min = band.min.minorUnits;
  const max = band.max.minorUnits;
  const width = Math.max(max - min, 1);
  const trackStart = min - width * PADDING;
  const trackWidth = width * (1 + 2 * PADDING);
  const percent = (value: number) => ((value - trackStart) / trackWidth) * 100;

  const raw = percent(salaryMinor);
  return {
    bandStart: percent(min),
    bandEnd: percent(max),
    mid: percent(band.mid.minorUnits),
    marker: Math.min(100, Math.max(0, raw)),
    clamped: raw < 0 || raw > 100,
  };
}

const POSITION_TEXT: Record<BandPosition, string> = {
  BELOW: 'below band',
  WITHIN: 'within band',
  ABOVE: 'above band',
};

/** A compact range bar: the pay band, its midpoint, and a dot where this salary sits. */
@Component({
  selector: 'pa-band-bar',
  template: `
    @if (geometry(); as g) {
      <div class="track" role="img" [attr.aria-label]="label()" [title]="label()">
        <span class="band" [style.left.%]="g.bandStart" [style.width.%]="g.bandEnd - g.bandStart"></span>
        <span class="mid" [style.left.%]="g.mid"></span>
        <span class="marker" [class]="'marker ' + position()?.toLowerCase()" [class.clamped]="g.clamped"
              [style.left.%]="g.marker"></span>
      </div>
    } @else {
      <span class="muted">No band</span>
    }
  `,
  styles: `
    :host { display: block; min-width: 96px; }
    .track { position: relative; height: 18px; }
    .track::before {
      content: ''; position: absolute; inset: 8px 0; border-radius: 2px;
      background: color-mix(in srgb, var(--pa-muted) 18%, transparent);
    }
    .band {
      position: absolute; top: 6px; height: 6px; border-radius: 3px;
      background: color-mix(in srgb, var(--pa-within) 35%, transparent);
    }
    .mid { position: absolute; top: 3px; width: 1.5px; height: 12px; margin-left: -0.75px; background: var(--pa-muted); }
    .marker {
      position: absolute; top: 4px; width: 10px; height: 10px; margin-left: -5px; border-radius: 50%;
      border: 2px solid var(--pa-surface); box-sizing: border-box; transition: left 300ms ease;
    }
    .marker.below { background: var(--pa-below); }
    .marker.within { background: var(--pa-within); }
    .marker.above { background: var(--pa-above); }
    /* Pinned to the edge: the real value lies further out. */
    .marker.clamped { box-shadow: 0 0 0 3px color-mix(in srgb, var(--pa-text) 25%, transparent); }
  `,
})
export class BandBar {
  readonly salary = input.required<Money>();
  readonly band = input<PayBand | null>(null);
  readonly position = input<BandPosition | null>(null);

  protected readonly geometry = computed(() => {
    const band = this.band();
    return band ? bandBarGeometry(this.salary().minorUnits, band) : null;
  });

  protected readonly label = computed(() => {
    const band = this.band();
    const position = this.position();
    if (!band) {
      return 'No pay band';
    }
    return `${formatMoney(this.salary())}, band ${formatMoney(band.min)} to ${formatMoney(band.max)}`
      + (position ? `, ${POSITION_TEXT[position]}` : '');
  });
}
