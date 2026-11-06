import * as echarts from 'echarts/core';
import {
  BarChart,
  BoxplotChart,
  FunnelChart,
  GaugeChart,
  HeatmapChart,
  LineChart,
  PieChart,
  RadarChart,
  SankeyChart,
  ScatterChart,
  SunburstChart,
  TreeChart,
  TreemapChart,
} from 'echarts/charts';
import {
  CalendarComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  RadarComponent,
  TooltipComponent,
  VisualMapComponent,
} from 'echarts/components';
import { SVGRenderer } from 'echarts/renderers';

// Solo se importa lo que se usa: ECharts completo pasa del limite de ng build.
echarts.use([
  BarChart,
  BoxplotChart,
  FunnelChart,
  GaugeChart,
  HeatmapChart,
  LineChart,
  PieChart,
  RadarChart,
  SankeyChart,
  ScatterChart,
  SunburstChart,
  TreeChart,
  TreemapChart,
  CalendarComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  RadarComponent,
  TooltipComponent,
  VisualMapComponent,
  SVGRenderer,
]);

export const PASTEL = ['#A8D8EA', '#FFD6A5', '#CAFFBF', '#FFADAD', '#BDB2FF', '#FDFFB6', '#9BF6FF', '#FFC6FF', '#D0F4DE', '#E4C1F9'];
export const TINTA = '#2F3640';
export const SEMAFORO = { verde: '#B5EAD7', amarillo: '#FFF1B6', rojo: '#FFB7B2' };
export const ESCALA = ['#EEF7FB', '#7FB7D4'];
export const BORDE = { borderColor: '#6E7B8B', borderWidth: 1 };

const EJE = {
  axisLine: { lineStyle: { color: '#CBB9DE' } },
  axisLabel: { color: TINTA, fontSize: 11 },
  splitLine: { lineStyle: { color: '#F1EAF9' } },
};

echarts.registerTheme('soltec', {
  color: PASTEL,
  textStyle: { fontFamily: 'Poppins, sans-serif', color: TINTA },
  title: { textStyle: { color: TINTA } },
  legend: { textStyle: { color: TINTA, fontSize: 11 } },
  tooltip: { textStyle: { fontSize: 12 } },
  categoryAxis: EJE,
  valueAxis: EJE,
  timeAxis: EJE,
});

export { echarts };
