import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { obtenerTurno } from '../api/reserva'
import type { TurnoPublico } from '../api/tipos'
import PaginaTurno from './PaginaTurno'

vi.mock('../api/reserva', () => ({ obtenerTurno: vi.fn() }))

const TURNO: TurnoPublico = {
  id: 'abc', estado: 'confirmado', fecha: '2026-11-17', hora: '10:30', profesionalNombre: 'Bruno',
  servicios: [{ nombre: 'Corte', duracionMin: 30, precio: 8000 }, { nombre: 'Barba', duracionMin: 20, precio: 5000 }],
  duracionMin: 50, precioTotal: 13000, formaPago: 'transferencia',
  transferencia: { alias: 'barberia.demo', cbu: '0000003100000000000001', titular: 'Juan Pérez' },
}

beforeEach(() => {
  vi.mocked(obtenerTurno).mockReset()
})

function abrir(entrada: string | { pathname: string; hash: string; state: unknown }) {
  render(
    <MemoryRouter initialEntries={[entrada]}>
      <Routes>
        <Route path="/turno/:id" element={<PaginaTurno />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Mi turno', () => {
  it('pide el turno con el id de la dirección y el token que está después del #', async () => {
    vi.mocked(obtenerTurno).mockResolvedValue(TURNO)

    abrir('/turno/abc#t=x%2Fy')

    await screen.findByRole('heading', { name: 'Tu turno' })
    expect(obtenerTurno).toHaveBeenCalledWith('abc', 'x/y')
  })

  it('HU-10 CA1 y CA5: recién reservado muestra "¡Listo!", el día en palabras, profesional, servicios, total, pago y transferencia', async () => {
    vi.mocked(obtenerTurno).mockResolvedValue(TURNO)

    abrir({ pathname: '/turno/abc', hash: '#t=secreto', state: { recienConfirmado: true } })

    expect(await screen.findByRole('heading', { name: '¡Listo! Tu turno quedó reservado' })).toBeInTheDocument()
    const resumen = screen.getByRole('region', { name: 'Tu turno' })
    expect(resumen).toHaveTextContent('martes 17 de noviembre, 10:30')
    expect(resumen).toHaveTextContent('Con Bruno')
    expect(resumen).toHaveTextContent('Corte · 30 min · $ 8.000')
    expect(resumen).toHaveTextContent('Total: 50 min · $ 13.000')
    expect(resumen).toHaveTextContent('Pago: Transferencia')
    expect(screen.getByText('barberia.demo')).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '6')
  })

  it('HU-10 CA7: al recargar (sin el aviso de recién reservado) se ve el mismo turno, sin crear otro', async () => {
    vi.mocked(obtenerTurno).mockResolvedValue(TURNO)

    abrir('/turno/abc#t=secreto')

    expect(await screen.findByRole('heading', { name: 'Tu turno' })).toBeInTheDocument()
    expect(screen.getByText('martes 17 de noviembre, 10:30')).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('con un link roto o vencido lo dice, sin errores técnicos', async () => {
    vi.mocked(obtenerTurno).mockResolvedValue(null)

    abrir('/turno/abc#t=inventado')

    expect(await screen.findByRole('heading', { name: 'No encontramos ese turno' })).toBeInTheDocument()
  })

  it('un turno cancelado lo avisa', async () => {
    vi.mocked(obtenerTurno).mockResolvedValue({ ...TURNO, estado: 'cancelado' })

    abrir('/turno/abc#t=secreto')

    expect(await screen.findByRole('alert')).toHaveTextContent('Este turno está cancelado.')
  })
})
