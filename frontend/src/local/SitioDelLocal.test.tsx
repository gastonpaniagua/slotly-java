import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { unLocal } from '../test/datos'
import { renderConRouter } from '../test/router'
import SitioDelLocal from './SitioDelLocal'

describe('SitioDelLocal', () => {
  it('HU-03 CA1: aplica el color del local a todas sus pantallas', () => {
    const { container } = renderConRouter(<SitioDelLocal local={unLocal({ colorAcento: '#2e7d32' })} />)

    expect(container.querySelector('.sitio')).toHaveStyle({ '--accent': '#2e7d32', '--accent-contrast': '#ffffff' })
  })

  it('HU-03 CA1 y CA4: el título de la pestaña y la descripción son los del local', () => {
    document.head.innerHTML = '<meta name="description" content="genérica" />'

    renderConRouter(<SitioDelLocal local={unLocal({ nombre: 'Barbería Demo', descripcion: 'Cortes clásicos' })} />)

    expect(document.title).toBe('Barbería Demo')
    expect(document.querySelector('meta[name="description"]')).toHaveAttribute('content', 'Cortes clásicos')
  })

  it('una dirección desconocida vuelve a la home del local', () => {
    renderConRouter(<SitioDelLocal local={unLocal({ nombre: 'Barbería Demo' })} />, '/cualquier/cosa')

    expect(screen.getByRole('heading', { level: 1, name: 'Barbería Demo' })).toBeInTheDocument()
  })
})
