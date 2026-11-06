package com.example.soltec.service;

import com.example.soltec.service.DashboardCatalogo.Metrica;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class DashboardExcelService {

    private static final int ANCHO_COLUMNA = 26 * 256;

    public byte[] generar(String titulo, String periodo, String nombreAgrupacion, Metrica metrica,
                          List<Map<String, Object>> filas, Double total, Map<String, Object> indicadores,
                          List<Map<String, Object>> tecnicos) throws IOException {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            CellStyle estiloTitulo = estiloNegrita(libro, (short) 14);
            CellStyle estiloEncabezado = estiloNegrita(libro, (short) 11);

            hojaReporte(libro, titulo, periodo, nombreAgrupacion, metrica, filas, total, estiloTitulo, estiloEncabezado);
            hojaIndicadores(libro, periodo, indicadores, estiloTitulo, estiloEncabezado);
            hojaTecnicos(libro, periodo, tecnicos, estiloTitulo, estiloEncabezado);

            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private void hojaReporte(Workbook libro, String titulo, String periodo, String nombreAgrupacion, Metrica metrica,
                             List<Map<String, Object>> filas, Double total, CellStyle estiloTitulo, CellStyle estiloEncabezado) {
        Sheet hoja = libro.createSheet("Reporte");
        int numero = encabezado(hoja, titulo, periodo, estiloTitulo);

        boolean conPorcentaje = metrica.aditiva() && total != null && total > 0;
        Row cabecera = hoja.createRow(numero++);
        celda(cabecera, 0, nombreAgrupacion, estiloEncabezado);
        celda(cabecera, 1, metrica.corto(), estiloEncabezado);
        if (conPorcentaje) {
            celda(cabecera, 2, "% del total", estiloEncabezado);
        }

        for (int i = 0; i < filas.size(); i++) {
            Row fila = hoja.createRow(numero++);
            Object valor = filas.get(i).get("valor");
            celda(fila, 0, filas.get(i).get("categoria"), null);
            celda(fila, 1, valor, null);
            if (conPorcentaje && valor instanceof Number cantidad) {
                celda(fila, 2, Math.round(cantidad.doubleValue() / total * 1000) / 10.0, null);
            }
        }

        if (total != null) {
            Row fila = hoja.createRow(numero);
            celda(fila, 0, "Total", estiloEncabezado);
            celda(fila, 1, total, estiloEncabezado);
        }
        anchos(hoja, 3);
    }

    private void hojaIndicadores(Workbook libro, String periodo, Map<String, Object> indicadores,
                                 CellStyle estiloTitulo, CellStyle estiloEncabezado) {
        Sheet hoja = libro.createSheet("Indicadores");
        int numero = encabezado(hoja, "Indicadores", periodo, estiloTitulo);

        Row cabecera = hoja.createRow(numero++);
        celda(cabecera, 0, "Indicador", estiloEncabezado);
        celda(cabecera, 1, "Valor", estiloEncabezado);
        for (Map.Entry<String, Object> indicador : indicadores.entrySet()) {
            Row fila = hoja.createRow(numero++);
            celda(fila, 0, indicador.getKey(), null);
            celda(fila, 1, indicador.getValue(), null);
        }
        anchos(hoja, 2);
    }

    private void hojaTecnicos(Workbook libro, String periodo, List<Map<String, Object>> tecnicos,
                              CellStyle estiloTitulo, CellStyle estiloEncabezado) {
        Sheet hoja = libro.createSheet("Técnicos");
        int numero = encabezado(hoja, "Técnicos", periodo, estiloTitulo);

        String[] columnas = {"tecnico", "carga", "resueltas", "horas", "sla"};
        String[] titulos = {"Técnico", "Carga", "Resueltas", "Horas promedio", "SLA %"};
        Row cabecera = hoja.createRow(numero++);
        for (int c = 0; c < titulos.length; c++) {
            celda(cabecera, c, titulos[c], estiloEncabezado);
        }
        for (int i = 0; i < tecnicos.size(); i++) {
            Row fila = hoja.createRow(numero++);
            for (int c = 0; c < columnas.length; c++) {
                celda(fila, c, tecnicos.get(i).get(columnas[c]), null);
            }
        }
        anchos(hoja, columnas.length);
    }

    // Devuelve la siguiente fila libre
    private int encabezado(Sheet hoja, String titulo, String periodo, CellStyle estiloTitulo) {
        celda(hoja.createRow(0), 0, titulo, estiloTitulo);
        celda(hoja.createRow(1), 0, "Período: " + periodo, null);
        return 3;
    }

    private void celda(Row fila, int columna, Object valor, CellStyle estilo) {
        Cell celda = fila.createCell(columna);
        if (valor instanceof Number numero) {
            celda.setCellValue(numero.doubleValue());
        } else if (valor != null) {
            celda.setCellValue(valor.toString());
        }
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    private void anchos(Sheet hoja, int columnas) {
        for (int c = 0; c < columnas; c++) {
            hoja.setColumnWidth(c, ANCHO_COLUMNA);
        }
    }

    private CellStyle estiloNegrita(Workbook libro, short tamano) {
        Font fuente = libro.createFont();
        fuente.setBold(true);
        fuente.setFontHeightInPoints(tamano);
        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente);
        return estilo;
    }
}
