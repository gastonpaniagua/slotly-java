import { fireEvent, render, screen } from '@testing-library/react'
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

    render(<App host="demo.localhost" />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Barbería Demo' })).toBeInTheDocument()
  })

  it('mientras carga muestra el esqueleto, anunciado como "Cargando…"', () => {
    vi.mocked(obtenerLocal).mockReturnValue(new Promise(() => {}))

    render(<App host="demo.localhost" />)

    expect(screen.getByRole('main')).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByText('Cargando…')).toBeInTheDocument()
  })

  it('HU-03 CA2: si el local no existe, lo dice sin detalles técnicos', async () => {
    vi.mocked(obtenerLocal).mockResolvedValue({ estado: 'no-existe' })

    render(<App host="noexiste.localhost" />)

    expect(await screen.findByRole('heading', { name: 'Este local no existe en Slotly' })).toBeInTheDocument()
  })

  it('HU-01 CA4: si la API falla, muestra un mensaje amable y deja probar de nuevo', async () => {
    vi.mocked(obtenerLocal)
      .mockResolvedValueOnce({ estado: 'error' })
      .mockResolvedValueOnce({ estado: 'ok', local: unLocal({ nombre: 'Barbería Demo' }) })

    render(<App host="demo.localhost" />)

    expect(await screen.findByRole('heading', { name: 'No pudimos cargar la página' })).toBeInTheDocument()
    expect(screen.getByText('Probá de nuevo en un rato.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Probar de nuevo' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Barbería Demo' })).toBeInTheDocument()
    expect(obtenerLocal).toHaveBeenCalledTimes(2)
  })

  it('HU-03 CA5: en el dominio raíz muestra la página de Slotly y no pide ningún local', () => {
    render(<App host="localhost" />)

    expect(screen.getByRole('heading', { level: 1, name: 'Slotly' })).toBeInTheDocument()
    expect(obtenerLocal).not.toHaveBeenCalled()
  })
})
