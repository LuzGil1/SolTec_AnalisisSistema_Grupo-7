import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import {
  AgrupacionDTO,
  CatalogoDashboardDTO,
  ConsultaDashboard,
  DashboardService,
  FiltrosDashboard,
  MetricaDTO,
  OpcionesDashboardDTO,
  ResultadoConsulta,
  TipoGraficaDTO,
} from '../../../../core/services/dashboard.service';
import {
  BANDERAS,
  CAMPOS_FILTRO,
  DefinicionGrafica,
  FRECUENTES,
  GRAFICAS,
  INDICADORES,
  Indicador,
  METRICAS_VARIACION,
  NOTA_CARGA,
  SOLO_BARRAS,
} from './catalogo-graficas';
import { CONSTRUCTORES, Contexto, sparkline } from './opciones-graficas';
import { SEMAFORO, echarts } from './tema-echarts';
import { TrazabilidadComponent } from '../trazabilidad/trazabilidad.component';

const CLAVE_LOCAL = 'soltec_dashboard';
const ID_EXPLORADOR = 'explorador';

interface FilaVariacion {
  titulo: string;
  valor: string;
  texto: string;
}

interface FilaExplorador {
  categoria: string;
  valor: string;
  porcentaje: string;
}

@Component({
  selector: 'app-dashboard-graficas',
  standalone: true,
  imports: [FormsModule, NgxEchartsDirective, TrazabilidadComponent],
  providers: [provideEchartsCore({ echarts })],
  templateUrl: './dashboard-graficas.component.html',
  styleUrl: './dashboard-graficas.component.scss',
})
export class DashboardGraficasComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);

  readonly graficas = GRAFICAS;
  readonly tarjetas = GRAFICAS.filter((g) => g.vista !== 'indicadores');
  readonly indicadores = INDICADORES;
  readonly frecuentes = FRECUENTES;
  readonly banderas = BANDERAS;
  readonly camposFiltro = CAMPOS_FILTRO;
  readonly semaforo = SEMAFORO;
  readonly notaCarga = NOTA_CARGA;
  readonly opcionesInicio = { renderer: 'svg' as const };
  // ngx-echarts no crea la grafica sin opciones: cada dibujo va por dibujar()
  readonly sinOpciones = {};

  catalogo: CatalogoDashboardDTO | null = null;
  opciones: OpcionesDashboardDTO | null = null;
  periodo = 'ultimos_12_meses';
  filtros: FiltrosDashboard = { modoFecha: 'REGISTRO' };

  verFiltros = false;
  verConfiguracion = false;
  graficasVisibles: string[] = GRAFICAS.map((g) => g.id);
  filtrosVisibles: string[] = CAMPOS_FILTRO.map((c) => c.id);

  cargando = true;
  error = '';
  totalSolicitudes = 0;
  etiquetaPeriodo = '';
  resultados: Record<string, ResultadoConsulta | undefined> = {};
  sinDatos: Record<string, boolean> = {};
  fallidas: Record<string, boolean> = {};
  private instancias: Record<string, any> = {};

  valoresIndicador: Record<string, string | undefined> = {};
  textosIndicador: Record<string, string> = {};
  filasVariacion: FilaVariacion[] = [];

  // Explorador: una grafica libre con los selectores del catalogo
  frecuente = 0;
  metrica = 'cantidad';
  agrupacion = 'tipo';
  agrupacionSecundaria = 'prioridad';
  tipoGrafica = 'barras_h';
  tituloExplorador = '';
  periodoExplorador = '';
  filasExplorador: FilaExplorador[] = [];
  columnaPorcentaje = false;

  ngOnInit(): void {
    this.leerConfiguracion();
    this.dashboardService.obtenerCatalogo().subscribe({
      next: (catalogo) => {
        this.catalogo = catalogo;
        this.ajustarExplorador();
        this.cargar();
        this.consultarExplorador();
      },
      error: (err) => (this.error = err?.error?.mensaje ?? 'No se pudo cargar el catálogo del dashboard.'),
    });
    this.recargarOpciones();
  }

  // ----- Filtros encadenados -----

  // Si el padre cambia y el hijo ya no es valido, el hijo se limpia solo:
  // nunca se envia una combinacion imposible.
  recargarOpciones(): void {
    const servicio = this.filtros.servicioId ?? null;
    const especialidad = this.filtros.especialidadId ?? null;
    this.dashboardService.obtenerOpciones(servicio, especialidad).subscribe({
      next: (opciones) => {
        if (especialidad !== null && !this.existe(opciones.especialidades, especialidad)) {
          this.filtros.especialidadId = null;
          this.recargarOpciones();
          return;
        }
        const tecnico = this.filtros.tecnicoId ?? null;
        if (tecnico !== null && !this.existe(opciones.tecnicos, tecnico)) {
          this.filtros.tecnicoId = null;
        }
        this.opciones = opciones;
      },
    });
  }

  private existe(lista: { codigo: string }[], id: number): boolean {
    for (let i = 0; i < lista.length; i++) {
      if (Number(lista[i].codigo) === id) {
        return true;
      }
    }
    return false;
  }

  numeroDe(codigo: string): number {
    return Number(codigo);
  }

  // Lo que se envia: un filtro oculto nunca viaja al backend
  private filtrosAEnviar(): FiltrosDashboard {
    const salida: Record<string, unknown> = {};
    const claves = Object.keys(this.filtros);
    for (let i = 0; i < claves.length; i++) {
      if (this.filtroVisible(claves[i])) {
        salida[claves[i]] = (this.filtros as Record<string, unknown>)[claves[i]];
      }
    }
    return salida as FiltrosDashboard;
  }

  aplicar(): void {
    this.cargar();
    this.consultarExplorador();
  }

  limpiar(): void {
    this.filtros = { modoFecha: 'REGISTRO' };
    this.periodo = 'ultimos_12_meses';
    this.recargarOpciones();
    this.aplicar();
  }

  // ----- Consultas -----

  private consultasDelLote(): ConsultaDashboard[] {
    const consultas: ConsultaDashboard[] = [];
    for (let i = 0; i < GRAFICAS.length; i++) {
      const g = GRAFICAS[i];
      if (this.visible(g.id) && g.consulta) {
        consultas.push({ ...g.consulta, clave: g.id });
      }
    }
    for (let i = 0; i < INDICADORES.length; i++) {
      consultas.push({ clave: `kpi_${i}`, forma: 'escalar', metrica: INDICADORES[i].metrica });
      consultas.push({ clave: `spark_${i}`, forma: 'temporal', metrica: INDICADORES[i].metrica, agrupacion: 'mes' });
    }
    if (this.visible('tarjetas_variacion')) {
      for (let i = 0; i < METRICAS_VARIACION.length; i++) {
        consultas.push({ clave: `variacion_${i}`, forma: 'escalar', metrica: METRICAS_VARIACION[i] });
      }
    }
    return consultas;
  }

  cargar(): void {
    this.cargando = true;
    this.dashboardService.consultarLote(this.periodo, this.filtrosAEnviar(), this.consultasDelLote()).subscribe({
      next: (lote) => {
        this.totalSolicitudes = lote.totalSolicitudes;
        this.etiquetaPeriodo = lote.periodo;
        this.resultados = {};
        for (let i = 0; i < lote.resultados.length; i++) {
          this.resultados[lote.resultados[i].clave ?? ''] = lote.resultados[i];
        }
        this.error = '';
        this.cargando = false;
        this.prepararIndicadores();
        this.prepararVariacion();
        this.dibujarTodas();
      },
      error: (err) => {
        this.cargando = false;
        this.error = err?.error?.mensaje ?? 'No se pudieron cargar las gráficas.';
      },
    });
  }

  // ----- Dibujo -----

  alIniciar(id: string, instancia: any): void {
    this.instancias[id] = instancia;
    this.dibujar(id);
  }

  private dibujarTodas(): void {
    const ids = Object.keys(this.instancias);
    for (let i = 0; i < ids.length; i++) {
      this.dibujar(ids[i]);
    }
  }

  private estaVacio(datos: any): boolean {
    if (datos === null || datos === undefined) {
      return true;
    }
    if (Array.isArray(datos)) {
      return datos.length === 0;
    }
    return datos.valor === null;
  }

  // Cada grafica va en su propio try/catch: si una falla, se registra en
  // consola y las demas se siguen dibujando.
  private dibujar(id: string): void {
    const instancia = this.instancias[id];
    if (!instancia) {
      return;
    }
    if (id.startsWith('spark_')) {
      this.dibujarSparkline(id, instancia);
      return;
    }
    if (id === ID_EXPLORADOR) {
      this.dibujarExplorador(instancia);
      return;
    }

    const datos = this.resultados[id]?.datos;
    const vacio = this.estaVacio(datos);
    this.sinDatos[id] = vacio;
    if (vacio) {
      instancia.clear();
      return;
    }
    try {
      instancia.setOption(CONSTRUCTORES[id](datos, this.contextoDe(this.resultados[id]?.titulo)), true);
      this.fallidas[id] = false;
    } catch (e) {
      this.fallidas[id] = true;
      console.error(`No se pudo dibujar ${id}:`, e);
    }
  }

  private contextoDe(titulo?: string): Contexto {
    return { unidad: '', nombre: titulo ?? '' };
  }

  private dibujarSparkline(id: string, instancia: any): void {
    const datos = this.resultados[id]?.datos;
    if (!datos) {
      return;
    }
    const valores: (number | null)[] = [];
    for (let i = 0; i < datos.length; i++) {
      valores.push(datos[i].valor === null ? null : Number(datos[i].valor));
    }
    try {
      instancia.setOption(sparkline(valores), true);
    } catch (e) {
      console.error(`No se pudo dibujar ${id}:`, e);
    }
  }

  // ----- Indicadores -----

  private prepararIndicadores(): void {
    for (let i = 0; i < INDICADORES.length; i++) {
      const datos = this.resultados[`kpi_${i}`]?.datos;
      this.valoresIndicador[INDICADORES[i].metrica] = this.conUnidad(datos?.valor, INDICADORES[i].unidad);
      this.textosIndicador[INDICADORES[i].metrica] = this.textoVariacion(datos?.valor, datos?.anterior, INDICADORES[i].unidad);
    }
  }

  private conUnidad(valor: any, unidad: string): string {
    return valor === null || valor === undefined ? 'N/D' : `${Number(valor)}${unidad}`;
  }

  private textoVariacion(valor: any, anterior: any, unidad: string): string {
    if (valor === null || valor === undefined || anterior === null || anterior === undefined) {
      return 'Sin período anterior para comparar';
    }
    const diferencia = Math.round((Number(valor) - Number(anterior)) * 10) / 10;
    if (diferencia === 0) {
      return 'Igual que el período anterior';
    }
    return `${diferencia > 0 ? '+' : ''}${diferencia}${unidad} contra el período anterior`;
  }

  private prepararVariacion(): void {
    const filas: FilaVariacion[] = [];
    for (let i = 0; i < METRICAS_VARIACION.length; i++) {
      const resultado = this.resultados[`variacion_${i}`];
      if (resultado?.datos) {
        filas.push({
          titulo: resultado.titulo,
          valor: this.conUnidad(resultado.datos.valor, ''),
          texto: this.textoVariacion(resultado.datos.valor, resultado.datos.anterior, ''),
        });
      }
    }
    this.filasVariacion = filas;
  }

  colorSemaforo(valor: string | null): string {
    if (valor === 'verde') {
      return SEMAFORO.verde;
    }
    if (valor === 'amarillo') {
      return SEMAFORO.amarillo;
    }
    return valor === 'rojo' ? SEMAFORO.rojo : 'transparent';
  }

  // ----- Explorador -----

  get metricaActual(): MetricaDTO | undefined {
    return this.catalogo?.metricas.find((m) => m.clave === this.metrica);
  }

  get agrupacionActual(): AgrupacionDTO | undefined {
    return this.catalogo?.agrupaciones.find((a) => a.clave === this.agrupacion);
  }

  get tipoActual(): TipoGraficaDTO | undefined {
    return this.catalogo?.tiposGrafica.find((t) => t.clave === this.tipoGrafica);
  }

  get tiposExplorador(): TipoGraficaDTO[] {
    return (this.catalogo?.tiposGrafica ?? []).filter((t) => t.explorador);
  }

  agrupacionDeshabilitada(clave: string): boolean {
    return !this.metricaActual?.agrupacionesPermitidas.includes(clave);
  }

  secundariaDeshabilitada(clave: string): boolean {
    return clave === this.agrupacion || clave === 'accion_bitacora' || this.agrupacionDeshabilitada(clave);
  }

  // Lee las reglas del catalogo, no las adivina
  tipoDeshabilitado(tipo: TipoGraficaDTO): boolean {
    const metrica = this.metricaActual;
    const agrupacion = this.agrupacionActual;
    if (!metrica || !agrupacion) {
      return true;
    }
    if (tipo.requiereTiempo && !agrupacion.temporal) {
      return true;
    }
    if (tipo.soloAditiva && !metrica.aditiva) {
      return true;
    }
    if (tipo.requiereSecundaria) {
      return this.agrupacion === 'accion_bitacora' || this.primeraSecundariaValida() === null;
    }
    return false;
  }

  private primeraSecundariaValida(): string | null {
    const agrupaciones = this.catalogo?.agrupaciones ?? [];
    if (!this.secundariaDeshabilitada(this.agrupacionSecundaria)) {
      return this.agrupacionSecundaria;
    }
    for (let i = 0; i < agrupaciones.length; i++) {
      if (!this.secundariaDeshabilitada(agrupaciones[i].clave)) {
        return agrupaciones[i].clave;
      }
    }
    return null;
  }

  private ajustarExplorador(): void {
    const metrica = this.metricaActual;
    if (!metrica) {
      return;
    }
    if (this.agrupacionDeshabilitada(this.agrupacion)) {
      this.agrupacion = metrica.agrupacionesPermitidas[0];
    }
    const tipos = this.tiposExplorador;
    const actual = this.tipoActual;
    if (!actual || this.tipoDeshabilitado(actual)) {
      for (let i = 0; i < tipos.length; i++) {
        if (!this.tipoDeshabilitado(tipos[i])) {
          this.tipoGrafica = tipos[i].clave;
          break;
        }
      }
    }
    if (this.tipoActual?.requiereSecundaria) {
      this.agrupacionSecundaria = this.primeraSecundariaValida() ?? this.agrupacionSecundaria;
    }
  }

  cambioFrecuente(): void {
    const reporte = FRECUENTES[this.frecuente];
    if (!reporte.metrica) {
      return;
    }
    this.metrica = reporte.metrica;
    this.agrupacion = reporte.agrupacion ?? this.agrupacion;
    this.tipoGrafica = reporte.tipo ?? this.tipoGrafica;
    this.ajustarExplorador();
    this.consultarExplorador();
  }

  // Si el usuario mueve algo a mano, pasa a Personalizado
  cambioExplorador(): void {
    this.frecuente = 0;
    this.ajustarExplorador();
    this.consultarExplorador();
  }

  private consultaExplorador(): ConsultaDashboard {
    const tipo = this.tipoActual;
    const forma = tipo?.forma ?? 'serie_simple';
    const consulta: ConsultaDashboard = { forma, metrica: this.metrica, agrupacion: this.agrupacion };
    if (tipo?.requiereSecundaria) {
      consulta.agrupacionSecundaria = this.agrupacionSecundaria;
    }
    return consulta;
  }

  private resultadoExplorador: ResultadoConsulta | null = null;

  consultarExplorador(): void {
    if (!this.catalogo) {
      return;
    }
    this.dashboardService.consultar(this.periodo, this.filtrosAEnviar(), this.consultaExplorador()).subscribe({
      next: (resultado) => {
        this.resultadoExplorador = resultado;
        this.tituloExplorador = resultado.titulo;
        this.periodoExplorador = resultado.periodo;
        this.prepararTablaExplorador(resultado);
        this.dibujar(ID_EXPLORADOR);
      },
      error: (err) => {
        this.resultadoExplorador = null;
        this.filasExplorador = [];
        this.tituloExplorador = err?.error?.mensaje ?? 'No se pudo consultar el reporte.';
        this.dibujar(ID_EXPLORADOR);
      },
    });
  }

  private dibujarExplorador(instancia: any): void {
    const datos = this.resultadoExplorador?.datos;
    const vacio = this.estaVacio(datos);
    this.sinDatos[ID_EXPLORADOR] = vacio;
    if (vacio) {
      instancia.clear();
      return;
    }
    try {
      instancia.setOption(CONSTRUCTORES[this.tipoGrafica](datos, { unidad: this.metricaActual?.unidad ?? '', nombre: this.tituloExplorador }), true);
    } catch (e) {
      console.error(`No se pudo dibujar ${this.tipoGrafica}:`, e);
    }
  }

  // La columna % del total solo aparece si la metrica se puede sumar
  private prepararTablaExplorador(resultado: ResultadoConsulta): void {
    const metrica = this.metricaActual;
    const total = resultado.total ?? 0;
    this.columnaPorcentaje = !!metrica?.aditiva && total > 0;
    const filas: FilaExplorador[] = [];
    const datos: any[] = Array.isArray(resultado.datos) ? resultado.datos : [];
    for (let i = 0; i < datos.length; i++) {
      const nombre = datos[i].serie ? `${datos[i].categoria}, ${datos[i].serie}` : datos[i].categoria ?? datos[i].fecha;
      const valor = datos[i].valor;
      filas.push({
        categoria: nombre,
        valor: valor === null ? 'N/D' : `${Number(valor)}${metrica?.unidad ?? ''}`,
        porcentaje: valor === null || total === 0 ? '' : `${((Number(valor) / total) * 100).toFixed(1)} %`,
      });
    }
    this.filasExplorador = filas;
  }

  // ----- Descargas -----

  imprimir(): void {
    window.print();
  }

  descargarExcel(): void {
    this.dashboardService.exportarExcel(this.periodo, this.metrica, this.agrupacion, this.filtrosAEnviar()).subscribe({
      next: (archivo) => {
        const enlace = document.createElement('a');
        enlace.href = URL.createObjectURL(archivo);
        enlace.download = 'dashboard_soltec.xlsx';
        enlace.click();
        URL.revokeObjectURL(enlace.href);
      },
      error: () => (this.error = 'No se pudo generar el archivo de Excel.'),
    });
  }

  // ----- Panel de configuracion -----

  visible(id: string): boolean {
    return this.graficasVisibles.indexOf(id) !== -1;
  }

  filtroVisible(id: string): boolean {
    return this.filtrosVisibles.indexOf(id) !== -1;
  }

  alternarGrafica(grafica: DefinicionGrafica): void {
    if (this.visible(grafica.id)) {
      this.graficasVisibles = this.graficasVisibles.filter((id) => id !== grafica.id);
      this.guardarConfiguracion();
      return;
    }
    this.graficasVisibles = [...this.graficasVisibles, grafica.id];
    this.guardarConfiguracion();
    this.cargar();
  }

  // Un filtro oculto se limpia y deja de enviarse
  alternarFiltro(id: string): void {
    if (this.filtroVisible(id)) {
      this.filtrosVisibles = this.filtrosVisibles.filter((f) => f !== id);
      delete (this.filtros as Record<string, unknown>)[id];
      if (id === 'servicioId' || id === 'especialidadId') {
        this.recargarOpciones();
      }
      this.aplicar();
    } else {
      this.filtrosVisibles = [...this.filtrosVisibles, id];
    }
    this.guardarConfiguracion();
  }

  aplicarPerfil(perfil: 'todo' | 'barras' | 'restaurar'): void {
    this.graficasVisibles = perfil === 'barras' ? [...SOLO_BARRAS] : GRAFICAS.map((g) => g.id);
    if (perfil === 'restaurar') {
      this.filtrosVisibles = CAMPOS_FILTRO.map((c) => c.id);
      localStorage.removeItem(CLAVE_LOCAL);
    } else {
      this.guardarConfiguracion();
    }
    this.cargar();
  }

  private guardarConfiguracion(): void {
    localStorage.setItem(CLAVE_LOCAL, JSON.stringify({ graficas: this.graficasVisibles, filtros: this.filtrosVisibles }));
  }

  private leerConfiguracion(): void {
    try {
      const guardado = JSON.parse(localStorage.getItem(CLAVE_LOCAL) ?? 'null');
      if (Array.isArray(guardado?.graficas)) {
        this.graficasVisibles = guardado.graficas;
      }
      if (Array.isArray(guardado?.filtros)) {
        this.filtrosVisibles = guardado.filtros;
      }
    } catch {
      localStorage.removeItem(CLAVE_LOCAL);
    }
  }

  variacionIndicador(indicador: Indicador): string {
    return this.textosIndicador[indicador.metrica] ?? '';
  }

  filtrosDe(campo: string): boolean {
    return (this.filtros as Record<string, boolean>)[campo] === true;
  }

  cambiarBandera(campo: string, valor: boolean): void {
    (this.filtros as Record<string, boolean>)[campo] = valor;
  }
}
