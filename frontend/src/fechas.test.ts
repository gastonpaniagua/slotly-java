import { describe, expect, it } from 'vitest'

import { diasDeLaSemana, fechaLarga, lunesDe, nombreCortoDelDia, sumarDias, textoDeLaSemana } from './fechas'

describe('fechas', () => {
  it('suma días cruzando meses y años', () => {
    expect(sumarDias('2026-11-30', 1)).toBe('2026-12-01')
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01')
    expect(sumarDias('2026-11-17', -17)).toBe('2026-10-31')
  })

  it.each([
    ['2026-11-16', '2026-11-16'], // lunes
    ['2026-11-17', '2026-11-16'], // martes
    ['2026-11-22', '2026-11-16'], // domingo: es el último día de esa semana
    ['2026-12-02', '2026-11-30'], // semana que cruza de mes
  ])('el lunes de la semana de %s es %s', (fecha, lunes) => {
    expect(lunesDe(fecha)).toBe(lunes)
  })

  it('la semana tiene 7 días de lunes a domingo', () => {
    expect(diasDeLaSemana('2026-11-16')).toEqual([
      '2026-11-16', '2026-11-17', '2026-11-18', '2026-11-19', '2026-11-20', '2026-11-21', '2026-11-22',
    ])
  })

  it('nombra los días y las semanas en castellano', () => {
    expect(nombreCortoDelDia('2026-11-17')).toBe('mar')
    expect(fechaLarga('2026-11-17')).toBe('martes 17 de noviembre')
    expect(textoDeLaSemana('2026-11-16')).toBe('16 al 22 de noviembre')
    expect(textoDeLaSemana('2026-11-30')).toBe('30 de noviembre al 6 de diciembre')
  })
})
