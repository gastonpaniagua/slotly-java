import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { obtenerMisTurnos, pedirCodigo, verificarCodigo } from '../api/acceso'
import { obtenerTurno } from '../api/reserva'
import type { TurnoDelCliente, TurnoPublico } from '../api/tipos'
import { unLocal } from '../test/datos'
import { leerAcceso, recordarAcceso, recordarCliente, recordarTurno } from './memoria'
import PaginaEntrar from './PaginaEntrar'
import PaginaMisTurnos from './PaginaMisTurnos'
import PaginaTurno from './PaginaTurno'

vi.mock('../api/acceso', () => ({ pedirCodigo: vi.fn(), verificarCodigo: vi.fn(), obtenerMisTurnos: vi.fn() }))
vi.mock('../api/reserva', () => ({ obtenerTurno: vi.fn(), cancelarTurno: vi.fn() }))

const TURNO: TurnoPublico = {
  id: 'proximo', estado: 'confirmado', fecha: '2026-11-17', hora: '10:00', profesionalNombre: 'Ana',
  servicios: [{ nombre: 'Corte', duracionMin: 30, precio: 8000 }], duracionMin: 30, precioTotal: 8000,
  formaPago: 'efectivo', transferencia: null, yaPaso: false, puedeCambiar: true,
}

function delCliente(turno: Partial<TurnoPublico>): TurnoDelCliente {
  return { turno: { ...TURNO, ...turno }, profesionalId: 'ana', servicioIds: ['corte'] }
}

// Muestra a dónde navegó y con qué estado, para comprobar "Reservar lo mismo".
function Destino() {
  const ubicacion = useLocation()
  return <p data-testid="destino">{ubicacion.pathname} {JSON.stringify(ubicacion.state)}</p>
}

function abrir(ruta: string) {
  render(
    <MemoryRouter initialEntries={[ruta]}>
      <Routes>
        <Route path="/entrar" element={<PaginaEntrar />} />
        <Route path="/mis-turnos" element={<PaginaMisTurnos />} />
        <Route path="/turno/:id" element={<PaginaTurno local={unLocal()} />} />
        <Route path="/reservar/*" element={<Destino />} />
      </Routes>
    </MemoryRouter>,
  )
}

const UN_MES = new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString()

beforeEach(() => {
  vi.mocked(pedirCodigo).mockReset().mockResolvedValue({ estado: 'ok', vigenciaMinutos: 10 })
  vi.mocked(verificarCodigo).mockReset()
  vi.mocked(obtenerMisTurnos).mockReset()
  vi.mocked(obtenerTurno).mockReset().mockResolvedValue({ estado: 'ok', turno: TURNO })
})

