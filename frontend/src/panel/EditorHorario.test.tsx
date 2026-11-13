import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { guardarHorario, verHorario } from '../api/admin'
import type { ProfesionalAdmin, UsuarioPanel } from '../api/tipos'
import EditorHorario from './EditorHorario'
import { ContextoSesion } from './sesion'

vi.mock('../api/admin', () => ({ verHorario: vi.fn(), guardarHorario: vi.fn() }))

const ADMIN: UsuarioPanel = { email: 'duenio@demo.com', rol: 'admin', profesionalId: null }
const ANA: ProfesionalAdmin = {
  id: 'ana', nombre: 'Ana', fotoUrl: null, activo: true, servicioIds: [], usuarioEmail: null, turnosFuturos: 0,
}
const MARTES = { diaSemana: 2, desde: '10:00', hasta: '13:00' }

beforeEach(() => {
  vi.mocked(verHorario).mockReset().mockResolvedValue({ estado: 'ok', datos: [MARTES] })
  vi.mocked(guardarHorario).mockReset()
    .mockImplementation(async (_id, franjas) => ({ estado: 'ok', datos: { franjas, turnosAfuera: [] } }))
})

function abrir() {
  render(
    <ContextoSesion.Provider value={{ usuario: ADMIN, sesionPerdida: vi.fn() }}>
      <EditorHorario profesional={ANA} alCerrar={vi.fn()} />
    </ContextoSesion.Provider>,
  )
}

describe('Horario semanal', () => {
  it('HU-26: muestra las franjas de cada día; los días sin franjas dicen "No trabaja"', async () => {
    abrir()

    const martes = await screen.findByRole('group', { name: 'Martes' })
    expect(within(martes).getByLabelText('Martes, desde')).toHaveValue('10:00')
    expect(within(martes).getByLabelText('Martes, hasta')).toHaveValue('13:00')
    expect(within(screen.getByRole('group', { name: 'Lunes' })).getByText('No trabaja')).toBeInTheDocument()
  })

  it('HU-26 CA1: agregar la franja de la tarde arma la pausa, y se guarda todo junto', async () => {
    abrir()
    const martes = await screen.findByRole('group', { name: 'Martes' })

    fireEvent.click(within(martes).getByRole('button', { name: 'Agregar franja' }))
    fireEvent.click(screen.getByRole('button', { name: 'Guardar horario' }))

    await vi.waitFor(() => expect(guardarHorario).toHaveBeenCalledWith('ana', [
      MARTES, { diaSemana: 2, desde: '16:00', hasta: '20:00' },
    ]))
    expect(await screen.findByText('Horario guardado.')).toBeInTheDocument()
  })

  it('HU-26: cambiar una hora y quitar una franja', async () => {
    abrir()
    const martes = await screen.findByRole('group', { name: 'Martes' })
    fireEvent.change(within(martes).getByLabelText('Martes, hasta'), { target: { value: '14:00' } })
    fireEvent.click(within(screen.getByRole('group', { name: 'Jueves' })).getByRole('button', { name: 'Agregar franja' }))
    fireEvent.click(screen.getByRole('button', { name: 'Quitar Martes de 10:00 a 14:00' }))

    fireEvent.click(screen.getByRole('button', { name: 'Guardar horario' }))

    await vi.waitFor(() => expect(guardarHorario).toHaveBeenCalledWith('ana', [{ diaSemana: 4, desde: '10:00', hasta: '13:00' }]))
  })

  it('HU-26 CA2: si las franjas se pisan, muestra el mensaje con el día y las horas', async () => {
    vi.mocked(guardarHorario).mockResolvedValue({
      estado: 'validacion', mensaje: 'Revisá los datos marcados.',
      errores: { franjas: 'El martes tiene dos franjas que se pisan: 10:00 a 13:00 y 12:00 a 14:00.' },
    })
    abrir()
    await screen.findByRole('group', { name: 'Martes' })

    fireEvent.click(screen.getByRole('button', { name: 'Guardar horario' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El martes tiene dos franjas que se pisan: 10:00 a 13:00 y 12:00 a 14:00.')
  })

  it('HU-26 CA3: avisa los turnos que quedaron fuera del horario nuevo, sin cancelarlos', async () => {
    vi.mocked(guardarHorario).mockResolvedValue({
      estado: 'ok',
      datos: { franjas: [MARTES], turnosAfuera: [{ id: 't1', inicio: '2026-11-17T17:00', clienteNombre: 'Lucía' }] },
    })
    abrir()
    await screen.findByRole('group', { name: 'Martes' })

    fireEvent.click(screen.getByRole('button', { name: 'Guardar horario' }))

    const aviso = await screen.findByRole('status')
    expect(aviso).toHaveTextContent('Hay 1 turno fuera del horario nuevo. No se cancelan solos')
    expect(aviso).toHaveTextContent('martes 17 de noviembre, 17:00 · Lucía')
    expect(screen.getByRole('button', { name: 'Listo' })).toBeInTheDocument()
  })
})
