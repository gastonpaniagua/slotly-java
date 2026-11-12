import type { FranjaDeAgenda, TurnoDeAgenda } from '../api/tipos'

// Lo que se dibuja en la lista de un día de un profesional: turnos, bloqueos y pausas, en orden.
// Todas las horas son del local y vienen así de la API ("2026-11-17T10:00"): acá no se convierten zonas.
export type ItemDeAgenda =
  | { tipo: 'turno'; desde: string; hasta: string; turno: TurnoDeAgenda }
  | { tipo: 'bloqueo'; desde: string; hasta: string; turno: TurnoDeAgenda }
  | { tipo: 'pausa'; desde: string; hasta: string }

export const TODO_EL_DIA = { desde: '00:00', hasta: '24:00' }

// Si el turno o bloqueo toca ese día. Las fechas "2026-11-17T10:00" se comparan como texto: el orden es el mismo.
function tocaElDia(turno: TurnoDeAgenda, fecha: string): boolean {
  return turno.inicio < `${fecha}T24:00` && turno.fin > `${fecha}T00:00`
}

// La parte del turno que cae en ese día: un bloqueo de varios días (vacaciones) se corta en 00:00 y 24:00.
function horasEnElDia(turno: TurnoDeAgenda, fecha: string): { desde: string; hasta: string } {
  return {
    desde: turno.inicio.slice(0, 10) < fecha ? TODO_EL_DIA.desde : turno.inicio.slice(11, 16),
    hasta: turno.fin.slice(0, 10) > fecha ? TODO_EL_DIA.hasta : turno.fin.slice(11, 16),
  }
}

// Las franjas de trabajo de ese día y profesional, en orden. Entre una y otra hay una pausa (HU-17 CA5).
export function franjasDelDia(franjas: FranjaDeAgenda[], fecha: string, profesionalId: string): FranjaDeAgenda[] {
  return franjas
    .filter((f) => f.fecha === fecha && f.profesionalId === profesionalId)
    .sort((a, b) => a.desde.localeCompare(b.desde))
}

export function itemsDelDia(
  fecha: string,
  profesionalId: string,
  franjas: FranjaDeAgenda[],
  turnos: TurnoDeAgenda[],
): ItemDeAgenda[] {
  const items: ItemDeAgenda[] = turnos
    .filter((t) => t.profesionalId === profesionalId && tocaElDia(t, fecha))
    .map((t) => ({ tipo: t.tipo === 'bloqueo' ? 'bloqueo' : 'turno', ...horasEnElDia(t, fecha), turno: t }))

  const delDia = franjasDelDia(franjas, fecha, profesionalId)
  for (let i = 1; i < delDia.length; i++) {
    if (delDia[i - 1].hasta < delDia[i].desde) {
      items.push({ tipo: 'pausa', desde: delDia[i - 1].hasta, hasta: delDia[i].desde })
    }
  }
  return items.sort((a, b) => a.desde.localeCompare(b.desde) || a.hasta.localeCompare(b.hasta))
}

// "10:00 a 13:00 y 16:00 a 20:00"
export function textoDelHorario(franjas: FranjaDeAgenda[]): string {
  const partes = franjas.map((f) => `${f.desde} a ${f.hasta}`)
  return partes.length <= 1 ? partes.join('') : `${partes.slice(0, -1).join(', ')} y ${partes[partes.length - 1]}`
}

const ESTADOS: Record<string, string> = {
  confirmado: 'Confirmado',
  completado: 'Vino',
  ausente: 'No vino',
}

export function textoDelEstado(estado: string): string {
  return ESTADOS[estado] ?? estado
}
