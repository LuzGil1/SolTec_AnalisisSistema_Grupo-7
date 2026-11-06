import { BORDE, ESCALA, PASTEL, SEMAFORO, TINTA } from './tema-echarts';

// Convierte los datos que devuelve el motor en la configuracion de ECharts.
// Cada funcion recibe solo su forma de datos; los colores salen del tema.

export interface Contexto {
  unidad: string;
  nombre: string;
}

export type Constructor = (datos: any, contexto: Contexto) => any;

const TIEMPO_BIN = [0, 8, 16, 24, 48, 72, 120, 168];
const MARGEN_AMARILLO = 15;

// La meta viene de la tabla parametro. Sin meta, no se dibuja el semaforo.
function colorSegunMeta(valor: number, meta: number | null): string {
  if (meta === null) {
    return ESCALA[1];
  }
  return valor >= meta ? SEMAFORO.verde : valor >= meta - MARGEN_AMARILLO ? SEMAFORO.amarillo : SEMAFORO.rojo;
}

function numero(valor: any): number | null {
  return valor === null || valor === undefined ? null : Number(valor);
}

function columnas(filas: any[], campoCategoria: string, campoValor: string): { nombres: string[]; valores: (number | null)[] } {
  const nombres: string[] = [];
  const valores: (number | null)[] = [];
  for (let i = 0; i < filas.length; i++) {
    nombres.push(filas[i][campoCategoria]);
    valores.push(numero(filas[i][campoValor]));
  }
  return { nombres, valores };
}

// Pasa [{categoria, serie, valor}] a una matriz: una lista por serie
function pivotear(filas: any[]): { categorias: string[]; series: string[]; matriz: (number | null)[][] } {
  const categorias: string[] = [];
  const series: string[] = [];
  for (let i = 0; i < filas.length; i++) {
    if (categorias.indexOf(filas[i].categoria) === -1) {
      categorias.push(filas[i].categoria);
    }
    if (series.indexOf(filas[i].serie) === -1) {
      series.push(filas[i].serie);
    }
  }
  const matriz: (number | null)[][] = [];
  for (let s = 0; s < series.length; s++) {
    const fila: (number | null)[] = [];
    for (let c = 0; c < categorias.length; c++) {
      fila.push(0);
    }
    matriz.push(fila);
  }
  for (let i = 0; i < filas.length; i++) {
    matriz[series.indexOf(filas[i].serie)][categorias.indexOf(filas[i].categoria)] = numero(filas[i].valor);
  }
  return { categorias, series, matriz };
}

function colorPorPosicion(parametros: any): string {
  return PASTEL[parametros.dataIndex % PASTEL.length];
}

function colorDeSerie(indice: number): string {
  return PASTEL[indice % PASTEL.length];
}

function barrasVerticales(datos: any[], contexto: Contexto): any {
  const c = columnas(datos, 'categoria', 'valor');
  return {
    grid: { left: 50, right: 24, top: 22, bottom: 74 },
    xAxis: { type: 'category', data: c.nombres, axisLabel: { rotate: c.nombres.length > 4 ? 28 : 0, fontSize: 10, width: 96, overflow: 'truncate' } },
    yAxis: { type: 'value' },
    tooltip: { trigger: 'item', valueFormatter: (v: number) => `${v}${contexto.unidad}` },
    series: [{
      type: 'bar', data: c.valores,
      itemStyle: { ...BORDE, borderRadius: [5, 5, 0, 0], color: colorPorPosicion },
      label: { show: c.nombres.length <= 14, position: 'top', color: TINTA, fontSize: 11 },
    }],
  };
}

function barrasHorizontales(datos: any[], contexto: Contexto): any {
  const c = columnas(datos, 'categoria', 'valor');
  return {
    grid: { left: 150, right: 46, top: 12, bottom: 28 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: c.nombres, inverse: true, axisLabel: { width: 138, overflow: 'truncate' } },
    tooltip: { trigger: 'item', valueFormatter: (v: number) => `${v}${contexto.unidad}` },
    series: [{
      type: 'bar', data: c.valores,
      itemStyle: { ...BORDE, borderRadius: [0, 5, 5, 0], color: colorPorPosicion },
      label: { show: true, position: 'right', color: TINTA, fontSize: 11 },
    }],
  };
}