describe('Entrar con mi teléfono', () => {
  it('HU-22 CA1 a CA3: pide el código, lo ingresa y entra a sus turnos (el acceso queda en el navegador)', async () => {
    recordarCliente({ nombre: 'Lucía', telefono: '11 2345-6789' })
    vi.mocked(verificarCodigo).mockResolvedValue({ estado: 'ok', acceso: { token: 'acceso-1', venceEn: UN_MES } })
    vi.mocked(obtenerMisTurnos).mockResolvedValue({ estado: 'ok', datos: { proximos: [], historial: [] } })
    abrir('/entrar')

    // El celular viene precargado si ya reservó desde este navegador.
    expect(screen.getByLabelText('Celular')).toHaveValue('11 2345-6789')
    fireEvent.click(screen.getByRole('button', { name: 'Pedir código' }))
    expect(await screen.findByText(/Si tenés turnos en este local, te mandamos un código de 6 números/)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Código'), { target: { value: '482913' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Mis turnos' })).toBeInTheDocument()
    expect(pedirCodigo).toHaveBeenCalledWith('11 2345-6789')
    expect(verificarCodigo).toHaveBeenCalledWith('11 2345-6789', '482913')
    expect(leerAcceso()?.token).toBe('acceso-1')
  })

  it('HU-22 CA4 y CA5: un código incorrecto o vencido lo dice y no entra', async () => {
    vi.mocked(verificarCodigo).mockResolvedValue({ estado: 'invalido' })
    abrir('/entrar')
    fireEvent.change(screen.getByLabelText('Celular'), { target: { value: '11 2345-6789' } })
    fireEvent.click(screen.getByRole('button', { name: 'Pedir código' }))
    fireEvent.change(await screen.findByLabelText('Código'), { target: { value: '000000' } })

    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El código no es válido o venció. Revisalo o pedí uno nuevo.')
    expect(leerAcceso()).toBeNull()
  })

  it('HU-22 CA6: con demasiados pedidos lo dice', async () => {
    vi.mocked(pedirCodigo).mockResolvedValue({ estado: 'demasiados' })
    abrir('/entrar')
    fireEvent.change(screen.getByLabelText('Celular'), { target: { value: '11 2345-6789' } })

    fireEvent.click(screen.getByRole('button', { name: 'Pedir código' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Pediste muchos códigos. Probá de nuevo en una hora.')
  })

  it('un código que no tiene 6 números no se manda', async () => {
    abrir('/entrar')
    fireEvent.change(screen.getByLabelText('Celular'), { target: { value: '11 2345-6789' } })
    fireEvent.click(screen.getByRole('button', { name: 'Pedir código' }))
    fireEvent.change(await screen.findByLabelText('Código'), { target: { value: '12' } })

    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(screen.getByRole('alert')).toHaveTextContent('El código tiene 6 números.')
    expect(verificarCodigo).not.toHaveBeenCalled()
  })
})

describe('Mis turnos', () => {
  it('sin haber entrado: los turnos de este navegador y la opción de entrar con el teléfono', () => {
    recordarTurno({ id: 'abc', token: 't', fecha: '2026-11-17', hora: '10:00' })

    abrir('/mis-turnos')

    expect(screen.getByRole('link', { name: /martes 17 de noviembre, 10:00/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Entrar con mi teléfono' })).toHaveAttribute('href', '/entrar')
    expect(obtenerMisTurnos).not.toHaveBeenCalled()
  })

  it('HU-22 CA3 y HU-23 CA1: con el acceso muestra los próximos y el historial con su estado', async () => {
    recordarAcceso({ token: 'acceso-1', venceEn: UN_MES })
    vi.mocked(obtenerMisTurnos).mockResolvedValue({ estado: 'ok', datos: {
      proximos: [delCliente({ id: 'proximo' })],
      historial: [
        delCliente({ id: 'cancelado', fecha: '2026-11-10', estado: 'cancelado' }),
        delCliente({ id: 'viejo', fecha: '2026-11-03', estado: 'completado' }),
      ],
    } })

    abrir('/mis-turnos')

    const proximos = await screen.findByRole('region', { name: 'Próximos' })
    expect(within(proximos).getByRole('link', { name: /martes 17 de noviembre, 10:00/ })).toHaveAttribute('href', '/turno/proximo')
    const historial = screen.getByRole('region', { name: 'Historial' })
    const items = within(historial).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('martes 10 de noviembre')
    expect(items[0]).toHaveTextContent('Cancelado')
    expect(items[1]).toHaveTextContent('Fuiste')
    expect(obtenerMisTurnos).toHaveBeenCalledWith('acceso-1')
  })

  it('HU-23 CA2: "Reservar lo mismo" arranca la reserva con los servicios y el profesional de ese turno', async () => {
    recordarAcceso({ token: 'acceso-1', venceEn: UN_MES })
    vi.mocked(obtenerMisTurnos).mockResolvedValue({ estado: 'ok', datos: {
      proximos: [], historial: [delCliente({ id: 'viejo', estado: 'completado' })],
    } })
    abrir('/mis-turnos')

    fireEvent.click(await screen.findByRole('button', { name: 'Reservar lo mismo' }))

    expect(screen.getByTestId('destino')).toHaveTextContent(
      '/reservar/servicios {"repetir":{"servicioIds":["corte"],"profesionalId":"ana"}}')
  })

  it('HU-22: si el acceso venció, lo olvida y ofrece entrar de nuevo', async () => {
    recordarAcceso({ token: 'viejo', venceEn: UN_MES })
    vi.mocked(obtenerMisTurnos).mockResolvedValue({ estado: 'sin-acceso' })

    abrir('/mis-turnos')

    expect(await screen.findByRole('alert')).toHaveTextContent('Tu acceso venció. Volvé a entrar con tu teléfono.')
    expect(screen.getByRole('link', { name: 'Entrar con mi teléfono' })).toBeInTheDocument()
    expect(leerAcceso()).toBeNull()
  })

  it('"Salir en este navegador" olvida el acceso', async () => {
    recordarAcceso({ token: 'acceso-1', venceEn: UN_MES })
    vi.mocked(obtenerMisTurnos).mockResolvedValue({ estado: 'ok', datos: { proximos: [], historial: [] } })
    abrir('/mis-turnos')

    fireEvent.click(await screen.findByRole('button', { name: 'Salir en este navegador' }))

    expect(leerAcceso()).toBeNull()
    expect(screen.getByRole('link', { name: 'Entrar con mi teléfono' })).toBeInTheDocument()
  })

  it('HU-22: un turno abierto desde la lista (sin # en el link) se pide con el acceso', async () => {
    recordarAcceso({ token: 'acceso-1', venceEn: UN_MES })

    abrir('/turno/proximo')

    expect(await screen.findByRole('heading', { name: 'Tu turno' })).toBeInTheDocument()
    expect(obtenerTurno).toHaveBeenCalledWith('proximo', { acceso: 'acceso-1' })
  })
})
