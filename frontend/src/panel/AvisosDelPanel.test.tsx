import { fireEvent, render, screen, within } from '@testing-library/react'
import { Link, MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { marcarAvisosLeidos, verAvisos } from '../api/notificaciones'
import { obtenerUsuario } from '../api/panel'
import type { Aviso, UsuarioPanel } from '../api/tipos'
import { unLocal } from '../test/datos'
import { textoDelAviso } from './avisos'
import PanelConSesion from './PanelConSesion'

vi.mock('../api/panel', () => ({ obtenerUsuario: vi.fn(), salir: vi.fn() }))
vi.mock('../api/notificaciones', () => ({ verAvisos: vi.fn(), marcarAvisosLeidos: vi.fn() }))

const BRUNO: UsuarioPanel = { email: 'bruno@demo.com', rol: 'profesional', profesionalId: 'bruno' }
const DUENIO: UsuarioPanel = { email: 'duenio@demo.com', rol: 'admin', profesionalId: null }

function unAviso(datos: Partial<Aviso> = {}): Aviso {
  return {
    id: crypto.randomUUID(), tipo: 'reserva', fecha: '2026-11-17', hora: '10:00', fechaAnterior: null, horaAnterior: null,
    clienteNombre: 'Lucía', servicios: ['Corte'], creadaEn: '2026-11-16T09:01', leida: false, ...datos,
  }
}

beforeEach(() => {
  vi.mocked(obtenerUsuario).mockReset().mockResolvedValue({ estado: 'ok', datos: BRUNO })
  vi.mocked(verAvisos).mockReset().mockResolvedValue({
    estado: 'ok',
    datos: {
      sinLeer: 1,
      notificaciones: [
        unAviso({ tipo: 'cancelacion', clienteNombre: 'Pedro', hora: '12:00' }),
        unAviso({ leida: true }),
      ],
    },
  })
  vi.mocked(marcarAvisosLeidos).mockReset().mockResolvedValue({ estado: 'ok', datos: undefined })
})

function abrir() {
  render(
    <MemoryRouter initialEntries={['/agenda']}>
      <Routes>
        <Route element={<PanelConSesion local={unLocal()} />}>
          <Route path="/agenda" element={<Link to="/admin/servicios">Ir a servicios</Link>} />
          <Route path="/admin/servicios" element={<p>Servicios</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('Avisos del profesional', () => {
  it('HU-37 CA1: el encabezado muestra cuántos avisos no leyó y la lista, el detalle', async () => {
    abrir()

    fireEvent.click(await screen.findByRole('button', { name: 'Avisos, 1 sin leer' }))

    const lista = screen.getByRole('region', { name: 'Avisos' })
    const items = within(lista).getAllByRole('listitem')
    expect(within(items[0]).getByText('Nuevo')).toBeInTheDocument()
    expect(items[0]).toHaveTextContent('Se canceló el turno de Pedro del martes 17 de noviembre a las 12:00.')
    expect(items[0]).toHaveTextContent('lunes 16 de noviembre, 09:01')
    expect(within(items[1]).queryByText('Nuevo')).not.toBeInTheDocument()
    expect(items[1]).toHaveTextContent('Nuevo turno de Lucía: Corte, el martes 17 de noviembre a las 10:00.')
  })

  it('HU-37 CA2: al abrir la lista se marcan como leídos y el contador vuelve a cero', async () => {
    abrir()

    fireEvent.click(await screen.findByRole('button', { name: 'Avisos, 1 sin leer' }))

    expect(marcarAvisosLeidos).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Avisos' })).toHaveAttribute('aria-expanded', 'true')
    fireEvent.click(screen.getByRole('button', { name: 'Avisos' }))
    expect(screen.queryByRole('region', { name: 'Avisos' })).not.toBeInTheDocument()
    expect(marcarAvisosLeidos).toHaveBeenCalledTimes(1)
  })

  it('sin avisos sin leer, abrir la lista no pide marcarlos; sin avisos, lo dice', async () => {
    vi.mocked(verAvisos).mockResolvedValue({ estado: 'ok', datos: { sinLeer: 0, notificaciones: [] } })
    abrir()

    fireEvent.click(await screen.findByRole('button', { name: 'Avisos' }))

    expect(screen.getByText('Todavía no tenés avisos.')).toBeInTheDocument()
    expect(marcarAvisosLeidos).not.toHaveBeenCalled()
  })

  it('se vuelven a pedir al cambiar de página, sin consultar cada tantos segundos', async () => {
    abrir()
    await screen.findByRole('button', { name: 'Avisos, 1 sin leer' })
    expect(verAvisos).toHaveBeenCalledTimes(1)
    vi.mocked(verAvisos).mockResolvedValue({ estado: 'ok', datos: { sinLeer: 3, notificaciones: [] } })

    fireEvent.click(screen.getByRole('link', { name: 'Ir a servicios' }))

    expect(await screen.findByRole('button', { name: 'Avisos, 3 sin leer' })).toBeInTheDocument()
  })

  it('un admin sin agenda propia no tiene avisos', async () => {
    vi.mocked(obtenerUsuario).mockResolvedValue({ estado: 'ok', datos: DUENIO })
    abrir()

    expect(await screen.findByText('duenio@demo.com')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Avisos/ })).not.toBeInTheDocument()
    expect(verAvisos).not.toHaveBeenCalled()
  })
})

describe('Texto de cada aviso', () => {
  it('HU-37 CA1: reprogramado por el cliente y movido desde el panel, con el horario anterior', () => {
    expect(textoDelAviso(unAviso({ tipo: 'reprogramacion', hora: '11:00', fechaAnterior: '2026-11-17', horaAnterior: '10:00' })))
      .toBe('Lucía cambió su turno del martes 17 de noviembre a las 10:00 al martes 17 de noviembre a las 11:00.')
    expect(textoDelAviso(unAviso({ tipo: 'movido', clienteNombre: 'Pedro', fecha: '2026-11-18', hora: '12:00',
      fechaAnterior: '2026-11-17', horaAnterior: '11:00' })))
      .toBe('Se movió el turno de Pedro del martes 17 de noviembre a las 11:00 al miércoles 18 de noviembre a las 12:00.')
    expect(textoDelAviso(unAviso({ servicios: ['Corte', 'Barba'] })))
      .toBe('Nuevo turno de Lucía: Corte + Barba, el martes 17 de noviembre a las 10:00.')
  })
})
