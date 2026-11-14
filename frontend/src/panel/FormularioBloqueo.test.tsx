import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { crearBloqueo } from '../api/agenda'
import type { UsuarioPanel } from '../api/tipos'
import FormularioBloqueo from './FormularioBloqueo'
import { ContextoSesion } from './sesion'

vi.mock('../api/agenda', () => ({ crearBloqueo: vi.fn() }))

const ADMIN: UsuarioPanel = { email: 'duenio@demo.com', rol: 'admin', profesionalId: null }
const ANA: UsuarioPanel = { email: 'ana@demo.com', rol: 'profesional', profesionalId: 'ana' }
const PROFESIONALES = [{ id: 'ana', nombre: 'Ana' }, { id: 'bruno', nombre: 'Bruno' }]

const alGuardar = vi.fn<() => void>()

beforeEach(() => {
  vi.mocked(crearBloqueo).mockReset().mockResolvedValue({ estado: 'ok', datos: { ids: ['b1'] } })
  alGuardar.mockReset()
})

function abrir(usuario: UsuarioPanel = ADMIN) {
  render(
    <ContextoSesion.Provider value={{ usuario, sesionPerdida: vi.fn() }}>
      <FormularioBloqueo dia="2026-11-17" profesionales={PROFESIONALES} alGuardar={alGuardar} alCancelar={vi.fn()} />
    </ContextoSesion.Provider>,
  )
}

describe('Bloquear', () => {
  it('HU-27 CA1: el admin bloquea unas horas de todo el local, con motivo', async () => {
    abrir()

    fireEvent.change(screen.getByLabelText('Motivo'), { target: { value: 'Capacitación' } })
    fireEvent.click(screen.getByRole('button', { name: 'Bloquear' }))

    await vi.waitFor(() => expect(alGuardar).toHaveBeenCalled())
    expect(crearBloqueo).toHaveBeenCalledWith({
      profesional: 'todos', desde: '2026-11-17T10:00', hasta: '2026-11-17T13:00', motivo: 'Capacitación',
    })
  })

  it('HU-27 CA1: días enteros van de las 00:00 del primero a las 00:00 del día siguiente al último, para un profesional', async () => {
    abrir()
    fireEvent.change(screen.getByLabelText('A quién'), { target: { value: 'bruno' } })
    fireEvent.click(screen.getByRole('checkbox', { name: 'Días enteros' }))
    fireEvent.change(screen.getByLabelText('Hasta el día'), { target: { value: '2026-11-20' } })

    fireEvent.click(screen.getByRole('button', { name: 'Bloquear' }))

    await vi.waitFor(() => expect(crearBloqueo).toHaveBeenCalledWith({
      profesional: 'bruno', desde: '2026-11-17T00:00', hasta: '2026-11-21T00:00', motivo: null,
    }))
    expect(screen.queryByLabelText('Hora desde')).not.toBeInTheDocument()
  })

  it('HU-27 CA2: con turnos en el rango, dice cuántos y cuáles, y no cierra el formulario', async () => {
    vi.mocked(crearBloqueo).mockResolvedValue({
      estado: 'error', codigo: 'TURNOS_EN_RANGO', mensaje: 'Hay 1 turno en ese rango.',
      problema: { turnos: [{ id: 't1', profesionalNombre: 'Ana', inicio: '2026-11-17T10:30', clienteNombre: 'Lucía' }] },
    })
    abrir()

    fireEvent.click(screen.getByRole('button', { name: 'Bloquear' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Hay 1 turno en ese rango. No se guardó: reprogramalos o cancelalos antes.')
    expect(screen.getByRole('list', { name: 'Turnos en el rango' })).toHaveTextContent('martes 17 de noviembre, 10:30 · Ana · Lucía')
    expect(alGuardar).not.toHaveBeenCalled()
  })

  it('HU-27 CA3: el profesional no elige a quién: bloquea su agenda', async () => {
    abrir(ANA)

    expect(screen.queryByLabelText('A quién')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Bloquear' }))

    await vi.waitFor(() => expect(crearBloqueo).toHaveBeenCalledWith(expect.objectContaining({ profesional: null })))
  })

  it('HU-27: un rango al revés muestra el error del campo', async () => {
    vi.mocked(crearBloqueo).mockResolvedValue({
      estado: 'validacion', mensaje: 'Revisá los datos marcados.',
      errores: { hasta: 'El bloqueo tiene que terminar después de empezar.' },
    })
    abrir()

    fireEvent.click(screen.getByRole('button', { name: 'Bloquear' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El bloqueo tiene que terminar después de empezar.')
  })
})
