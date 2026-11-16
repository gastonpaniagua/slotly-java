import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { borrarBloqueo, cancelarTurnoDelPanel, crearTurnoManual, marcarAsistencia, moverTurno } from '../api/agenda'
import { obtenerAgenda } from '../api/panel'
import type { Agenda, FranjaDeAgenda, TurnoDeAgenda, UsuarioPanel } from '../api/tipos'
import { sumarDias } from '../fechas'
import { unLocal, unServicio, unTurno } from '../test/datos'
import PaginaAgenda from './PaginaAgenda'
import { ContextoSesion } from './sesion'

vi.mock('../api/panel', () => ({ obtenerAgenda: vi.fn() }))
vi.mock('../api/agenda', () => ({
  borrarBloqueo: vi.fn(), crearBloqueo: vi.fn(), crearTurnoManual: vi.fn(), moverTurno: vi.fn(), cancelarTurnoDelPanel: vi.fn(),
  marcarAsistencia: vi.fn(),
}))

const HOY = '2026-11-17'   // martes
const ANA: UsuarioPanel = { email: 'ana@demo.com', rol: 'profesional', profesionalId: 'ana' }
const DUENIO: UsuarioPanel = { email: 'duenio@demo.com', rol: 'admin', profesionalId: null }

const LOCAL = unLocal({ servicios: [unServicio({ id: 'corte', nombre: 'Corte' }), unServicio({ id: 'barba', nombre: 'Barba' })] })

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
          <Route path="/agenda" element={<PaginaAgenda local={LOCAL} />} />
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

  it('HU-39 CA1 y CA2: cada turno dice cómo van sus mensajes; si uno no llegó, queda marcado para llamarlo', async () => {
    turnos = [
      unTurno({ inicio: `${HOY}T10:00`, fin: `${HOY}T10:30`, clienteNombre: 'Lucía',
        mensajes: [{ tipo: 'confirmacion', estado: 'enviado' }, { tipo: 'recordatorio', estado: 'pendiente' }] }),
      unTurno({ inicio: `${HOY}T11:00`, fin: `${HOY}T11:30`, clienteNombre: 'Martín',
        mensajes: [{ tipo: 'confirmacion', estado: 'fallido' }] }),
      unTurno({ inicio: `${HOY}T12:00`, fin: `${HOY}T12:30`, clienteNombre: 'Pedro', tipo: 'manual' }),
    ]

    abrir(ANA)

    await screen.findByRole('heading', { name: 'martes 17 de noviembre' })
    const [lucia, martin, pedro] = screen.getAllByRole('listitem')
    expect(lucia).toHaveTextContent('Confirmación enviada · Recordatorio pendiente')
    expect(lucia).not.toHaveTextContent('No le llegó')
    expect(martin).toHaveTextContent('Confirmación no llegó')
    expect(martin).toHaveTextContent('No le llegó un mensaje: llamalo.')
    expect(martin).toHaveClass('agenda__item--fallo')
    expect(pedro).not.toHaveTextContent('Confirmación')
    expect(pedro).not.toHaveClass('agenda__item--fallo')
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

  it('HU-27 CA4: "Quitar" un bloqueo pide un "sí", lo borra y vuelve a pedir la agenda', async () => {
    vi.mocked(borrarBloqueo).mockReset().mockResolvedValue({ estado: 'ok', datos: null })
    turnos = [unTurno({ id: 'bloqueo-1', tipo: 'bloqueo', inicio: `${HOY}T17:00`, fin: `${HOY}T18:00`, clienteNombre: null })]
    abrir(ANA)

    fireEvent.click(await screen.findByRole('button', { name: 'Quitar' }))
    expect(borrarBloqueo).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Sí, quitar' }))

    expect(await screen.findByText('Bloqueo quitado: esas horas vuelven a ofrecerse.')).toBeInTheDocument()
    expect(borrarBloqueo).toHaveBeenCalledWith('bloqueo-1')
    await vi.waitFor(() => expect(obtenerAgenda).toHaveBeenCalledTimes(2))
  })

  it('HU-27: "Bloquear" abre el formulario con el día que se está mirando', async () => {
    abrir(ANA)

    fireEvent.click(await screen.findByRole('button', { name: 'Bloquear' }))

    expect(screen.getByRole('form', { name: 'Bloquear' })).toBeInTheDocument()
    expect(screen.getByLabelText('Desde')).toHaveValue(HOY)
  })

  it('HU-28 CA1: "+ Agregar turno" carga un turno manual en esa agenda y ese día, con teléfono opcional', async () => {
    vi.mocked(crearTurnoManual).mockReset().mockResolvedValue({ estado: 'ok', datos: { id: 'nuevo' } })
    abrir(ANA)

    fireEvent.click(await screen.findByRole('button', { name: 'Agregar turno con Ana el martes 17 de noviembre' }))
    const formulario = screen.getByRole('form', { name: 'Nuevo turno con Ana' })
    fireEvent.change(within(formulario).getByLabelText('Hora'), { target: { value: '11:30' } })
    fireEvent.click(within(formulario).getByRole('checkbox', { name: 'Barba' }))
    fireEvent.change(within(formulario).getByLabelText('Nombre del cliente'), { target: { value: 'Pedro' } })
    fireEvent.click(within(formulario).getByRole('button', { name: 'Guardar turno' }))

    expect(await screen.findByText('Turno guardado.')).toBeInTheDocument()
    expect(crearTurnoManual).toHaveBeenCalledWith({
      profesional: null, fecha: HOY, hora: '11:30', servicioIds: ['barba'], clienteNombre: 'Pedro', clienteTelefono: null,
    })
  })

  it('HU-28 CA2: si el hueco está ocupado, lo dice y el formulario queda abierto', async () => {
    vi.mocked(crearTurnoManual).mockReset().mockResolvedValue({
      estado: 'error', codigo: 'HORARIO_OCUPADO', mensaje: 'Ese horario se pisa con otro turno o un bloqueo.',
    })
    abrir(ANA)
    fireEvent.click(await screen.findByRole('button', { name: /Agregar turno con Ana/ }))
    fireEvent.change(screen.getByLabelText('Nombre del cliente'), { target: { value: 'Pedro' } })

    fireEvent.click(screen.getByRole('button', { name: 'Guardar turno' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ese horario se pisa con otro turno o un bloqueo.')
    expect(screen.getByRole('form', { name: 'Nuevo turno con Ana' })).toBeInTheDocument()
  })

  it('HU-29 CA1: "Mover" lleva el turno al día y la hora nuevos', async () => {
    vi.mocked(moverTurno).mockReset().mockResolvedValue({ estado: 'ok', datos: { id: 't1' } })
    turnos = [unTurno({ id: 't1', inicio: `${HOY}T10:00`, fin: `${HOY}T10:30`, clienteNombre: 'Lucía' })]
    abrir(ANA)

    fireEvent.click(await screen.findByRole('button', { name: 'Mover' }))
    const formulario = screen.getByRole('form', { name: 'Mover el turno de Lucía' })
    expect(within(formulario).getByLabelText('Hora nueva')).toHaveValue('10:00')
    fireEvent.change(within(formulario).getByLabelText('Hora nueva'), { target: { value: '12:00' } })
    fireEvent.click(within(formulario).getByRole('button', { name: 'Mover' }))

    expect(await screen.findByText('Turno movido.')).toBeInTheDocument()
    expect(moverTurno).toHaveBeenCalledWith('t1', HOY, '12:00')
  })

  it('HU-29 CA3: "Cancelar" pide un "sí" y cancela el turno', async () => {
    vi.mocked(cancelarTurnoDelPanel).mockReset().mockResolvedValue({ estado: 'ok', datos: { id: 't1' } })
    turnos = [unTurno({ id: 't1', inicio: `${HOY}T10:00`, fin: `${HOY}T10:30` })]
    abrir(ANA)

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar' }))
    expect(cancelarTurnoDelPanel).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Sí, cancelar' }))

    expect(await screen.findByText('Turno cancelado: el horario quedó libre.')).toBeInTheDocument()
    expect(cancelarTurnoDelPanel).toHaveBeenCalledWith('t1')
  })

  it('HU-29: un turno que ya no está confirmado no ofrece mover ni cancelar', async () => {
    turnos = [unTurno({ estado: 'completado' })]
    abrir(ANA)

    await screen.findByText('Vino')
    expect(screen.queryByRole('button', { name: 'Mover' })).not.toBeInTheDocument()
  })

  it('HU-28: el admin carga el turno en la agenda del profesional que tocó', async () => {
    vi.mocked(crearTurnoManual).mockReset().mockResolvedValue({ estado: 'ok', datos: { id: 'nuevo' } })
    profesionales = [{ id: 'ana', nombre: 'Ana' }, { id: 'bruno', nombre: 'Bruno' }]
    abrir(DUENIO)

    fireEvent.click(await screen.findByRole('button', { name: 'Agregar turno con Bruno el martes 17 de noviembre' }))
    fireEvent.change(screen.getByLabelText('Nombre del cliente'), { target: { value: 'Pedro' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar turno' }))

    await vi.waitFor(() => expect(crearTurnoManual).toHaveBeenCalledWith(expect.objectContaining({ profesional: 'bruno' })))
  })

  it('HU-30 CA1: un turno que ya empezó ofrece "Vino" y "No vino", y marcarlo recarga la agenda', async () => {
    vi.mocked(marcarAsistencia).mockReset().mockResolvedValue({ estado: 'ok', datos: { id: 't1' } })
    turnos = [unTurno({ id: 't1', inicio: `${HOY}T10:00`, fin: `${HOY}T10:30`, yaEmpezo: true })]
    abrir(ANA)

    fireEvent.click(await screen.findByRole('button', { name: 'Vino' }))

    expect(await screen.findByText('Marcado: vino.')).toBeInTheDocument()
    expect(marcarAsistencia).toHaveBeenCalledWith('t1', true)
  })

  it('HU-30 CA2: un turno que todavía no empezó no ofrece marcarlo', async () => {
    turnos = [unTurno({ inicio: `${HOY}T18:00`, fin: `${HOY}T18:30`, yaEmpezo: false })]
    abrir(ANA)

    await screen.findByRole('button', { name: 'Mover' })
    expect(screen.queryByRole('button', { name: 'Vino' })).not.toBeInTheDocument()
  })

  it('HU-30 CA3: una marca de hoy se puede corregir; una de otro día, no', async () => {
    vi.mocked(marcarAsistencia).mockReset().mockResolvedValue({ estado: 'ok', datos: { id: 'hoy' } })
    turnos = [
      unTurno({ id: 'hoy', inicio: `${HOY}T10:00`, fin: `${HOY}T10:30`, estado: 'completado', yaEmpezo: true }),
      unTurno({ id: 'ayer', inicio: '2026-11-16T10:00', fin: '2026-11-16T10:30', estado: 'ausente', yaEmpezo: true }),
    ]
    abrir(ANA, '/agenda?vista=semana&dia=2026-11-17')

    fireEvent.click(await screen.findByRole('button', { name: 'Corregir: no vino' }))

    await vi.waitFor(() => expect(marcarAsistencia).toHaveBeenCalledWith('hoy', false))
    expect(screen.queryByRole('button', { name: 'Corregir: vino' })).not.toBeInTheDocument()
  })
})