function barrasMultiples(datos: any[], apilar: string | undefined, enPorcentaje: boolean): any {
  const p = pivotear(datos);
  const series: any[] = [];
  const totales: number[] = [];
  for (let c = 0; c < p.categorias.length; c++) {
    let suma = 0;
    for (let s = 0; s < p.series.length; s++) {
      suma += p.matriz[s][c] ?? 0;
    }
    totales.push(suma);
  }
  for (let s = 0; s < p.series.length; s++) {
    const valores: (number | null)[] = [];
    for (let c = 0; c < p.categorias.length; c++) {
      const v = p.matriz[s][c];
      valores.push(enPorcentaje && totales[c] > 0 ? Math.round(((v ?? 0) / totales[c]) * 1000) / 10 : v);
    }
    series.push({ name: p.series[s], type: 'bar', stack: apilar, data: valores, itemStyle: { ...BORDE, color: colorDeSerie(s) } });
  }
  return {
    grid: { left: 50, right: 24, top: 48, bottom: 74 },
    legend: { top: 0, type: 'scroll' },
    xAxis: { type: 'category', data: p.categorias, axisLabel: { rotate: p.categorias.length > 4 ? 28 : 0, fontSize: 10, width: 96, overflow: 'truncate' } },
    yAxis: enPorcentaje ? { type: 'value', max: 100, axisLabel: { formatter: '{value} %' } } : { type: 'value' },
    tooltip: { trigger: 'axis' },
    series,
  };
}

function lollipop(datos: any[], contexto: Contexto): any {
  const c = columnas(datos, 'categoria', 'valor');
  return {
    grid: { left: 150, right: 40, top: 12, bottom: 28 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: c.nombres, inverse: true, axisLabel: { width: 138, overflow: 'truncate' } },
    tooltip: { trigger: 'item', valueFormatter: (v: number) => `${v}${contexto.unidad}` },
    series: [
      { type: 'bar', data: c.valores, barWidth: 3, itemStyle: { color: PASTEL[4] }, silent: true },
      { type: 'scatter', symbolSize: 20, data: c.valores.map((v, i) => [v, i]), itemStyle: { color: PASTEL[4], ...BORDE },
        label: { show: true, color: TINTA, fontSize: 11, formatter: (p: any) => p.value[0] } },
    ],
  };
}

function radar(datos: any[]): any {
  const filas: any[] = [];
  for (let i = 0; i < datos.length && filas.length < 4; i++) {
    if (datos[i].sla !== null && datos[i].horas !== null) {
      filas.push(datos[i]);
    }
  }
  let maxResueltas = 1;
  let maxCarga = 1;
  let maxScore = 1;
  let minHoras = Number.MAX_VALUE;
  for (let i = 0; i < filas.length; i++) {
    maxResueltas = Math.max(maxResueltas, Number(filas[i].resueltas));
    maxCarga = Math.max(maxCarga, Number(filas[i].carga));
    maxScore = Math.max(maxScore, Number(filas[i].score ?? 0));
    minHoras = Math.min(minHoras, Number(filas[i].horas));
  }
  const series: any[] = [];
  for (let i = 0; i < filas.length; i++) {
    const rapidez = Math.round((minHoras / Number(filas[i].horas)) * 100);
    series.push({
      name: filas[i].tecnico,
      value: [Number(filas[i].sla), Number(filas[i].resueltas), rapidez, Number(filas[i].carga), Number(filas[i].score ?? 0)],
      lineStyle: { width: 3, color: colorDeSerie(i) },
      itemStyle: { color: colorDeSerie(i), ...BORDE },
      areaStyle: { color: colorDeSerie(i), opacity: 0.25 },
    });
  }
  return {
    tooltip: {},
    legend: { bottom: 0, itemWidth: 12 },
    radar: {
      indicator: [
        { name: 'SLA', max: 100 },
        { name: 'Resueltas', max: maxResueltas },
        { name: 'Rapidez', max: 100 },
        { name: 'Carga', max: maxCarga },
        { name: 'Score', max: maxScore },
      ],
      axisName: { color: TINTA, fontSize: 11 },
      splitLine: { lineStyle: { color: '#EDE4F7' } },
      splitArea: { areaStyle: { color: ['#FDFBFF', '#F7F1FD'] } },
      axisLine: { lineStyle: { color: '#EDE4F7' } },
    },
    series: [{ type: 'radar', symbolSize: 6, data: series }],
  };
}

