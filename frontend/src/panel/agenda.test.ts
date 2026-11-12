import { describe, expect, it } from 'vitest'

import type { FranjaDeAgenda } from '../api/tipos'
import { unTurno } from '../test/datos'
import { itemsDelDia, textoDelEstado, textoDelHorario } from './agenda'

function franja(profesionalId: string, fecha: string, desde: string, hasta: string): FranjaDeAgenda {
  return { profesionalId, fecha, desde, hasta }
}

const HORARIO_DEL_MARTES = [franja('ana', '2026-11-17', '16:00', '20:00'), franja('ana', '2026-11-17', '10:00', '13:00')]

describe('itemsDelDia', () => {
  it('HU-17 CA1 y CA5: los turnos y bloqueos del profesional, con la pausa entre franjas, en orden de hora', () => {
    const once = unTurno({ inicio: '2026-11-17T11:00', fin: '2026-11-17T11:45' })
    const bloqueo = unTurno({ tipo: 'bloqueo', inicio: '2026-11-17T17:00', fin: '2026-11-17T18:00', clienteNombre: null })
    const diez = unTurno({ inicio: '2026-11-17T10:00', fin: '2026-11-17T10:30' })
    const deBruno = unTurno({ profesionalId: 'bruno' })
    const delMiercoles = unTurno({ inicio: '2026-11-18T10:00', fin: '2026-11-18T10:30' })

    const items = itemsDelDia('2026-11-17', 'ana', HORARIO_DEL_MARTES, [once, bloqueo, diez, deBruno, delMiercoles])

    expect(items.map((i) => [i.tipo, i.desde, i.hasta])).toEqual([
      ['turno', '10:00', '10:30'],
      ['turno', '11:00', '11:45'],
      ['pausa', '13:00', '16:00'],
      ['bloqueo', '17:00', '18:00'],
    ])
  })

  it('HU-17 CA5: un bloqueo de varios días se corta en cada día (de 00:00 a 24:00 los del medio)', () => {
    const vacaciones = unTurno({ tipo: 'bloqueo', inicio: '2026-11-16T15:00', fin: '2026-11-18T09:00' })

    const desde = (fecha: string) => itemsDelDia(fecha, 'ana', [], [vacaciones]).map((i) => [i.desde, i.hasta])

    expect(desde('2026-11-16')).toEqual([['15:00', '24:00']])
    expect(desde('2026-11-17')).toEqual([['00:00', '24:00']])
    expect(desde('2026-11-18')).toEqual([['00:00', '09:00']])
    expect(desde('2026-11-19')).toEqual([])
  })

  it('franjas pegadas no tienen pausa', () => {
    const pegadas = [franja('ana', '2026-11-17', '10:00', '13:00'), franja('ana', '2026-11-17', '13:00', '16:00')]

    expect(itemsDelDia('2026-11-17', 'ana', pegadas, [])).toEqual([])
  })
})

describe('textos', () => {
  it('el horario del día en palabras', () => {
    expect(textoDelHorario([franja('ana', 'x', '10:00', '13:00')])).toBe('10:00 a 13:00')
    expect(textoDelHorario(HORARIO_DEL_MARTES.slice().reverse())).toBe('10:00 a 13:00 y 16:00 a 20:00')
  })

  it('el estado del turno en palabras', () => {
    expect(textoDelEstado('confirmado')).toBe('Confirmado')
    expect(textoDelEstado('completado')).toBe('Vino')
    expect(textoDelEstado('ausente')).toBe('No vino')
  })
})
