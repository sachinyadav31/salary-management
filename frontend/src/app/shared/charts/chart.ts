import { Component, DestroyRef, ElementRef, NgZone, afterNextRender, effect, inject, input, output } from '@angular/core';
import { EChartsCoreOption, echarts } from './echarts';

/** Data of a clicked chart element (series item). */
export interface ChartClick {
  name: string;
  seriesName?: string;
  data: unknown;
}

/**
 * Thin ECharts host. Runs outside Angular's zone so pointer events over the canvas don't trigger app-wide
 * change detection; only clicks re-enter the zone. Resizes with its container and disposes on destroy.
 */
@Component({
  selector: 'pa-chart',
  template: '',
  styles: ':host { display: block; width: 100%; height: 100%; }',
  host: { role: 'img', '[attr.aria-label]': 'ariaLabel()' },
})
export class Chart {
  readonly option = input.required<EChartsCoreOption>();
  readonly ariaLabel = input<string>('Chart');
  readonly chartClick = output<ChartClick>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);
  private instance: ReturnType<typeof echarts.init> | null = null;

  constructor() {
    afterNextRender(() => {
      this.zone.runOutsideAngular(() => {
        const instance = echarts.init(this.host, null, { renderer: 'canvas' });
        instance.on('click', (event) =>
          this.zone.run(() => this.chartClick.emit({ name: event.name, seriesName: event.seriesName, data: event.data })),
        );
        instance.setOption(this.option());
        const resize = new ResizeObserver(() => instance.resize());
        resize.observe(this.host);
        this.destroyRef.onDestroy(() => {
          resize.disconnect();
          instance.dispose();
        });
        this.instance = instance;
      });
    });

    // Later option changes (data, selection, theme) replace the chart's option.
    effect(() => {
      const option = this.option();
      this.zone.runOutsideAngular(() => this.instance?.setOption(option, { notMerge: true }));
    });
  }
}
