// Modular ECharts: only what we draw is bundled (and only in the lazy insights chunk).
import { BarChart, EffectScatterChart, ScatterChart } from 'echarts/charts';
import { GeoComponent, GridComponent, TooltipComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([BarChart, ScatterChart, EffectScatterChart, GeoComponent, GridComponent, TooltipComponent, CanvasRenderer]);

export { echarts };
export type { EChartsCoreOption } from 'echarts/core';
