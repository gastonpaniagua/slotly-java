import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { verResumen } from '../api/admin'
import type { UsuarioPanel } from '../api/tipos'
import PaginaResumen from './PaginaResumen'
import { ContextoSesion } from './sesion'

vi.mock('../api/admin', () => ({ verResumen: vi.fn() }))

const ADMIN: UsuarioPanel = { email: 'duenio@demo.com', rol: 'admin', profesionalId: null }

beforeEach(() => {
  vi.mocked(verResumen).mockReset().mockResolvedValue({
    estado: 'ok',
    datos: {
      hoy: '2026-11-17', semanaDesde: '2026-11-16', semanaHasta: '2026-11-22',
      turnosHoy: 2, turnosSemana: 4, ingresosSemana: 34000, faltasSemana: 1,
    },
  })
})

function abrir() {
  render(
    <ContextoSesion.Provider value={{ usuario: ADMIN, sesionPerdida: vi.fn() }}>
      <PaginaResumen />
    </ContextoSesion.Provider>,
  )
}

describe('Resumen de la semana', () => {
  it('HU-33 CA1: turnos de hoy y de la semana, ingresos estimados y faltas', async () => {
    abrir()

    expect(await screen.findByText('Semana del 16 al 22 de noviembre. Hoy es martes 17 de noviembre.')).toBeInTheDocument()
    expect(screen.getByText('Turnos de hoy').nextSibling).toHaveTextContent('2')
    expect(screen.getByText('Turnos de la semana').nextSibling).toHaveTextContent('4')
    expect(screen.getByText('Ingresos estimados de la semana').nextSibling).toHaveTextContent('$ 34.000')
    expect(screen.getByText('No vinieron esta semana').nextSibling).toHaveTextContent('1')
  })

  it('si no carga, lo dice y "Probar de nuevo" lo vuelve a pedir', async () => {
    vi.mocked(verResumen).mockResolvedValueOnce({ estado: 'error' })
    abrir()

    fireEvent.click(await screen.findByRole('button', { name: 'Probar de nuevo' }))

    expect(await screen.findByText('Turnos de hoy')).toBeInTheDocument()
  })
})
