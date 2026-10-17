import { SalaryHistoryItem } from '../../../core/api/models';
import type { EChartsCoreOption } from '../../../shared/charts/echarts';
import { ChartPalette } from '../../../shared/charts/palette';
import { formatMoney } from '../../../shared/money';

const REASON_LABEL: Record<SalaryHistoryItem['reason'], string> = {
  HIRE: 'Hire',
  PROMOTION: 'Promotion',
  ANNUAL_REVIEW: 'Annual review',
  MARKET_ADJUSTMENT: 'Market adjustment',
  CORRECTION: 'Correction',
};

export function reasonLabel(reason: SalaryHistoryItem['reason']): string {
  return REASON_LABEL[reason];
}

/**
 * Salary over time as a step line: solid up to the salary in effect today, dashed into scheduled changes.
 * `history` arrives newest first (as the API returns it).
 */
export function timelineOption(history: SalaryHistoryItem[], palette: ChartPalette): EChartsCoreOption {
  const ordered = [...history].reverse();
  const point = (h: SalaryHistoryItem) => ({
    value: [h.effectiveDate, Number(h.amount.amount)],
    item: h,
    symbolSize: h.status === 'CURRENT' ? 12 : 8,
    itemStyle: {
      color: h.status === 'SCHEDULED' ? palette.surface : palette.accent,
      borderColor: palette.accent,
      borderWidth: 2,
    },
  });
  const currentIndex = ordered.findIndex((h) => h.status === 'CURRENT');
  const effective = ordered.slice(0, currentIndex + 1);
  const scheduled = currentIndex >= 0 && currentIndex < ordered.length - 1 ? ordered.slice(currentIndex) : [];

  const line = (dashed: boolean) => ({
    type: 'line',
    step: 'end',
    smooth: false,
    lineStyle: { width: 2.5, color: palette.accent, type: dashed ? 'dashed' : 'solid' },
    areaStyle: dashed ? undefined : {
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [{ offset: 0, color: palette.accent }, { offset: 1, color: 'transparent' }],
      },
      opacity: 0.18,
    },
  });

  return {
    animationDuration: 500,
    grid: { left: 8, right: 16, top: 16, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'item',
      backgroundColor: palette.surface,
      borderColor: palette.border,
      textStyle: { color: palette.text, fontSize: 12 },
      formatter: (p: { data: { item: SalaryHistoryItem } }) => {
        const h = p.data.item;
        const change = h.changePercent === null ? '' : ` (${h.changePercent > 0 ? '+' : ''}${h.changePercent}%)`;
        return `${h.effectiveDate}${h.status === 'SCHEDULED' ? ' · scheduled' : ''}<br/><b>${formatMoney(h.amount)}</b>${change}<br/>${reasonLabel(h.reason)}`;
      },
    },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: palette.border } },
      axisLabel: { color: palette.muted, fontSize: 11 },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      scale: true,
      splitLine: { lineStyle: { color: palette.border, type: 'dashed' } },
      axisLabel: {
        color: palette.muted,
        fontSize: 11,
        formatter: (v: number) => formatMoney({ minorUnits: 0, currency: history[0]?.amount.currency ?? 'USD', amount: String(v) }, 'compact'),
      },
    },
    series: [
      { name: 'effective', ...line(false), data: effective.map(point) },
      { name: 'scheduled', ...line(true), data: scheduled.map(point) },
    ],
  };
}