function pareto(datos: any[]): any {
  const ordenadas = [...datos].sort((a, b) => Number(b.valor) - Number(a.valor));
  const c = columnas(ordenadas, 'categoria', 'valor');
  let total = 0;
  for (let i = 0; i < c.valores.length; i++) {
    total += c.valores[i] ?? 0;
  }
  let acumulado = 0;
  const porcentajes: number[] = [];
  for (let i = 0; i < c.valores.length; i++) {
    acumulado += c.valores[i] ?? 0;
    porcentajes.push(total > 0 ? Math.round((acumulado / total) * 1000) / 10 : 0);
  }
  return {
    grid: { left: 50, right: 52, top: 40, bottom: 70 },
    legend: { top: 0 },
    xAxis: { type: 'category', data: c.nombres, axisLabel: { rotate: 22, fontSize: 10, width: 110, overflow: 'truncate' } },
    yAxis: [{ type: 'value' }, { type: 'value', max: 100, axisLabel: { formatter: '{value} %' } }],
    tooltip: { trigger: 'axis' },
    series: [
      { name: 'Solicitudes', type: 'bar', data: c.valores, itemStyle: { ...BORDE, color: PASTEL[0] } },
      { name: 'Acumulado', type: 'line', yAxisIndex: 1, data: porcentajes, symbolSize: 8,
        lineStyle: { width: 3, color: PASTEL[3] }, itemStyle: { color: PASTEL[3], ...BORDE } },
    ],
  };
}

