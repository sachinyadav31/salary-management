import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, shareReplay } from 'rxjs';
import { feature } from 'topojson-client';
import type { GeometryCollection, Topology } from 'topojson-specification';
import { echarts } from './echarts';

export const WORLD_MAP = 'world';

/**
 * Loads world outlines (world-atlas 110m TopoJSON, a static asset, not bundled JS), converts them to GeoJSON
 * and registers them with ECharts once per session.
 */
@Injectable({ providedIn: 'root' })
export class WorldMapLoader {
  private readonly http = inject(HttpClient);

  readonly ready$: Observable<string> = this.http.get<Topology>('/maps/countries-110m.json').pipe(
    map((topology) => {
      const countries = feature(topology, topology.objects['countries'] as GeometryCollection);
      echarts.registerMap(WORLD_MAP, countries as Parameters<typeof echarts.registerMap>[1]);
      return WORLD_MAP;
    }),
    shareReplay(1),
  );
}
