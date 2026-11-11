import { describe, expect, it } from 'vitest'

import { variablesDeAcento } from './acento'

// Los valores esperados son los que entregó el diseño junto con accent.js.
describe('variablesDeAcento', () => {
  it('verde oscuro (#2e7d32): texto blanco encima y tinta apenas más oscura', () => {
    const v = variablesDeAcento('#2e7d32')

    expect(v['--accent-contrast']).toBe('#ffffff')
    expect(v['--accent-ink']).toBe('#2d7b31')
    expect(v['--accent-edge']).toBe('#2e7d32')
  })

  it('rojo (#c0392b): ya contrasta, la tinta queda igual', () => {
    const v = variablesDeAcento('#c0392b')

    expect(v['--accent-contrast']).toBe('#ffffff')
    expect(v['--accent-ink']).toBe('#c0392b')
  })

  it('amarillo claro (#f1c40f): texto oscuro encima, tinta oscurecida y borde extra', () => {
    const v = variablesDeAcento('#f1c40f')

    expect(v['--accent-contrast']).toBe('#111827')
    expect(v['--accent-ink']).toBe('#876e08')
    expect(v['--accent-edge']).toBe('rgba(17, 24, 39, .18)')
  })

  it('sin color elegido usa el gris de Slotly', () => {
    const v = variablesDeAcento(null)

    expect(v['--accent']).toBe('#1f2937')
    expect(v['--accent-contrast']).toBe('#ffffff')
    expect(v['--accent-ink']).toBe('#1f2937')
  })

  it('un color inválido no rompe la página: cae en el gris de Slotly', () => {
    expect(variablesDeAcento('rojo')['--accent']).toBe('#1f2937')
  })
})
