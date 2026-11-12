import { afterEach, describe, expect, it, vi } from 'vitest'

import { ingresar, pedirDelPanel } from './panel'

function json(status: number, cuerpo: unknown) {
  return new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } })
}

function borrarTokenCsrf() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}

afterEach(() => {
  vi.unstubAllGlobals()
  borrarTokenCsrf()
})

describe('ingresar', () => {
  it('D1: manda el token CSRF de la cookie XSRF-TOKEN en el header X-XSRF-TOKEN', async () => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
    const fetchSimulado = vi.fn().mockResolvedValue(json(200, { email: 'ana@demo.com', rol: 'profesional', profesionalId: 'a' }))
    vi.stubGlobal('fetch', fetchSimulado)

    const resultado = await ingresar('ana@demo.com', 'clave')

    expect(resultado).toEqual({ estado: 'ok', usuario: { email: 'ana@demo.com', rol: 'profesional', profesionalId: 'a' } })
    expect(fetchSimulado).toHaveBeenCalledTimes(1)
    const [url, opciones] = fetchSimulado.mock.calls[0]
    expect(url).toBe('/api/panel/ingresar')
    expect(opciones.method).toBe('POST')
    expect(opciones.headers['X-XSRF-TOKEN']).toBe('token-123')
    expect(JSON.parse(opciones.body)).toEqual({ email: 'ana@demo.com', password: 'clave' })
  })

  it('D1: sin la cookie del token todavía, primero la pide (con "quién soy") y después manda el POST con el token', async () => {
    const fetchSimulado = vi.fn()
      .mockImplementationOnce(async () => {
        document.cookie = 'XSRF-TOKEN=recien-llegado; path=/'
        return json(401, { codigo: 'NO_AUTENTICADO' })
      })
      .mockResolvedValueOnce(json(200, { email: 'ana@demo.com', rol: 'profesional', profesionalId: 'a' }))
    vi.stubGlobal('fetch', fetchSimulado)

    await ingresar('ana@demo.com', 'clave')

    expect(fetchSimulado.mock.calls[0][0]).toBe('/api/panel/yo')
    expect(fetchSimulado.mock.calls[1][1].headers['X-XSRF-TOKEN']).toBe('recien-llegado')
  })

  it('HU-16 CA2: CREDENCIALES_INVALIDAS es "invalido"', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(401, { codigo: 'CREDENCIALES_INVALIDAS' })))

    expect(await ingresar('ana@demo.com', 'mal')).toEqual({ estado: 'invalido' })
  })

  it('HU-16 CA3: DEMASIADOS_INTENTOS es "demasiados"', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(429, { codigo: 'DEMASIADOS_INTENTOS' })))

    expect(await ingresar('ana@demo.com', 'clave')).toEqual({ estado: 'demasiados' })
  })

  it('si la API no responde, es "error"', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    expect(await ingresar('ana@demo.com', 'clave')).toEqual({ estado: 'error' })
  })
})

describe('pedirDelPanel', () => {
  it('HU-16 CA6 y CA7: un 401 es "sin-sesion" (no hay sesión o se cerró en otra pestaña)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(401, { codigo: 'NO_AUTENTICADO' })))

    expect(await pedirDelPanel('/api/panel/agenda')).toEqual({ estado: 'sin-sesion' })
  })

  it('otro error es "error"', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(500, { codigo: 'ERROR_INTERNO' })))

    expect(await pedirDelPanel('/api/panel/agenda')).toEqual({ estado: 'error' })
  })
})
