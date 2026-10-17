import { CountryInsight, Distribution, Money } from '../../core/api/models';
import { ChartPalette } from '../../shared/charts/palette';
import {
  COUNTRY_COORDINATES, bandHealth, bandSplitPercents, bubbleSize, distributionOption, mapOption,
} from './chart-options';

const PALETTE: ChartPalette = {
  text: 'text', muted: 'muted', border: 'border', surface: 'surface', surface2: 'surface2', accent: 'accent',
  accent2: 'accent2', below: 'coral', within: 'teal', above: 'violet', watch: 'amber',
};

const usd = (minorUnits: number): Money => ({ minorUnits, currency: 'USD', amount: (minorUnits / 100).toFixed(2) });
const stats = { min: usd(1), max: usd(1), average: usd(1), median: usd(5_000_000) };

function country(code: string, payrollMinor: number, withinPercent: number): CountryInsight {
  return {
    countryCode: code, countryName: code, currency: 'USD', headcount: 100, payroll: usd(payrollMinor),
    payrollUsd: usd(payrollMinor), local: stats, usd: stats,
    bands: { below: 5, within: 90, above: 5, withinPercent, averageCompaRatio: 1 },
    variablePayTargetUsd: usd(0), bonusesUsdLast12Months: usd(0),
  };
}

type Point = { name: string; value: number[]; symbolSize: number; itemStyle: { color: string; opacity: number } };
type Series = { data: Point[] };

describe('map option', () => {
  const countries = [country('US', 400_00, 88), country('JP', 100_00, 84.5), country('SG', 25_00, 86)];

  it('places every country at its coordinates, including ones too small for the outline map', () => {
    const series = mapOption(countries, null, PALETTE)['series'] as Series[];
    const sg = series[0].data.find((p) => p.name === 'SG')!;
    expect(sg.value.slice(0, 2)).toEqual(COUNTRY_COORDINATES['SG']);
    expect(Object.keys(COUNTRY_COORDINATES).sort()).toEqual(['AU', 'CA', 'DE', 'GB', 'IN', 'JP', 'SG', 'US']);
  });

  it('sizes bubbles by payroll area and colours them by band health', () => {
    const points = (mapOption(countries, null, PALETTE)['series'] as Series[])[0].data;
    const [us, jp, sg] = ['US', 'JP', 'SG'].map((code) => points.find((p) => p.name === code)!);

    expect(us.symbolSize).toBe(64);
    expect(jp.symbolSize).toBeLessThan(us.symbolSize);
    expect(us.itemStyle.color).toBe('teal');
    expect(sg.itemStyle.color).toBe('amber');
    expect(jp.itemStyle.color).toBe('coral');
  });

  it('moves the selected country to the rippling series and dims the rest', () => {
    const [others, selected] = mapOption(countries, 'JP', PALETTE)['series'] as Series[];

    expect(selected.data.map((p) => p.name)).toEqual(['JP']);
    expect(others.data.map((p) => p.name)).toEqual(['US', 'SG']);
    expect(others.data[0].itemStyle.opacity).toBeLessThan(selected.data[0].itemStyle.opacity);
  });
});

describe('distribution option', () => {
  it('labels bars with compact local amounts and plots counts', () => {
    const inr = (minorUnits: number): Money => ({ minorUnits, currency: 'INR', amount: (minorUnits / 100).toFixed(2) });
    const distribution: Distribution = {
      asOf: '', currency: 'INR', headcount: 3, stats: null,
      buckets: [{ from: inr(100_000_000), to: inr(200_000_000), count: 2 }, { from: inr(200_000_000), to: inr(300_000_000), count: 1 }],
    };

    const option = distributionOption(distribution, PALETTE);

    expect((option['xAxis'] as { data: string[] }).data).toEqual(['₹10L', '₹20L']);
    expect((option['series'] as { data: number[] }[])[0].data).toEqual([2, 1]);
  });
});

describe('helpers', () => {
  it('grades band health with documented thresholds', () => {
    expect(bandHealth(90)).toBe('healthy');
    expect(bandHealth(87)).toBe('healthy');
    expect(bandHealth(86.9)).toBe('watch');
    expect(bandHealth(85)).toBe('watch');
    expect(bandHealth(84.9)).toBe('concern');
  });

  it('scales bubble area, not diameter, with payroll', () => {
    expect(bubbleSize(0, 100)).toBe(16);
    expect(bubbleSize(100, 100)).toBe(64);
    expect(bubbleSize(25, 100)).toBe(40); // sqrt(0.25) = 0.5 of the range
  });

  it('splits band counts into percentages that always total 100', () => {
    expect(bandSplitPercents({ below: 1, within: 1, above: 1, withinPercent: null, averageCompaRatio: null }))
      .toEqual({ below: 33, within: 34, above: 33 });
    expect(bandSplitPercents({ below: 519, within: 8696, above: 785, withinPercent: null, averageCompaRatio: null }))
      .toEqual({ below: 5, within: 87, above: 8 });
    expect(bandSplitPercents({ below: 0, within: 0, above: 0, withinPercent: null, averageCompaRatio: null }))
      .toEqual({ below: 0, within: 0, above: 0 });
  });
});

describe('world map preparation', () => {
  it('unwraps rings crossing the antimeridian so they do not streak across the map', async () => {
    const { unwrapAntimeridian, prepareWorld } = await import('../../shared/charts/world-map');

    expect(unwrapAntimeridian([[179, 65], [-179, 66], [-178, 64], [179, 65]]))
      .toEqual([[179, 65], [181, 66], [182, 64], [179, 65]]);
    expect(unwrapAntimeridian([[10, 50], [12, 51], [10, 50]])).toEqual([[10, 50], [12, 51], [10, 50]]);

    const world = prepareWorld({
      type: 'FeatureCollection',
      features: [
        { type: 'Feature', properties: { name: 'Antarctica' }, geometry: { type: 'Polygon', coordinates: [] } },
        { type: 'Feature', properties: { name: 'India' }, geometry: { type: 'Polygon', coordinates: [] } },
      ],
    });
    expect(world.features.map((f) => f.properties?.['name'])).toEqual(['India']);
  });
});
