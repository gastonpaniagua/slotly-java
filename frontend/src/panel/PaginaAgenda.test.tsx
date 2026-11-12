import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { obtenerAgenda } from '../api/panel'
import type { Agenda, FranjaDeAgenda, TurnoDeAgenda, UsuarioPanel } from '../api/tipos'
import { sumarDias } from '../fechas'
import { unTurno } from '../test/datos'
import PaginaAgenda from './PaginaAgenda'
import { ContextoSesion } from './sesion'

vi.mock('../api/panel', () => ({ obtenerAgenda: vi.fn() }))

const HOY = '2026-11-17'   // martes
const ANA: UsuarioPanel = { email: 'ana@demo.com', rol: 'profesional', profesionalId: 'ana' }
const DUENIO: UsuarioPanel = { email: 'duenio@demo.com', rol: 'admin', profesionalId: null }

let turnos: TurnoDeAgenda[]
let franjas: FranjaDeAgenda[]
let profesionales: { id: string; nombre: string }[]

// La API simulada responde como la real: la agenda desde el día pedido (o desde hoy), con los datos de cada test.
function agendaDesde(desde: string | null, dias: 1 | 7): Agenda {
  const inicio = desde ?? HOY
  return { hoy: HOY, desde: inicio, hasta: sumarDias(inicio, dias - 1), profesionales, franjas, turnos }
}

beforeEach(() => {
  profesionales = [{ id: 'ana', nombre: 'Ana' }]
  franjas = [
    { profesionalId: 'ana', fecha: HOY, desde: '10:00', hasta: '13:00' },
    { profesionalId: 'ana', fecha: HOY, desde: '16:00', hasta: '20:00' },
  ]
  turnos = []
  vi.mocked(obtenerAgenda).mockReset()
    .mockImplementation(async (desde, dias) => ({ estado: 'ok', datos: agendaDesde(desde, dias) }))
})

function abrir(usuario: UsuarioPanel, ruta = '/agenda', sesionPerdida = vi.fn()) {
  render(
    <ContextoSesion.Provider value={{ usuario, sesionPerdida }}>
      <MemoryRouter initialEntries={[ruta]}>
        <Routes>
          <Route path="/agenda" element={<PaginaAgenda />} />
        </Routes>
      </MemoryRouter>
    </ContextoSesion.Provider>,
  )
  return sesionPerdida
}

