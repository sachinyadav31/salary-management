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
 * Salary over time as a step line: solid up to today (so even a single hire record reads as "paid X since"),
 * dashed into scheduled changes. `history` arrives newest first (as the API returns it).
 */
export function timelineOption(history: SalaryHistoryItem[], palette: ChartPalette,
                               today: string = new Date().toISOString().slice(0, 10)): EChartsCoreOption {
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
  const effective: object[] = ordered.slice(0, currentIndex + 1).map(point);
  const current = ordered[currentIndex];
  if (current && current.effectiveDate < today) {
    // Carry the current salary forward to today, without a marker.
    effective.push({ value: [today, Number(current.amount.amount)], symbol: 'none', item: current });
  }
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
      splitNumber: 4,
      splitLine: { lineStyle: { color: palette.border, type: 'dashed' } },
      axisLabel: { color: palette.muted, fontSize: 11, formatter: axisFormatter(history[0]?.amount.currency ?? 'USD') },
    },
    series: [
      { name: 'effective', ...line(false), data: effective },
      { name: 'scheduled', ...line(true), data: scheduled.map(point) },
    ],
  };
}

/** Axis ticks can be close together (¥6.55M vs ¥6.6M), so use 3 significant digits rather than 1 decimal. */
export function axisFormatter(currency: string): (value: number) => string {
  const format = new Intl.NumberFormat('en-US', {
    style: 'currency', currency, notation: 'compact', maximumSignificantDigits: 3,
  });
  return (value: number) => format.format(value);
}
