import { BandSplit, CountryInsight, Distribution } from '../../core/api/models';
import type { EChartsCoreOption } from '../../shared/charts/echarts';
import { ChartPalette } from '../../shared/charts/palette';
import { WORLD_MAP } from '../../shared/charts/world-map';
import { formatMoney } from '../../shared/money';

/** Approximate centroids [longitude, latitude]. Bubbles also show countries too small for the 110m outlines (SG). */
export const COUNTRY_COORDINATES: Record<string, [number, number]> = {
  US: [-98.5, 39.8],
  CA: [-106.3, 56.1],
  GB: [-1.8, 52.8],
  DE: [10.4, 51.2],
  IN: [78.9, 22.6],
  SG: [103.8, 1.35],
  JP: [138.3, 36.2],
  AU: [134.0, -25.3],
};

/** world-atlas names for tinting the countries ACME operates in. */
const MAP_REGION_NAMES: Record<string, string> = {
  US: 'United States of America',
  CA: 'Canada',
  GB: 'United Kingdom',
  DE: 'Germany',
  IN: 'India',
  JP: 'Japan',
  AU: 'Australia',
};

/** Share of people paid within their band: the country's "band health". */
export const HEALTHY_WITHIN_PERCENT = 87;
export const WATCH_WITHIN_PERCENT = 85;

export type BandHealth = 'healthy' | 'watch' | 'concern';

export function bandHealth(withinPercent: number | null): BandHealth {
  if (withinPercent === null || withinPercent >= HEALTHY_WITHIN_PERCENT) return 'healthy';
  return withinPercent >= WATCH_WITHIN_PERCENT ? 'watch' : 'concern';
}

const MIN_BUBBLE = 16;
const MAX_BUBBLE = 64;

/** Bubble diameter with area proportional to payroll (sqrt scale), so the eye compares areas fairly. */
export function bubbleSize(payrollMinor: number, maxPayrollMinor: number): number {
  if (maxPayrollMinor <= 0) return MIN_BUBBLE;
  return MIN_BUBBLE + (MAX_BUBBLE - MIN_BUBBLE) * Math.sqrt(payrollMinor / maxPayrollMinor);
}

/** On equal remainders, the spare point goes to the main category first. */
const TIE_BREAK_ORDER = ['within', 'below', 'above'] as const;

/** Below / within / above as whole percentages that always add up to 100 (largest remainder method). */
export function bandSplitPercents(bands: BandSplit): { below: number; within: number; above: number } {
  const total = bands.below + bands.within + bands.above;
  if (total === 0) return { below: 0, within: 0, above: 0 };
  const raw = { below: (bands.below * 100) / total, within: (bands.within * 100) / total, above: (bands.above * 100) / total };
  const floors = { below: Math.floor(raw.below), within: Math.floor(raw.within), above: Math.floor(raw.above) };
  let remainder = 100 - floors.below - floors.within - floors.above;
  [...TIE_BREAK_ORDER]
    .sort((a, b) => (raw[b] - floors[b]) - (raw[a] - floors[a]) || TIE_BREAK_ORDER.indexOf(a) - TIE_BREAK_ORDER.indexOf(b))
    .forEach((key) => {
      if (remainder > 0) {
        floors[key]++;
        remainder--;
      }
    });
  return floors;
}

export function mapOption(countries: CountryInsight[], selected: string | null, palette: ChartPalette): EChartsCoreOption {
  const maxPayroll = Math.max(0, ...countries.map((c) => c.payrollUsd.minorUnits));
  const healthColor: Record<BandHealth, string> = { healthy: palette.within, watch: palette.watch, concern: palette.below };

  const point = (c: CountryInsight) => ({
    name: c.countryCode,
    value: [...(COUNTRY_COORDINATES[c.countryCode] ?? [0, 0]), c.payrollUsd.minorUnits],
    symbolSize: bubbleSize(c.payrollUsd.minorUnits, maxPayroll),
    itemStyle: {
      color: healthColor[bandHealth(c.bands.withinPercent)],
      opacity: selected && selected !== c.countryCode ? 0.45 : 0.9,
      borderColor: selected === c.countryCode ? palette.text : 'transparent',
      borderWidth: 2,
    },
    tooltip: {
      formatter: [
        `<b>${c.countryName}</b>`,
        `${c.headcount.toLocaleString('en-US')} people · ${formatMoney(c.payrollUsd, 'compact')} payroll`,
        `Median ${formatMoney(c.local.median)} (${formatMoney(c.usd.median)})`,
        `${c.bands.withinPercent ?? '–'}% within band · ${c.bands.below} below · ${c.bands.above} above`,
      ].join('<br/>'),
    },
  });

  return {
    animationDurationUpdate: 400,
    tooltip: {
      trigger: 'item',
      backgroundColor: palette.surface,
      borderColor: palette.border,
      textStyle: { color: palette.text, fontSize: 12 },
    },
    geo: {
      map: WORLD_MAP,
      roam: false,
      silent: true,
      left: 0,
      right: 0,
      top: 10,
      bottom: 0,
      boundingCoords: [[-170, 75], [180, -50]],
      itemStyle: { areaColor: palette.surface2, borderColor: palette.border, borderWidth: 0.6 },
      regions: Object.entries(MAP_REGION_NAMES).map(([code, name]) => ({
        name,
        itemStyle: {
          areaColor: code === selected ? palette.accent : palette.accent,
          opacity: code === selected ? 0.35 : 0.14,
        },
      })),
    },
    series: [
      {
        name: 'countries',
        type: 'scatter',
        coordinateSystem: 'geo',
        cursor: 'pointer',
        data: countries.filter((c) => c.countryCode !== selected).map(point),
      },
      {
        name: 'selected',
        type: 'effectScatter',
        coordinateSystem: 'geo',
        cursor: 'pointer',
        rippleEffect: { scale: 2.2, brushType: 'stroke' },
        data: countries.filter((c) => c.countryCode === selected).map(point),
      },
    ],
  };
}

export function distributionOption(distribution: Distribution, palette: ChartPalette): EChartsCoreOption {
  const labels = distribution.buckets.map((b) => formatMoney(b.from, 'compact'));
  return {
    animationDuration: 600,
    grid: { left: 8, right: 8, top: 16, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: palette.surface,
      borderColor: palette.border,
      textStyle: { color: palette.text, fontSize: 12 },
      formatter: (items: { dataIndex: number }[]) => {
        const bucket = distribution.buckets[items[0].dataIndex];
        return `${formatMoney(bucket.from)} – ${formatMoney(bucket.to)}<br/><b>${bucket.count.toLocaleString('en-US')}</b> people`;
      },
    },
    xAxis: {
      type: 'category',
      data: labels,
      axisLine: { lineStyle: { color: palette.border } },
      axisTick: { show: false },
      axisLabel: { color: palette.muted, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: palette.border, type: 'dashed' } },
      axisLabel: { color: palette.muted, fontSize: 11 },
    },
    series: [
      {
        name: 'people',
        type: 'bar',
        barCategoryGap: '18%',
        data: distribution.buckets.map((b) => b.count),
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: palette.accent }, { offset: 1, color: palette.accent2 }],
          },
        },
      },
    ],
  };
}
