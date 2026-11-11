import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { unLocal, unServicio } from '../test/datos'
import { renderConRouter } from '../test/router'
import Home from './Home'

// El color y el título de la pestaña se prueban en SitioDelLocal.test.tsx.
describe('Home', () => {
  it('HU-03 CA1: muestra el nombre del local', () => {
    renderConRouter(<Home local={unLocal({ nombre: 'Barbería Demo' })} />)

    expect(screen.getByRole('heading', { level: 1, name: 'Barbería Demo' })).toBeInTheDocument()
  })

  it('con servicios muestra "Reservar turno" hacia el paso 1; sin servicios, no', () => {
    const { unmount } = renderConRouter(<Home local={unLocal({ servicios: [unServicio()] })} />)
    expect(screen.getByRole('link', { name: 'Reservar turno' })).toHaveAttribute('href', '/reservar/servicios')
    unmount()

    renderConRouter(<Home local={unLocal({ servicios: [] })} />)
    expect(screen.queryByRole('link', { name: 'Reservar turno' })).not.toBeInTheDocument()
  })

  it('HU-03 CA1: con logo muestra la imagen; sin logo, la inicial', () => {
    const { unmount } = renderConRouter(<Home local={unLocal({ nombre: 'Barbería Demo', logoUrl: 'https://x/logo.png' })} />)
    expect(screen.getByRole('img', { name: 'Logo de Barbería Demo' })).toHaveAttribute('src', 'https://x/logo.png')
    unmount()

    renderConRouter(<Home local={unLocal({ nombre: 'barbería demo', logoUrl: null })} />)
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(screen.getByText('B')).toBeInTheDocument()
  })

  it('HU-01 CA1: muestra cada servicio con descripción, precio y duración, en el orden recibido', () => {
    renderConRouter(<Home local={unLocal({
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

  it('HU-02 CA1: muestra la semana de lunes a domingo, con sus franjas y "Cerrado" en los días sin horario', () => {
    const franjas = [{ desde: '10:00', hasta: '13:00' }, { desde: '16:00', hasta: '20:00' }]
    renderConRouter(<Home local={unLocal({
      horarios: [0, 1, 2, 3, 4, 5, 6].map((dia) => ({ diaSemana: dia, franjas: dia >= 2 ? franjas : [] })),
    })} />)

    const dias = within(screen.getByRole('region', { name: 'Horarios de atención' })).getAllByRole('term')
    expect(dias.map((dia) => dia.textContent)).toEqual(
      ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'])
    expect(dias[0].nextElementSibling).toHaveTextContent('Cerrado')
    expect(dias[1].nextElementSibling).toHaveTextContent('10:00 a 13:00 y 16:00 a 20:00')
    expect(dias[6].nextElementSibling).toHaveTextContent('Cerrado')
  })

  it('HU-02 CA3: con dirección muestra la dirección, el mapa y "Cómo llegar" a Google Maps', () => {
    renderConRouter(<Home local={unLocal({ nombre: 'Barbería Demo', direccion: 'San Martín 1200', ciudad: 'Villa María' })} />)

    const ubicacion = screen.getByRole('region', { name: 'Ubicación' })
    expect(ubicacion).toHaveTextContent('San Martín 1200, Villa María')
    expect(within(ubicacion).getByTitle('Mapa con la ubicación de Barbería Demo'))
      .toHaveAttribute('src', expect.stringContaining('q=San%20Mart%C3%ADn%201200%2C%20Villa%20Mar%C3%ADa'))
    const comoLlegar = within(ubicacion).getByRole('link', { name: 'Cómo llegar' })
    expect(comoLlegar).toHaveAttribute('href',
      'https://www.google.com/maps/dir/?api=1&destination=San%20Mart%C3%ADn%201200%2C%20Villa%20Mar%C3%ADa')
    expect(comoLlegar).toHaveAttribute('target', '_blank')
  })

  it('HU-02 CA3: con coordenadas, el mapa y "Cómo llegar" usan el punto exacto', () => {
    renderConRouter(<Home local={unLocal({ direccion: 'San Martín 1200', latitud: -32.41, longitud: -63.24 })} />)

    expect(screen.getByRole('link', { name: 'Cómo llegar' }))
      .toHaveAttribute('href', 'https://www.google.com/maps/dir/?api=1&destination=-32.41%2C-63.24')
  })

  it('HU-02 CA4: sin dirección no aparece la sección de ubicación ni el mapa', () => {
    renderConRouter(<Home local={unLocal({ direccion: null })} />)

    expect(screen.queryByRole('region', { name: 'Ubicación' })).not.toBeInTheDocument()
    expect(document.querySelector('iframe')).not.toBeInTheDocument()
  })

  it('HU-01 CA3: sin servicios muestra "Todavía no hay servicios para reservar"', () => {
    renderConRouter(<Home local={unLocal({ servicios: [] })} />)

    expect(screen.getByText('Todavía no hay servicios para reservar.')).toBeInTheDocument()
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
  })
})
