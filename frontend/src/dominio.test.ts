import { describe, expect, it } from 'vitest'

import { esDominioRaiz } from './dominio'

describe('esDominioRaiz', () => {
  it.each([
    ['localhost', 'localhost'],
    ['slotly.com.ar', 'slotly.com.ar'],
    ['www.slotly.com.ar', 'slotly.com.ar'],
    ['SLOTLY.com.ar', 'slotly.com.ar'],
  ])('HU-03 CA5: "%s" es el dominio raíz de "%s"', (host, base) => {
    expect(esDominioRaiz(host, base)).toBe(true)
  })

  it.each([
    ['demo.localhost', 'localhost'],
    ['demo.slotly.com.ar', 'slotly.com.ar'],
    ['otrodominio.com', 'slotly.com.ar'],
  ])('"%s" no es el dominio raíz de "%s": se pide el local a la API', (host, base) => {
    expect(esDominioRaiz(host, base)).toBe(false)
  })
})
