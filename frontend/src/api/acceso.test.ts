import { afterEach, describe, expect, it, vi } from 'vitest'

import { obtenerMisTurnos, pedirCodigo, verificarCodigo } from './acceso'

function responder(status: number, cuerpo: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
    new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } }),
  ))
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('pedirCodigo', () => {
  it('HU-22 CA1 y CA2: con 202 devuelve cuánto dura el código (la misma respuesta para cualquier teléfono)', async () => {
    responder(202, { vigenciaMinutos: 10 })

    expect(await pedirCodigo('11 2345-6789')).toEqual({ estado: 'ok', vigenciaMinutos: 10 })
    const [url, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/publico/codigos')
    expect(JSON.parse(opciones?.body as string)).toEqual({ telefono: '11 2345-6789' })
  })

  it('un celular inválido trae el mensaje del campo', async () => {
    responder(400, { codigo: 'VALIDACION', errores: [{ campo: 'telefono', mensaje: 'Revisá el número de celular.' }] })

    expect(await pedirCodigo('123')).toEqual({ estado: 'validacion', mensaje: 'Revisá el número de celular.' })
  })

  it('HU-22 CA6: DEMASIADOS_INTENTOS es "demasiados"', async () => {
    responder(429, { codigo: 'DEMASIADOS_INTENTOS' })

    expect(await pedirCodigo('11 2345-6789')).toEqual({ estado: 'demasiados' })
  })
})

describe('verificarCodigo', () => {
  it('HU-22 CA3: con el código correcto devuelve el acceso', async () => {
    responder(200, { token: 'acceso-1', venceEn: '2026-12-16T12:00:00Z' })

    expect(await verificarCodigo('11 2345-6789', '123456'))
      .toEqual({ estado: 'ok', acceso: { token: 'acceso-1', venceEn: '2026-12-16T12:00:00Z' } })
  })

  it('HU-22 CA4 y CA5: CODIGO_INVALIDO es "invalido"', async () => {
    responder(400, { codigo: 'CODIGO_INVALIDO' })

    expect(await verificarCodigo('11 2345-6789', '000000')).toEqual({ estado: 'invalido' })
  })
})

describe('obtenerMisTurnos', () => {
  it('manda el acceso en el header X-Acceso-Cliente', async () => {
    responder(200, { proximos: [], historial: [] })

    expect(await obtenerMisTurnos('acceso-1')).toEqual({ estado: 'ok', datos: { proximos: [], historial: [] } })
    expect(fetch).toHaveBeenCalledWith('/api/publico/mis-turnos', { headers: { 'X-Acceso-Cliente': 'acceso-1' } })
  })

  it('con 401 el acceso ya no sirve: "sin-acceso"', async () => {
    responder(401, { codigo: 'NO_AUTENTICADO' })

    expect(await obtenerMisTurnos('viejo')).toEqual({ estado: 'sin-acceso' })
  })
})