function lineaSimple(datos: any[], contexto: Contexto, relleno: boolean, escalon: boolean): any {
  const c = columnas(datos, 'fecha', 'valor');
  return {
    grid: { left: 50, right: 24, top: 16, bottom: 44 },
    xAxis: { type: 'category', data: c.nombres, boundaryGap: false, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value' },
    tooltip: { trigger: 'axis', valueFormatter: (v: number) => `${v}${contexto.unidad}` },
    series: [{
      type: 'line', data: c.valores, smooth: !escalon, step: escalon ? 'end' : undefined, symbolSize: 7,
      lineStyle: { width: 3, color: ESCALA[1] }, itemStyle: { color: ESCALA[1], ...BORDE },
      areaStyle: relleno ? { color: PASTEL[8] } : undefined,
    }],
  };
}

function lineasMultiples(datos: any[], apilar: boolean): any {
  const p = pivotear(datos);
  const series: any[] = [];
  for (let s = 0; s < p.series.length; s++) {
    series.push({
      name: p.series[s], type: 'line', smooth: !apilar, symbolSize: 7, data: p.matriz[s],
      stack: apilar ? 'total' : undefined,
      lineStyle: { width: 3, color: colorDeSerie(s) }, itemStyle: { color: colorDeSerie(s), ...BORDE },
      areaStyle: apilar ? { color: colorDeSerie(s) } : undefined,
    });
  }
  return {
    grid: { left: 50, right: 28, top: 48, bottom: 44 },
    legend: { top: 0, type: 'scroll' },
    xAxis: { type: 'category', data: p.categorias, boundaryGap: false, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value' },
    tooltip: { trigger: 'axis' },
    series,
  };
}

function combinada(datos: any[]): any {
  const barras = columnas(datos, 'fecha', 'valorBarra');
  const lineas = columnas(datos, 'fecha', 'valorLinea');
  return {
    grid: { left: 50, right: 58, top: 40, bottom: 34 },
    legend: { top: 0 },
    xAxis: { type: 'category', data: barras.nombres, axisLabel: { fontSize: 10 } },
    yAxis: [{ type: 'value' }, { type: 'value', axisLabel: { formatter: '{value} h' } }],
    tooltip: { trigger: 'axis' },
    series: [
      { name: 'Recibidas', type: 'bar', data: barras.valores, itemStyle: { ...BORDE, borderRadius: [5, 5, 0, 0], color: PASTEL[0] } },
      { name: 'Horas promedio', type: 'line', yAxisIndex: 1, smooth: true, symbolSize: 8, data: lineas.valores, connectNulls: true,
        lineStyle: { width: 3, color: PASTEL[3] }, itemStyle: { color: PASTEL[3], ...BORDE } },
    ],
  };
}

function cascada(datos: any[]): any {
  const nombres: string[] = [];
  const bases: number[] = [];
  const valores: any[] = [];
  let nivel = 0;
  for (let i = 0; i < datos.length; i++) {
    const delta = Number(datos[i].delta);
    const extremo = i === 0 || i === datos.length - 1;
    nombres.push(datos[i].etapa);
    if (extremo) {
      bases.push(0);
      nivel = i === 0 ? delta : nivel;
    } else {
      nivel += delta;
      bases.push(nivel);
    }
    valores.push({ value: Math.abs(delta), itemStyle: { ...BORDE, color: extremo ? PASTEL[4] : PASTEL[3] } });
  }
  return {
    grid: { left: 50, right: 24, top: 22, bottom: 42 },
    xAxis: { type: 'category', data: nombres, axisLabel: { fontSize: 10, rotate: 14 } },
    yAxis: { type: 'value' },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (p: any[]) => `${p[1].name}: ${p[1].value}` },
    series: [
      { type: 'bar', stack: 'c', silent: true, itemStyle: { color: 'transparent' }, data: bases },
      { type: 'bar', stack: 'c', data: valores, label: { show: true, position: 'top', color: TINTA, fontSize: 11 } },
    ],
  };
}

function calendario(datos: any[]): any {
  const valores: any[] = [];
  let maximo = 1;
  for (let i = 0; i < datos.length; i++) {
    valores.push([datos[i].fecha, Number(datos[i].valor)]);
    maximo = Math.max(maximo, Number(datos[i].valor));
  }
  return {
    tooltip: {},
    visualMap: { min: 0, max: maximo, orient: 'horizontal', left: 'center', bottom: 0, calculable: true,
      inRange: { color: ESCALA }, textStyle: { color: TINTA, fontSize: 11 } },
    calendar: { top: 24, left: 48, right: 24, cellSize: ['auto', 34],
      range: [datos[0].fecha, datos[datos.length - 1].fecha],
      itemStyle: { borderColor: '#fff', borderWidth: 2, color: '#F7F3FC' },
      yearLabel: { show: false }, dayLabel: { color: TINTA, fontSize: 10, nameMap: ['D', 'L', 'M', 'M', 'J', 'V', 'S'] },
      monthLabel: { color: TINTA, fontSize: 11, nameMap: ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'] },
      splitLine: { show: false } },
    series: [{ type: 'heatmap', coordinateSystem: 'calendar', data: valores }],
  };
}

function sectores(datos: any[], radio: any, centro: any, inicio = 90, fin: number | 'auto' = 'auto', leyendaAbajo = false): any {
  const porciones: any[] = [];
  for (let i = 0; i < datos.length; i++) {
    if (Number(datos[i].valor) > 0) {
      porciones.push({ name: datos[i].categoria, value: Number(datos[i].valor) });
    }
  }
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d} %)' },
    legend: leyendaAbajo ? { bottom: 0, itemWidth: 12, type: 'scroll' }
      : { orient: 'vertical', right: 4, top: 'middle', itemWidth: 12, type: 'scroll' },
    series: [{
      type: 'pie', radius: radio, center: centro, startAngle: inicio, endAngle: fin, data: porciones,
      itemStyle: { borderColor: '#6E7B8B', borderWidth: 1 }, label: { show: false },
    }],
  };
}

function arbolTreemap(nodos: any[]): any[] {
  const salida: any[] = [];
  for (let i = 0; i < nodos.length; i++) {
    salida.push({ name: nodos[i].nombre, value: Number(nodos[i].valor), children: arbolTreemap(nodos[i].hijos ?? []) });
  }
  return salida;
}

