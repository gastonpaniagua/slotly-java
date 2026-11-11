import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { unLocal, unServicio } from '../test/datos'
import Home from './Home'

describe('Home', () => {
  it('HU-03 CA1: muestra el nombre y usa el color del local', () => {
    const { container } = render(<Home local={unLocal({ nombre: 'Barbería Demo', colorAcento: '#2e7d32' })} />)

    expect(screen.getByRole('heading', { level: 1, name: 'Barbería Demo' })).toBeInTheDocument()
    expect(container.querySelector('.home')).toHaveStyle({ '--accent': '#2e7d32', '--accent-contrast': '#ffffff' })
  })

  it('HU-03 CA1 y CA4: el título de la pestaña y la descripción son los del local', () => {
    document.head.innerHTML = '<meta name="description" content="genérica" />'

    render(<Home local={unLocal({ nombre: 'Barbería Demo', descripcion: 'Cortes clásicos' })} />)

    expect(document.title).toBe('Barbería Demo')
    expect(document.querySelector('meta[name="description"]')).toHaveAttribute('content', 'Cortes clásicos')
  })

  it('HU-03 CA1: con logo muestra la imagen; sin logo, la inicial', () => {
    const { unmount } = render(<Home local={unLocal({ nombre: 'Barbería Demo', logoUrl: 'https://x/logo.png' })} />)
    expect(screen.getByRole('img', { name: 'Logo de Barbería Demo' })).toHaveAttribute('src', 'https://x/logo.png')
    unmount()

    render(<Home local={unLocal({ nombre: 'barbería demo', logoUrl: null })} />)
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(screen.getByText('B')).toBeInTheDocument()
  })

  it('HU-01 CA1: muestra cada servicio con descripción, precio y duración, en el orden recibido', () => {
    render(<Home local={unLocal({
      servicios: [
        unServicio({ nombre: 'Corte y barba', descripcion: 'El combo completo', precio: 20000, duracionMin: 75 }),
        unServicio({ nombre: 'Barba', precio: 8000, duracionMin: 20 }),
      ],
    })} />)

    const servicios = screen.getAllByRole('listitem')
    expect(servicios).toHaveLength(2)
    expect(within(servicios[0]).getByRole('heading', { name: 'Corte y barba' })).toBeInTheDocument()
    expect(servicios[0]).toHaveTextContent('El combo completo')
    expect(servicios[0]).toHaveTextContent('$ 20.000')
    expect(servicios[0]).toHaveTextContent('1 h 15 min')
    expect(within(servicios[1]).getByRole('heading', { name: 'Barba' })).toBeInTheDocument()
    expect(servicios[1]).toHaveTextContent('$ 8.000')
    expect(servicios[1]).toHaveTextContent('20 min')
  })

  it('HU-01 CA3: sin servicios muestra "Todavía no hay servicios para reservar"', () => {
    render(<Home local={unLocal({ servicios: [] })} />)

    expect(screen.getByText('Todavía no hay servicios para reservar.')).toBeInTheDocument()
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
  })
})
