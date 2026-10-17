import { distributionOption, mapOption } from '../../features/insights/chart-options';
import { timelineOption } from '../../features/employees/detail/timeline-option';
import { echarts } from './echarts';
import { ChartPalette } from './palette';

/**
 * Modular ECharts silently draws nothing (and only logs to the console) when a series type is used without
 * being registered. Checks ECharts' own registry, so it can't drift from the echarts.use(...) call.
 */
describe('registered ECharts series', () => {
  const palette = {} as ChartPalette;
  const money = { minorUnits: 100, currency: 'USD', amount: '1.00' };
  const options = [
    mapOption([], null, palette),
    distributionOption({ asOf: '', currency: 'USD', headcount: 0, buckets: [], stats: null }, palette),
    timelineOption([{ id: 1, amount: money, effectiveDate: '2024-01-01', reason: 'HIRE', note: null, status: 'CURRENT', changePercent: null }], palette),
  ];

  const registry = (echarts as unknown as { ChartView: { getClass: (type: string) => unknown } }).ChartView;

  it('registers every series type the option builders use', () => {
    const used = new Set(options.flatMap((o) => (o['series'] as { type: string }[]).map((s) => s.type)));
    expect(used.size).toBeGreaterThan(2);
    expect([...used].filter((type) => !registry.getClass(type))).toEqual([]);
  });

  it('would notice an unregistered type', () => {
    expect(registry.getClass('pie')).toBeFalsy();
  });
});