function treemap(datos: any[]): any {
  return {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'treemap', roam: false, nodeClick: false, breadcrumb: { show: false },
      label: { color: TINTA, fontSize: 11 }, upperLabel: { show: true, height: 22, color: TINTA },
      visualDimension: 0, colorMappingBy: 'value', color: ESCALA,
      levels: [
        { itemStyle: { borderColor: '#fff', borderWidth: 3, gapWidth: 3 }, color: ESCALA },
        { itemStyle: { borderColor: '#fff', borderWidth: 1, gapWidth: 1 }, color: ESCALA },
      ],
      data: arbolTreemap(datos),
    }],
  };
}

function sunburst(datos: any[]): any {
  return {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'sunburst', radius: ['14%', '92%'], itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { color: TINTA, fontSize: 9, minAngle: 22, width: 62, overflow: 'truncate' }, data: arbolTreemap(datos),
    }],
  };
}

function arbol(datos: any[]): any {
  return {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'tree', data: [{ name: 'Especialidades', children: arbolTreemap(datos) }],
      top: 10, left: 110, bottom: 10, right: 130, symbolSize: 10, initialTreeDepth: 2,
      itemStyle: { color: PASTEL[4], ...BORDE }, lineStyle: { color: '#CBB9DE', width: 1.5 },
      label: { color: TINTA, fontSize: 11, position: 'left', verticalAlign: 'middle', align: 'right' },
      leaves: { label: { position: 'right', align: 'left' } },
    }],
  };
}

function waffle(datos: any[]): any {
  let total = 0;
  for (let i = 0; i < datos.length; i++) {
    total += Number(datos[i].valor);
  }
  const celdas: any[] = [];
  const nombres: string[] = [];
  for (let i = 0; i < datos.length; i++) {
    nombres.push(datos[i].categoria);
    const cuantas = i === datos.length - 1 ? 100 - celdas.length : Math.round((Number(datos[i].valor) / total) * 100);
    for (let k = 0; k < cuantas && celdas.length < 100; k++) {
      const posicion = celdas.length;
      celdas.push({
        value: [posicion % 10, Math.floor(posicion / 10)], nombre: datos[i].categoria, valor: datos[i].valor,
        itemStyle: { color: colorDeSerie(i), ...BORDE },
      });
    }
  }
  const series: any[] = [{ type: 'scatter', symbol: 'rect', symbolSize: 21, data: celdas }];
  for (let i = 0; i < nombres.length; i++) {
    series.push({ name: nombres[i], type: 'scatter', data: [], itemStyle: { color: colorDeSerie(i), ...BORDE } });
  }
  return {
    grid: { left: 60, right: 60, top: 12, bottom: 36 },
    legend: { bottom: 0, itemWidth: 12, data: nombres },
    xAxis: { type: 'category', data: ['', '', '', '', '', '', '', '', '', ''], show: false },
    yAxis: { type: 'category', data: ['', '', '', '', '', '', '', '', '', ''], show: false },
    tooltip: { formatter: (p: any) => `${p.data.nombre}: ${p.data.valor}` },
    series,
  };
}

