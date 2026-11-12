import { afterEach, describe, expect, it, vi } from 'vitest'

import { leerCliente, leerTurnos, linkDelTurno, recordarCliente, recordarTurno } from './memoria'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('memoria del navegador', () => {
  it('HU-10 CA9: recuerda nombre y celular', () => {
    expect(leerCliente()).toBeNull()

    recordarCliente({ nombre: 'Lucía', telefono: '11 2345-6789' })

    expect(leerCliente()).toEqual({ nombre: 'Lucía', telefono: '11 2345-6789' })
  })

  it('HU-10 CA9: recuerda los turnos ordenados por fecha, sin repetir y como mucho 10', () => {
    recordarTurno({ id: 'b', token: 't2', fecha: '2026-11-20', hora: '10:00' })
    recordarTurno({ id: 'a', token: 't1', fecha: '2026-11-17', hora: '16:00' })
    recordarTurno({ id: 'a', token: 't1', fecha: '2026-11-17', hora: '16:00' })

    expect(leerTurnos().map((t) => t.id)).toEqual(['a', 'b'])

    for (let i = 0; i < 12; i++) {
      recordarTurno({ id: `extra-${i}`, token: 't', fecha: '2026-12-01', hora: `1${i % 10}:00` })
    }
    expect(leerTurnos()).toHaveLength(10)
  })

  it('un valor roto en el almacenamiento no rompe la página', () => {
    localStorage.setItem('slotly.cliente', '{no es json')
    localStorage.setItem('slotly.turnos', '"no es una lista"')

    expect(leerCliente()).toBeNull()
    expect(leerTurnos()).toEqual([])
  })

  it('sin almacenamiento disponible (modo incógnito, bloqueado) no falla: solo no recuerda', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('bloqueado') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('bloqueado') })

    expect(() => recordarCliente({ nombre: 'Lucía', telefono: '11 2345-6789' })).not.toThrow()
    expect(leerCliente()).toBeNull()
  })

  it('el token va después del # del link, la parte que no se manda al servidor', () => {
    expect(linkDelTurno({ id: 'abc', token: 'x/y+z' })).toBe('/turno/abc#t=x%2Fy%2Bz')
  })
})
