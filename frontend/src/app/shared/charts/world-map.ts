import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Feature, FeatureCollection, Geometry, Position } from 'geojson';
import { Observable, map, shareReplay } from 'rxjs';
import { feature } from 'topojson-client';
import type { GeometryCollection, Topology } from 'topojson-specification';
import { echarts } from './echarts';

export const WORLD_MAP = 'world';

/** Not useful for a payroll map and would waste vertical space. */
const EXCLUDED_COUNTRIES = new Set(['Antarctica']);

/**
 * A ring that crosses the ±180° meridian jumps from +180 to −180 between neighbouring points; a flat
 * renderer draws that jump as a stripe across the whole map. Shifting the western half by +360° keeps
 * the shape continuous (it just extends past the right edge).
 */
export function unwrapAntimeridian(ring: Position[]): Position[] {
  const crosses = ring.some((point, i) => i > 0 && Math.abs(point[0] - ring[i - 1][0]) > 180);
  return crosses ? ring.map(([lon, lat]) => [lon < 0 ? lon + 360 : lon, lat]) : ring;
}

export function prepareWorld(collection: FeatureCollection): FeatureCollection {
  const fixGeometry = (geometry: Geometry): Geometry => {
    switch (geometry.type) {
      case 'Polygon':
        return { ...geometry, coordinates: geometry.coordinates.map(unwrapAntimeridian) };
      case 'MultiPolygon':
        return { ...geometry, coordinates: geometry.coordinates.map((polygon) => polygon.map(unwrapAntimeridian)) };
      default:
        return geometry;
    }
  };
  return {
    ...collection,
    features: collection.features
      .filter((f) => !EXCLUDED_COUNTRIES.has(String(f.properties?.['name'])))
      .map((f): Feature => ({ ...f, geometry: fixGeometry(f.geometry) })),
  };
}

/**
 * Loads world outlines (world-atlas 110m TopoJSON, a static asset, not bundled JS), converts them to GeoJSON
 * and registers them with ECharts once per session.
 */
@Injectable({ providedIn: 'root' })
export class WorldMapLoader {
  private readonly http = inject(HttpClient);

  readonly ready$: Observable<string> = this.http.get<Topology>('/maps/countries-110m.json').pipe(
    map((topology) => {
      const countries = feature(topology, topology.objects['countries'] as GeometryCollection) as FeatureCollection;
      echarts.registerMap(WORLD_MAP, prepareWorld(countries) as Parameters<typeof echarts.registerMap>[1]);
      return WORLD_MAP;
    }),
    shareReplay(1),
  );
}