describe('Agenda', () => {
  it('HU-17 CA1: muestra los turnos de hoy en orden, con cliente, servicios y estado', async () => {
    turnos = [
      unTurno({ inicio: `${HOY}T11:00`, fin: `${HOY}T11:45`, clienteNombre: 'Lucía', servicios: ['Corte', 'Barba'] }),
      unTurno({ inicio: `${HOY}T10:00`, fin: `${HOY}T10:30`, clienteNombre: 'Martín', estado: 'completado' }),
    ]

    abrir(ANA)

    expect(await screen.findByRole('heading', { name: 'martes 17 de noviembre' })).toBeInTheDocument()
    expect(obtenerAgenda).toHaveBeenCalledWith(null, 1)
    const items = screen.getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('10:00 a 10:30')
    expect(items[0]).toHaveTextContent('Martín')
    expect(items[0]).toHaveTextContent('Vino')
    expect(items[1]).toHaveTextContent('11:00 a 11:45')
    expect(items[1]).toHaveTextContent('Lucía')
    expect(items[1]).toHaveTextContent('Corte + Barba')
    expect(items[1]).toHaveTextContent('Confirmado')
    expect(screen.getByText('Trabaja de 10:00 a 13:00 y 16:00 a 20:00.')).toBeInTheDocument()
  })

  it('HU-17: se pasa al día siguiente y al anterior, y "Hoy" vuelve', async () => {
    abrir(ANA)
    await screen.findByRole('heading', { name: 'martes 17 de noviembre' })

    fireEvent.click(screen.getByRole('button', { name: 'Día siguiente' }))
    expect(await screen.findByRole('heading', { name: 'miércoles 18 de noviembre' })).toBeInTheDocument()
    expect(obtenerAgenda).toHaveBeenLastCalledWith('2026-11-18', 1)

    fireEvent.click(screen.getByRole('button', { name: 'Día anterior' }))
    fireEvent.click(screen.getByRole('button', { name: 'Día anterior' }))
    expect(await screen.findByRole('heading', { name: 'lunes 16 de noviembre' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Hoy' }))
    expect(await screen.findByRole('heading', { name: 'martes 17 de noviembre' })).toBeInTheDocument()
  })

  it('HU-17 CA2: la vista semana muestra los 7 días de lunes a domingo y pasa a la semana anterior y la siguiente', async () => {
    turnos = [unTurno({ inicio: '2026-11-19T10:00', fin: '2026-11-19T10:30', clienteNombre: 'Lucía' })]
    abrir(ANA)
    await screen.findByRole('heading', { name: 'martes 17 de noviembre' })

    fireEvent.click(screen.getByRole('button', { name: 'Semana' }))

    expect(await screen.findByRole('heading', { name: 'Semana del 16 al 22 de noviembre' })).toBeInTheDocument()
    expect(obtenerAgenda).toHaveBeenLastCalledWith('2026-11-16', 7)
    expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(7)
    expect(within(screen.getByRole('region', { name: 'jueves 19 de noviembre' })).getByText('Lucía')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Semana siguiente' }))
    expect(await screen.findByRole('heading', { name: 'Semana del 23 al 29 de noviembre' })).toBeInTheDocument()
    expect(obtenerAgenda).toHaveBeenLastCalledWith('2026-11-23', 7)

    fireEvent.click(screen.getByRole('button', { name: 'Semana anterior' }))
    fireEvent.click(screen.getByRole('button', { name: 'Semana anterior' }))
    expect(await screen.findByRole('heading', { name: 'Semana del 9 al 15 de noviembre' })).toBeInTheDocument()
  })

  it('HU-17 CA2: abrir la semana sin día pide la semana desde su lunes', async () => {
    turnos = [unTurno({ inicio: '2026-11-16T10:00', fin: '2026-11-16T10:30', clienteNombre: 'Lucía' })]

    abrir(ANA, '/agenda?vista=semana')

    // El lunes 16 (antes de hoy) solo aparece si se pidió la semana desde el lunes.
    expect(await screen.findByText('Lucía')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Semana del 16 al 22 de noviembre' })).toBeInTheDocument()
    expect(obtenerAgenda).toHaveBeenLastCalledWith('2026-11-16', 7)
    expect(screen.getAllByRole('heading', { level: 3 })[0]).toHaveTextContent('lunes 16 de noviembre')
  })

  it('HU-17 CA4: un día sin turnos dice "Sin turnos"', async () => {
    abrir(ANA)

    expect(await screen.findByText('Sin turnos')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('un día que no trabaja lo dice, sin repetir "Sin turnos"', async () => {
    franjas = []

    abrir(ANA)

    expect(await screen.findByText('No trabaja este día.')).toBeInTheDocument()
    expect(screen.queryByText('Sin turnos')).not.toBeInTheDocument()
  })

  it('HU-17 CA5: los bloqueos y las pausas aparecen marcados distinto de los turnos', async () => {
    turnos = [
      unTurno({ inicio: `${HOY}T10:00`, fin: `${HOY}T10:30` }),
      unTurno({ tipo: 'bloqueo', inicio: `${HOY}T17:00`, fin: `${HOY}T18:00`, clienteNombre: null, motivo: 'Médico' }),
    ]

    abrir(ANA)

    const bloqueo = (await screen.findByText('Bloqueado: Médico')).closest('li')
    expect(bloqueo).toHaveClass('agenda__item--bloqueo')
    const pausa = screen.getByText('Pausa').closest('li')
    expect(pausa).toHaveClass('agenda__item--pausa')
    expect(pausa).toHaveTextContent('13:00 a 16:00')
    expect(screen.getByText('Lucía').closest('li')).toHaveClass('agenda__item--turno')
  })

  it('HU-18 CA1: el admin ve a todos juntos y puede filtrar por uno; el profesional no tiene filtro', async () => {
    profesionales = [{ id: 'ana', nombre: 'Ana' }, { id: 'bruno', nombre: 'Bruno' }]
    turnos = [
      unTurno({ profesionalId: 'ana', clienteNombre: 'Lucía' }),
      unTurno({ profesionalId: 'bruno', clienteNombre: 'Martín' }),
    ]
    abrir(DUENIO)

    expect(await screen.findByRole('heading', { name: 'Ana' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Bruno' })).toBeInTheDocument()
    expect(screen.getByText('Lucía')).toBeInTheDocument()
    expect(screen.getByText('Martín')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Profesional'), { target: { value: 'bruno' } })

    expect(await screen.findByText('Martín')).toBeInTheDocument()
    expect(screen.queryByText('Lucía')).not.toBeInTheDocument()
  })

  it('HU-18 CA1: el profesional no ve el filtro', async () => {
    abrir(ANA)
    await screen.findByText('Sin turnos')

    expect(screen.queryByLabelText('Profesional')).not.toBeInTheDocument()
  })

  it('HU-16 CA7: si la API dice que no hay sesión (se cerró en otra pestaña), avisa al panel para ir al login', async () => {
    vi.mocked(obtenerAgenda).mockResolvedValue({ estado: 'sin-sesion' })

    const sesionPerdida = abrir(ANA)

    await vi.waitFor(() => expect(sesionPerdida).toHaveBeenCalled())
  })

  it('si la agenda no carga, lo dice y "Probar de nuevo" la vuelve a pedir', async () => {
    vi.mocked(obtenerAgenda).mockResolvedValueOnce({ estado: 'error' })
    abrir(ANA)

    fireEvent.click(await screen.findByRole('button', { name: 'Probar de nuevo' }))

    expect(await screen.findByText('Sin turnos')).toBeInTheDocument()
  })
})
