import { DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { BandPosition } from '../core/api/models';

/** Compa-ratio pill (salary ÷ band midpoint), coloured by band position. */
@Component({
  selector: 'pa-compa-badge',
  imports: [DecimalPipe],
  template: `
    @if (compa() !== null) {
      <span class="pill num" [class]="'pill num ' + (position()?.toLowerCase() ?? '')" [attr.aria-label]="label()"
            [title]="label()">{{ compa() | number: '1.2-2' }}</span>
    } @else {
      <span class="muted">–</span>
    }
  `,
  styles: `
    .pill {
      display: inline-block; min-width: 44px; padding: 2px 8px; border-radius: 999px; text-align: center;
      font-size: 12px; font-weight: 600; border: 1px solid transparent;
    }
    .below { color: var(--pa-below); background: color-mix(in srgb, var(--pa-below) 14%, transparent); }
    .within { color: var(--pa-within); background: color-mix(in srgb, var(--pa-within) 12%, transparent); }
    .above { color: var(--pa-above); background: color-mix(in srgb, var(--pa-above) 14%, transparent); }
  `,
})
export class CompaBadge {
  readonly compa = input<number | null>(null);
  readonly position = input<BandPosition | null>(null);

  protected readonly label = computed(() => {
    const compa = this.compa();
    if (compa === null) {
      return 'No compa-ratio';
    }
    const percent = Math.round((compa - 1) * 100);
    const relative = percent === 0 ? 'at band midpoint' : `${Math.abs(percent)}% ${percent < 0 ? 'below' : 'above'} band midpoint`;
    return `Compa-ratio ${compa.toFixed(2)}: ${relative}`;
  });
}
