export interface Vencimiento {
  texto: string;
  clase: string;
}

const HORA_MS = 1000 * 60 * 60;

function plural(n: number, singular: string, pluralTexto: string): string {
  return `${n} ${n === 1 ? singular : pluralTexto}`;
}

// Tiempo restante hasta la fecha limite, con color segun urgencia:
// vencido en rojo, menos de 6 horas en ambar, el resto normal.
export function describirVencimiento(fechaLimite: string | null, finalizado = false): Vencimiento {
  if (finalizado) {
    return { texto: 'Finalizado', clase: 'text-brand-text-muted' };
  }
  if (!fechaLimite) {
    return { texto: 'Sin vencimiento', clase: 'text-brand-text-muted' };
  }

  const diffMs = new Date(fechaLimite).getTime() - Date.now();
  const horas = Math.abs(diffMs) / HORA_MS;

  if (diffMs < 0) {
    const dias = Math.floor(horas / 24);
    const texto = dias >= 1
      ? `Vencido hace ${plural(dias, 'día', 'días')}`
      : `Vencido hace ${plural(Math.max(1, Math.round(horas)), 'hora', 'horas')}`;
    return { texto, clase: 'text-red-600 font-semibold' };
  }

  if (horas < 6) {
    return { texto: `En ${plural(Math.max(1, Math.round(horas)), 'hora', 'horas')}`, clase: 'text-amber-600 font-semibold' };
  }

  if (horas < 24) {
    return { texto: `En ${plural(Math.round(horas), 'hora', 'horas')}`, clase: 'text-brand-text-dark' };
  }

  return { texto: `En ${plural(Math.round(horas / 24), 'día', 'días')}`, clase: 'text-brand-text-dark' };
}

// Tiempo que una solicitud lleva esperando en la bolsa ("5 horas", "3 días").
export function describirEspera(horas: number): string {
  if (horas < 1) {
    return 'Menos de 1 hora';
  }
  if (horas < 24) {
    return plural(Math.floor(horas), 'hora', 'horas');
  }
  return plural(Math.floor(horas / 24), 'día', 'días');
}
