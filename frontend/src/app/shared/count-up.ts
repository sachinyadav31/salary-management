import { Component, ElementRef, NgZone, effect, inject, input, DestroyRef } from '@angular/core';

const DURATION_MS = 900;

/** Animates a number from its previous value (0 at first) with ease-out; instant if reduce-motion is on. */
@Component({
  selector: 'pa-count-up',
  template: '',
  host: { class: 'num' },
})
export class CountUp {
  readonly value = input.required<number>();
  readonly format = input<(value: number) => string>((value) => Math.round(value).toLocaleString('en-US'));

  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly zone = inject(NgZone);
  private current = 0;
  private frame = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.frame));

    effect(() => {
      const target = this.value();
      const format = this.format();
      cancelAnimationFrame(this.frame);
      const reduceMotion = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
      if (reduceMotion || typeof requestAnimationFrame !== 'function') {
        this.render(target, format);
        return;
      }
      const from = this.current;
      const start = performance.now();
      this.zone.runOutsideAngular(() => {
        const step = (now: number) => {
          const t = Math.min(1, (now - start) / DURATION_MS);
          const eased = 1 - Math.pow(1 - t, 3);
          this.render(t === 1 ? target : from + (target - from) * eased, format);
          if (t < 1) this.frame = requestAnimationFrame(step);
        };
        this.frame = requestAnimationFrame(step);
      });
    });
  }

  private render(value: number, format: (value: number) => string): void {
    this.current = value;
    this.element.textContent = format(value);
  }
}
