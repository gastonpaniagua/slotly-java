import { afterEach, describe, expect, it, vi } from 'vitest'

import { obtenerLocal } from './publico'

// El fetch se reemplaza por uno simulado (doc 06): acá se prueba cómo se interpreta cada respuesta.
function responder(status: number, cuerpo: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
    new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } }),
  ))
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('obtenerLocal', () => {
  it('con 200 devuelve el local', async () => {
    responder(200, { nombre: 'Barbería Demo', servicios: [], horarios: [] })

    const resultado = await obtenerLocal()

    expect(resultado).toEqual({ estado: 'ok', local: { nombre: 'Barbería Demo', servicios: [], horarios: [] } })
  })

  it('HU-03 CA2: con 404 LOCAL_NO_ENCONTRADO avisa que el local no existe', async () => {
    responder(404, { status: 404, codigo: 'LOCAL_NO_ENCONTRADO' })

    expect(await obtenerLocal()).toEqual({ estado: 'no-existe' })
  })

  it('HU-01 CA4: con un 500 devuelve error, no "no existe"', async () => {
    responder(500, { status: 500, codigo: 'ERROR_INTERNO' })

    expect(await obtenerLocal()).toEqual({ estado: 'error' })
  })

  it('HU-01 CA4: si la API no responde (fetch falla) devuelve error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    expect(await obtenerLocal()).toEqual({ estado: 'error' })
  })

  it('un 404 sin nuestro código (por ejemplo, del proxy) es un error, no "no existe"', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('Not Found', { status: 404 })))

    expect(await obtenerLocal()).toEqual({ estado: 'error' })
  })
})
