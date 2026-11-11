import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { obtenerLocal } from './api/publico'
import App from './App'
import { unLocal } from './test/datos'

// Se simula el cliente de la API: acá se prueba qué pantalla elige App para cada resultado.
vi.mock('./api/publico', () => ({ obtenerLocal: vi.fn() }))

afterEach(() => {
  vi.mocked(obtenerLocal).mockReset()
})

describe('App', () => {
  it('con el local, muestra su home', async () => {
    vi.mocked(obtenerLocal).mockResolvedValue({ estado: 'ok', local: unLocal({ nombre: 'Barbería Demo' }) })

    render(<App />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Barbería Demo' })).toBeInTheDocument()
  })

  it('HU-03 CA2: si el local no existe, lo dice sin detalles técnicos', async () => {
    vi.mocked(obtenerLocal).mockResolvedValue({ estado: 'no-existe' })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Este local no existe en Slotly' })).toBeInTheDocument()
  })

  it('HU-01 CA4: si la API falla, muestra un mensaje amable', async () => {
    vi.mocked(obtenerLocal).mockResolvedValue({ estado: 'error' })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'No pudimos cargar la página' })).toBeInTheDocument()
    expect(screen.getByText('Probá de nuevo en un rato.')).toBeInTheDocument()
  })
})