function histograma(datos: any[]): any {
  const conteo: number[] = [];
  const etiquetas: string[] = [];
  for (let b = 0; b < TIEMPO_BIN.length; b++) {
    conteo.push(0);
    etiquetas.push(b === TIEMPO_BIN.length - 1 ? `${TIEMPO_BIN[b]}+` : `${TIEMPO_BIN[b]}-${TIEMPO_BIN[b + 1]}`);
  }
  for (let i = 0; i < datos.length; i++) {
    const horas = Number(datos[i]);
    let b = TIEMPO_BIN.length - 1;
    while (b > 0 && horas < TIEMPO_BIN[b]) {
      b--;
    }
    conteo[b]++;
  }
  return {
    grid: { left: 50, right: 24, top: 16, bottom: 42 },
    xAxis: { type: 'category', data: etiquetas, name: 'Horas', nameLocation: 'middle', nameGap: 26, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value' },
    tooltip: { trigger: 'axis' },
    series: [{ type: 'bar', barCategoryGap: '8%', data: conteo, itemStyle: { ...BORDE, color: PASTEL[4] } }],
  };
}

function cuantil(ordenados: number[], q: number): number {
  const posicion = (ordenados.length - 1) * q;
  const base = Math.floor(posicion);
  const siguiente = ordenados[base + 1] ?? ordenados[base];
  return Math.round((ordenados[base] + (siguiente - ordenados[base]) * (posicion - base)) * 10) / 10;
}

function boxplot(datos: any[]): any {
  const nombres: string[] = [];
  const cajas: number[][] = [];
  for (let i = 0; i < datos.length; i++) {
    const valores: number[] = [];
    for (let k = 0; k < datos[i].valores.length; k++) {
      valores.push(Number(datos[i].valores[k]));
    }
    valores.sort((a, b) => a - b);
    nombres.push(datos[i].categoria);
    cajas.push([valores[0], cuantil(valores, 0.25), cuantil(valores, 0.5), cuantil(valores, 0.75), valores[valores.length - 1]]);
  }
  return {
    grid: { left: 54, right: 24, top: 16, bottom: 62 },
    xAxis: { type: 'category', data: nombres, axisLabel: { rotate: 20, fontSize: 10 } },
    yAxis: { type: 'value', axisLabel: { formatter: '{value} h' } },
    tooltip: { trigger: 'item' },
    series: [{ type: 'boxplot', data: cajas, itemStyle: { color: PASTEL[2], borderColor: '#6E7B8B' } }],
  };
}

function dispersion(datos: any[], nombreX: string, nombreY: string, burbujas: boolean): any {
  const puntos: any[] = [];
  for (let i = 0; i < datos.length; i++) {
    if (datos[i].etiqueta === 'Sin técnico') {
      continue;
    }
    puntos.push({
      value: [Number(datos[i].x), Number(datos[i].y), Number(datos[i].radio), datos[i].etiqueta],
      itemStyle: { color: colorDeSerie(i), ...BORDE },
    });
  }
  return {
    grid: { left: 60, right: 28, top: 20, bottom: 46 },
    xAxis: { type: 'value', scale: true, name: nombreX, nameLocation: 'middle', nameGap: 26, nameTextStyle: { color: TINTA, fontSize: 11 } },
    yAxis: { type: 'value', scale: true, name: nombreY, nameLocation: 'middle', nameGap: 40, nameTextStyle: { color: TINTA, fontSize: 11 } },
    tooltip: { formatter: (p: any) => `${p.value[3]}<br>${nombreX}: ${p.value[0]}<br>${nombreY}: ${p.value[1]}` },
    series: [{
      type: 'scatter', data: puntos,
      symbolSize: burbujas ? (v: number[]) => 12 + Math.sqrt(v[2]) * 3 : 13,
    }],
  };
}

function calor(datos: any[], izquierda: number): any {
  const p = pivotear(datos);
  const celdas: number[][] = [];
  let maximo = 1;
  for (let s = 0; s < p.series.length; s++) {
    for (let c = 0; c < p.categorias.length; c++) {
      const valor = p.matriz[s][c] ?? 0;
      celdas.push([c, s, valor]);
      maximo = Math.max(maximo, valor);
    }
  }
  return {
    grid: { left: izquierda, right: 24, top: 16, bottom: 92 },
    xAxis: { type: 'category', data: p.categorias, splitArea: { show: true }, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'category', data: p.series, splitArea: { show: true } },
    tooltip: { position: 'top', formatter: (q: any) => `${p.series[q.value[1]]}, ${p.categorias[q.value[0]]}: ${q.value[2]}` },
    visualMap: { min: 0, max: maximo, orient: 'horizontal', left: 'center', bottom: 0, calculable: true,
      itemHeight: 110, itemWidth: 12, inRange: { color: ESCALA }, textStyle: { color: TINTA, fontSize: 11 } },
    series: [{ type: 'heatmap', data: celdas, itemStyle: { borderColor: '#fff', borderWidth: 1 } }],
  };
}

function embudo(datos: any[]): any {
  const pasos: any[] = [];
  for (let i = 0; i < datos.length; i++) {
    pasos.push({ name: datos[i].categoria, value: Number(datos[i].valor) });
  }
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}' },
    series: [{
      type: 'funnel', left: '8%', right: '8%', top: 10, bottom: 10, gap: 3, sort: 'none', data: pasos,
      label: { position: 'inside', color: TINTA, fontSize: 11, formatter: '{b}: {c}' },
      itemStyle: { borderColor: '#6E7B8B', borderWidth: 1 },
    }],
  };
}

