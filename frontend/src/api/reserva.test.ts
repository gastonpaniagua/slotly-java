import { afterEach, describe, expect, it, vi } from 'vitest'

import { cancelarTurno, obtenerHorasParaReprogramar, obtenerTurno, reprogramarTurno } from './reserva'

function responder(status: number, cuerpo: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
    new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } }),
  ))
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('obtenerTurno', () => {
  it('manda el token en el header X-Token-Turno (no en la dirección)', async () => {
    responder(200, { id: 'abc' })

    expect(await obtenerTurno('abc', 'secreto')).toEqual({ estado: 'ok', turno: { id: 'abc' } })
    expect(fetch).toHaveBeenCalledWith('/api/publico/turnos/abc', { headers: { 'X-Token-Turno': 'secreto' } })
  })

  it('HU-19 CA2: TURNO_NO_ENCONTRADO es un link inválido', async () => {
    responder(404, { codigo: 'TURNO_NO_ENCONTRADO' })

    expect(await obtenerTurno('abc', 'x')).toEqual({ estado: 'invalido' })
  })

  it('HU-19 CA3: LINK_VENCIDO es un link vencido', async () => {
    responder(410, { codigo: 'LINK_VENCIDO' })

    expect(await obtenerTurno('abc', 'x')).toEqual({ estado: 'vencido' })
  })

  it('otro error, o la API caída, es "error" (no "link inválido")', async () => {
    responder(500, { codigo: 'ERROR_INTERNO' })
    expect(await obtenerTurno('abc', 'x')).toEqual({ estado: 'error' })

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    expect(await obtenerTurno('abc', 'x')).toEqual({ estado: 'error' })
  })
})

describe('cancelarTurno', () => {
  it('HU-20: hace un POST con el token en el header y devuelve el turno', async () => {
    responder(200, { id: 'abc', estado: 'cancelado' })

    expect(await cancelarTurno('abc', 'secreto')).toEqual({ estado: 'ok', turno: { id: 'abc', estado: 'cancelado' } })
    expect(fetch).toHaveBeenCalledWith('/api/publico/turnos/abc/cancelar',
      { method: 'POST', headers: { 'X-Token-Turno': 'secreto' } })
  })

  it('HU-20 CA2: FUERA_DE_PLAZO es "fuera-de-plazo"', async () => {
    responder(409, { codigo: 'FUERA_DE_PLAZO' })

    expect(await cancelarTurno('abc', 'x')).toEqual({ estado: 'fuera-de-plazo' })
  })

  it('link inválido, vencido o error', async () => {
    responder(404, { codigo: 'TURNO_NO_ENCONTRADO' })
    expect(await cancelarTurno('abc', 'x')).toEqual({ estado: 'invalido' })
    responder(410, { codigo: 'LINK_VENCIDO' })
    expect(await cancelarTurno('abc', 'x')).toEqual({ estado: 'vencido' })
    responder(500, { codigo: 'ERROR_INTERNO' })
    expect(await cancelarTurno('abc', 'x')).toEqual({ estado: 'error' })
  })
})

describe('reprogramar', () => {
  it('HU-21: las horas se piden con el token y sin servicios ni profesional (la API usa los del turno)', async () => {
    responder(200, { fecha: '2026-11-18', horas: ['10:00'] })

    expect(await obtenerHorasParaReprogramar('abc', 'secreto', '2026-11-18'))
      .toEqual({ estado: 'ok', datos: { fecha: '2026-11-18', horas: ['10:00'] } })
    expect(fetch).toHaveBeenCalledWith('/api/publico/turnos/abc/reprogramar/horas?fecha=2026-11-18',
      { headers: { 'X-Token-Turno': 'secreto' } })
  })

  it('HU-21 CA2: reprogramar devuelve el id y el token del turno nuevo', async () => {
    responder(200, { id: 'nuevo', token: 't2', turno: { id: 'nuevo' } })

    expect(await reprogramarTurno('abc', 'secreto', '2026-11-18', '11:00'))
      .toEqual({ estado: 'ok', id: 'nuevo', token: 't2', turno: { id: 'nuevo' } })
    const [url, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/publico/turnos/abc/reprogramar')
    expect(JSON.parse(opciones?.body as string)).toEqual({ fecha: '2026-11-18', hora: '11:00' })
  })

  it('HU-21 CA3 y CA4: HORARIO_OCUPADO y FUERA_DE_PLAZO', async () => {
    responder(409, { codigo: 'HORARIO_OCUPADO' })
    expect(await reprogramarTurno('abc', 'x', '2026-11-18', '11:00')).toEqual({ estado: 'ocupado' })
    responder(409, { codigo: 'FUERA_DE_PLAZO' })
    expect(await reprogramarTurno('abc', 'x', '2026-11-18', '11:00')).toEqual({ estado: 'fuera-de-plazo' })
  })
})
