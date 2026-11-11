import { describe, expect, it } from 'vitest'

import { formatearDuracion, formatearPrecio } from './formato'

describe('formatearPrecio', () => {
  it.each([
    [8000, '$ 8.000'],
    [14000, '$ 14.000'],
    [500, '$ 500'],
    [0, '$ 0'],
    [1250000, '$ 1.250.000'],
  ])('HU-01 CA1: %i pesos se muestra como "%s"', (pesos, esperado) => {
    expect(formatearPrecio(pesos)).toBe(esperado)
  })
})

describe('formatearDuracion', () => {
  it.each([
    [45, '45 min'],
    [5, '5 min'],
    [60, '1 h'],
    [75, '1 h 15 min'],
    [120, '2 h'],
    [90, '1 h 30 min'],
  ])('HU-01 CA1: %i minutos se muestra como "%s"', (minutos, esperado) => {
    expect(formatearDuracion(minutos)).toBe(esperado)
  })
})