function sankey(datos: any[]): any {
  const nodos: any[] = [];
  const vistos: Record<string, boolean> = {};
  const enlaces: any[] = [];
  for (let i = 0; i < datos.length; i++) {
    enlaces.push({ source: datos[i].origen, target: datos[i].destino, value: Number(datos[i].valor) });
    const extremos = [datos[i].origen, datos[i].destino];
    for (let k = 0; k < 2; k++) {
      if (!vistos[extremos[k]]) {
        vistos[extremos[k]] = true;
        nodos.push({ name: extremos[k] });
      }
    }
  }
  return {
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [{
      type: 'sankey', emphasis: { focus: 'adjacency' }, data: nodos, links: enlaces, left: 10, right: 130,
      label: { color: TINTA, fontSize: 11 }, lineStyle: { color: 'gradient', opacity: 0.45 }, itemStyle: { ...BORDE },
    }],
  };
}

function lineaTiempo(traza: any): any {
  const origenes = ['Seguimiento', 'Asignación', 'Bitácora'];
  const claves = ['SEGUIMIENTO', 'ASIGNACION', 'BITACORA'];
  const series: any[] = [];
  for (let o = 0; o < origenes.length; o++) {
    const puntos: any[] = [];
    for (let i = 0; i < traza.eventos.length; i++) {
      const evento = traza.eventos[i];
      if (evento.origen === claves[o]) {
        puntos.push({ value: [evento.fecha, origenes[o]], evento });
      }
    }
    series.push({ name: origenes[o], type: 'scatter', symbolSize: 13, data: puntos, itemStyle: { color: colorDeSerie(o + 3), ...BORDE } });
  }
  return {
    grid: { left: 92, right: 28, top: 34, bottom: 34 },
    legend: { top: 0 },
    xAxis: { type: 'time', axisLabel: { fontSize: 10 } },
    yAxis: { type: 'category', data: origenes },
    tooltip: { formatter: (p: any) => `${p.data.evento.operacion}<br>${p.data.evento.usuario}<br>${p.data.evento.detalle}` },
    series,
  };
}

function textoMeta(valor: number, meta: number | null): string {
  return meta === null ? `Actual: ${valor} %` : `Actual: ${valor} %<br>Meta: ${meta} %`;
}

function gauge(datos: any): any {
  const meta = numero(datos.meta);
  const tramos = meta === null ? [[1, ESCALA[0]]]
    : [[(meta - MARGEN_AMARILLO) / 100, SEMAFORO.rojo], [meta / 100, SEMAFORO.amarillo], [1, SEMAFORO.verde]];
  return {
    series: [{
      type: 'gauge', min: 0, max: 100, progress: { show: true, width: 16 },
      axisLine: { lineStyle: { width: 16, color: tramos } },
      itemStyle: { color: ESCALA[1], ...BORDE }, pointer: { width: 5, itemStyle: { color: '#6E7B8B' } },
      axisTick: { show: false }, splitLine: { length: 10, lineStyle: { color: '#CBB9DE' } },
      axisLabel: { color: TINTA, fontSize: 10, distance: -22 },
      detail: { valueAnimation: false, formatter: '{value} %', color: TINTA, fontSize: 26, offsetCenter: [0, '62%'] },
      data: [{ value: Number(datos.valor ?? 0) }],
    }],
  };
}

function bullet(datos: any): any {
  const valor = Number(datos.valor ?? 0);
  const meta = numero(datos.meta);
  const franjas: any[] = meta === null
    ? [{ type: 'bar', stack: 'r', barWidth: 54, silent: true, data: [100], itemStyle: { color: ESCALA[0] } }]
    : [
        { type: 'bar', stack: 'r', barWidth: 54, silent: true, data: [meta - MARGEN_AMARILLO], itemStyle: { color: SEMAFORO.rojo } },
        { type: 'bar', stack: 'r', silent: true, data: [MARGEN_AMARILLO], itemStyle: { color: SEMAFORO.amarillo } },
        { type: 'bar', stack: 'r', silent: true, data: [100 - meta], itemStyle: { color: SEMAFORO.verde } },
      ];
  return {
    grid: { left: 40, right: 40, top: 70, bottom: 70 },
    xAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value} %' } },
    yAxis: [{ type: 'category', data: [''], axisTick: { show: false } }, { type: 'category', data: [''], show: false }],
    tooltip: { formatter: () => textoMeta(valor, meta) },
    series: [
      ...franjas,
      { type: 'bar', yAxisIndex: 1, barWidth: 20, z: 5, data: [valor], itemStyle: { color: PASTEL[4], ...BORDE },
        label: { show: true, position: 'right', color: TINTA, formatter: '{c} %' },
        markLine: { silent: true, symbol: 'none', lineStyle: { color: TINTA, width: 3, type: 'solid' },
          label: { formatter: 'Meta', color: TINTA }, data: meta === null ? [] : [{ xAxis: meta }] } },
    ],
  };
}

