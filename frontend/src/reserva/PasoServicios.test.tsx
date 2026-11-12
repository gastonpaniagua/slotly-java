import { fireEvent, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { obtenerProfesionales } from '../api/publico'
import SitioDelLocal from '../local/SitioDelLocal'
import { unLocal, unServicio } from '../test/datos'
import { renderConRouter } from '../test/router'

// "Siguiente" pide los profesionales: se simula una respuesta con dos, así siempre se pasa al paso 2.
vi.mock('../api/publico', () => ({ obtenerProfesionales: vi.fn(), obtenerDias: vi.fn() }))

beforeEach(() => {
  vi.mocked(obtenerProfesionales).mockReset().mockResolvedValue({
    estado: 'ok',
    profesionales: [{ id: 'ana', nombre: 'Ana', fotoUrl: null }, { id: 'bruno', nombre: 'Bruno', fotoUrl: null }],
  })
})

// Se prueba a través de SitioDelLocal para que funcionen las rutas reales (/, /reservar/...).
function abrirReserva(ruta = '/reservar/servicios') {
  const local = unLocal({
    servicios: [
      unServicio({ id: 'corte', nombre: 'Corte', duracionMin: 45, precio: 8000 }),
      unServicio({ id: 'barba', nombre: 'Barba', duracionMin: 30, precio: 5000 }),
    ],
  })
  return renderConRouter(<SitioDelLocal local={local} />, ruta)
}

describe('Paso 1: servicios', () => {
  it('HU-05 CA1: al elegir Corte (45 min, $ 8.000) y Barba (30 min, $ 5.000) el resumen dice "1 h 15 min · $ 13.000"', () => {
    abrirReserva()

    fireEvent.click(screen.getByRole('checkbox', { name: /Corte/ }))
    fireEvent.click(screen.getByRole('checkbox', { name: /Barba/ }))

    expect(screen.getByRole('status', { name: 'Resumen' })).toHaveTextContent('1 h 15 min · $ 13.000')
  })

  it('HU-05 CA2: sin servicios elegidos, "Siguiente" está deshabilitado y no hay resumen', () => {
    abrirReserva()

    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
    expect(screen.queryByRole('status', { name: 'Resumen' })).not.toBeInTheDocument()

    // Al elegir uno se habilita, y al sacarlo vuelve a deshabilitarse.
    fireEvent.click(screen.getByRole('checkbox', { name: /Corte/ }))
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeEnabled()
    fireEvent.click(screen.getByRole('checkbox', { name: /Corte/ }))
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
  })

  it('HU-05 CA3: después de avanzar, "Atrás" vuelve con la selección marcada', async () => {
    abrirReserva()
    fireEvent.click(screen.getByRole('checkbox', { name: /Barba/ }))

    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(await screen.findByRole('heading', { name: 'Elegí con quién' })).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '2')

    fireEvent.click(screen.getByRole('link', { name: 'Atrás' }))
    expect(screen.getByRole('checkbox', { name: /Barba/ })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /Corte/ })).not.toBeChecked()
  })

  it('HU-05 CA4: muestra la barra de progreso en el paso 1 de 6', () => {
    abrirReserva()

    expect(screen.getByText(/Paso 1 de 6/)).toBeInTheDocument()
    const barra = screen.getByRole('progressbar', { name: 'Progreso de la reserva' })
    expect(barra).toHaveAttribute('aria-valuenow', '1')
    expect(barra).toHaveAttribute('aria-valuemax', '6')
  })

  it('desde la home, "Reservar turno" abre el paso 1', () => {
    abrirReserva('/')

    fireEvent.click(screen.getByRole('link', { name: 'Reservar turno' }))

    expect(screen.getByRole('heading', { name: 'Elegí los servicios' })).toBeInTheDocument()
  })

  it('entrar directo al paso 2 sin servicios elegidos lleva al paso 1', () => {
    abrirReserva('/reservar/profesional')

    expect(screen.getByRole('heading', { name: 'Elegí los servicios' })).toBeInTheDocument()
  })
})
