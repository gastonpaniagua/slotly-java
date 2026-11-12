// Fechas de calendario como texto "2026-11-17", sin hora ni zona horaria.
// Se calculan en UTC a propósito: así el reloj y la zona del navegador del cliente nunca corren un día
// (el "hoy" lo decide el back con la zona del local).

function aDate(fecha: string): Date {
  return new Date(`${fecha}T00:00:00Z`)
}

function aTexto(fecha: Date): string {
  return fecha.toISOString().slice(0, 10)
}

export function sumarDias(fecha: string, dias: number): string {
  const d = aDate(fecha)
  d.setUTCDate(d.getUTCDate() + dias)
  return aTexto(d)
}

// El lunes de la semana de esa fecha (la semana arranca en lunes).
export function lunesDe(fecha: string): string {
  const diaSemana = aDate(fecha).getUTCDay() // 0 domingo … 6 sábado
  return sumarDias(fecha, -((diaSemana + 6) % 7))
}

// Los 7 días de la semana que empieza ese lunes.
export function diasDeLaSemana(lunes: string): string[] {
  return Array.from({ length: 7 }, (_, i) => sumarDias(lunes, i))
}

const formatoDiaCorto = new Intl.DateTimeFormat('es-AR', { weekday: 'short', timeZone: 'UTC' })
const formatoLargo = new Intl.DateTimeFormat('es-AR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' })
const formatoDiaMes = new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'long', timeZone: 'UTC' })

// "lun", "mar"… (sin el punto que agrega a veces Intl)
export function nombreCortoDelDia(fecha: string): string {
  return formatoDiaCorto.format(aDate(fecha)).replace('.', '')
}

export function numeroDelDia(fecha: string): number {
  return aDate(fecha).getUTCDate()
}

// "martes, 17 de noviembre" → "martes 17 de noviembre"
export function fechaLarga(fecha: string): string {
  return formatoLargo.format(aDate(fecha)).replace(',', '')
}

// "16 al 22 de noviembre", o "30 de noviembre al 6 de diciembre" si la semana cruza de mes.
export function textoDeLaSemana(lunes: string): string {
  const domingo = sumarDias(lunes, 6)
  const [, mesLunes] = lunes.split('-')
  const [, mesDomingo] = domingo.split('-')
  if (mesLunes === mesDomingo) {
    return `${numeroDelDia(lunes)} al ${formatoDiaMes.format(aDate(domingo))}`
  }
  return `${formatoDiaMes.format(aDate(lunes))} al ${formatoDiaMes.format(aDate(domingo))}`
}