function termometro(datos: any): any {
  const valor = Number(datos.valor ?? 0);
  const meta = numero(datos.meta);
  return {
    grid: { left: 90, right: 90, top: 16, bottom: 28 },
    xAxis: { type: 'category', data: [''], axisTick: { show: false } },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value} %' } },
    tooltip: { formatter: () => textoMeta(valor, meta) },
    series: [
      { type: 'bar', barWidth: 46, silent: true, data: [100], itemStyle: { color: '#F7F3FC', ...BORDE, borderRadius: [23, 23, 23, 23] } },
      { type: 'bar', barWidth: 46, barGap: '-100%', z: 5, data: [valor],
        itemStyle: { color: colorSegunMeta(valor, meta), ...BORDE, borderRadius: [0, 0, 23, 23] },
        label: { show: true, position: 'top', color: TINTA, formatter: '{c} %' },
        markLine: { silent: true, symbol: 'none', lineStyle: { color: TINTA, width: 2, type: 'solid' },
          label: { formatter: 'Meta', color: TINTA, position: 'end' }, data: meta === null ? [] : [{ yAxis: meta }] } },
    ],
  };
}

export function sparkline(valores: (number | null)[]): any {
  const indices: number[] = [];
  for (let i = 0; i < valores.length; i++) {
    indices.push(i);
  }
  return {
    grid: { left: 0, right: 0, top: 3, bottom: 3 },
    xAxis: { type: 'category', show: false, data: indices, boundaryGap: false },
    yAxis: { type: 'value', show: false, scale: true },
    series: [{ type: 'line', data: valores, smooth: true, symbol: 'none', connectNulls: true,
      lineStyle: { width: 2.5, color: PASTEL[4] }, areaStyle: { color: PASTEL[4], opacity: 0.3 } }],
  };
}

export const CONSTRUCTORES: Record<string, Constructor> = {
  barras_v: barrasVerticales,
  barras_h: barrasHorizontales,
  barras_agrupadas: (d) => barrasMultiples(d, undefined, false),
  barras_apiladas: (d) => barrasMultiples(d, 'total', false),
  barras_100: (d) => barrasMultiples(d, 'total', true),
  lollipop,
  radar,
  pareto,
  linea: (d, c) => lineaSimple(d, c, false, false),
  lineas_multiples: (d) => lineasMultiples(d, false),
  area: (d, c) => lineaSimple(d, c, true, false),
  area_apilada: (d) => lineasMultiples(d, true),
  escalonada: (d, c) => lineaSimple(d, c, true, true),
  combinada,
  cascada,
  calendario,
  pastel: (d) => sectores(d, '70%', ['36%', '50%']),
  dona: (d) => sectores(d, ['44%', '72%'], ['36%', '50%']),
  semidona: (d) => sectores(d, ['42%', '70%'], ['50%', '66%'], 180, 360, true),
  treemap,
  sunburst,
  waffle,
  histograma,
  boxplot,
  dispersion: (d) => dispersion(d, 'Horas hasta asignar', 'Horas hasta resolver', false),
  burbujas: (d) => dispersion(d, 'Carga actual', 'Horas promedio', true),
  calor_dia_hora: (d) => calor(d, 92),
  calor_especialidad_mes: (d) => calor(d, 120),
  embudo,
  sankey,
  linea_tiempo: lineaTiempo,
  arbol,
  gauge,
  bullet,
  termometro,
};
