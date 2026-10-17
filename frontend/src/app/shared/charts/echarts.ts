// Modular ECharts: only what we draw is bundled (and only in lazy chunks that use charts).
// Every series type an option builder emits must be registered here; registered-series.spec.ts enforces it.
import { BarChart, EffectScatterChart, LineChart, ScatterChart } from 'echarts/charts';
import { GeoComponent, GridComponent, TooltipComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([BarChart, LineChart, ScatterChart, EffectScatterChart, GeoComponent, GridComponent, TooltipComponent, CanvasRenderer]);

export { echarts };
export type { EChartsCoreOption } from 'echarts/core';
