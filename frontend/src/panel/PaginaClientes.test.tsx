import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { borrarDatosCliente, listarClientes, verCliente } from '../api/admin'
import type { ClienteAdmin, UsuarioPanel } from '../api/tipos'
import PaginaClientes from './PaginaClientes'
import { ContextoSesion } from './sesion'

vi.mock('../api/admin', () => ({ listarClientes: vi.fn(), verCliente: vi.fn(), borrarDatosCliente: vi.fn() }))

const ADMIN: UsuarioPanel = { email: 'duenio@demo.com', rol: 'admin', profesionalId: null }
const LUCIA: ClienteAdmin = {
  id: 'lucia', nombre: 'Lucía', telefono: '+5491123456789', email: 'lucia@mail.com', visitas: 3, faltas: 1,
  ultimaVisita: '2026-11-10', turnosFuturos: 0,
}
const PEDRO: ClienteAdmin = {
  id: 'pedro', nombre: 'Pedro', telefono: null, email: null, visitas: 1, faltas: 0, ultimaVisita: null, turnosFuturos: 1,
}

beforeEach(() => {
  vi.mocked(listarClientes).mockReset().mockResolvedValue({ estado: 'ok', datos: [LUCIA, PEDRO] })
  vi.mocked(verCliente).mockReset().mockResolvedValue({
    estado: 'ok',
    datos: {
      cliente: LUCIA,
      turnos: [
        { id: 't2', inicio: '2026-11-10T10:00', estado: 'completado', profesionalNombre: 'Ana', servicios: ['Corte', 'Barba'], precioTotal: 13000 },
        { id: 't1', inicio: '2026-11-03T11:00', estado: 'ausente', profesionalNombre: 'Bruno', servicios: ['Corte'], precioTotal: 8000 },
      ],
    },
  })
  vi.mocked(borrarDatosCliente).mockReset()
})

function abrir() {
  render(
    <ContextoSesion.Provider value={{ usuario: ADMIN, sesionPerdida: vi.fn() }}>
      <PaginaClientes />
    </ContextoSesion.Provider>,
  )
}

describe('Clientes del local', () => {
  it('HU-31 CA1: lista nombre, teléfono, visitas, faltas y última visita', async () => {
    abrir()

    const items = await screen.findAllByRole('listitem')
    expect(items[0]).toHaveTextContent('Lucía')
    expect(items[0]).toHaveTextContent('+5491123456789 · 3 visitas · 1 falta · Última visita: martes 10 de noviembre')
    expect(items[1]).toHaveTextContent('Sin teléfono · 1 visita · 0 faltas')
  })

  it('HU-31 CA1: busca por nombre o teléfono al tocar "Buscar"', async () => {
    abrir()
    await screen.findByText('Lucía')

    fireEvent.change(screen.getByLabelText('Buscar por nombre o teléfono'), { target: { value: '2345' } })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))

    await vi.waitFor(() => expect(listarClientes).toHaveBeenLastCalledWith('2345'))
  })

  it('HU-31 CA2: "Ver historial" muestra sus turnos del más nuevo al más viejo', async () => {
    abrir()

    fireEvent.click((await screen.findAllByRole('button', { name: 'Ver historial' }))[0])

    const historial = await screen.findByRole('region', { name: 'Historial del cliente' })
    const turnos = await within(historial).findAllByRole('listitem')
    expect(turnos[0]).toHaveTextContent('martes 10 de noviembre, 10:00 · Corte + Barba · con Ana · $ 13.000 · Vino')
    expect(turnos[1]).toHaveTextContent('No vino')
    expect(verCliente).toHaveBeenCalledWith('lucia')
  })

  it('HU-34 CA1: borrar los datos pide confirmar, y después recarga la lista', async () => {
    vi.mocked(borrarDatosCliente).mockResolvedValue({ estado: 'ok', datos: { ...LUCIA, nombre: 'Cliente borrado', telefono: null } })
    abrir()
    fireEvent.click((await screen.findAllByRole('button', { name: 'Ver historial' }))[0])
    fireEvent.click(await screen.findByRole('button', { name: 'Borrar los datos de este cliente' }))

    expect(screen.getByText(/no se pueden recuperar/)).toBeInTheDocument()
    expect(borrarDatosCliente).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Sí, borrar sus datos' }))

    expect(await screen.findByText('Datos borrados. Sus turnos quedan en el historial, sin datos personales.')).toBeInTheDocument()
    expect(borrarDatosCliente).toHaveBeenCalledWith('lucia')
    await vi.waitFor(() => expect(listarClientes).toHaveBeenCalledTimes(2))
  })

  it('HU-34 CA2: con turnos por delante, muestra que hay que cancelarlos antes', async () => {
    vi.mocked(borrarDatosCliente).mockResolvedValue({
      estado: 'error', codigo: 'TURNOS_FUTUROS', mensaje: 'Tiene 1 turno por delante. Cancelalos antes de borrar sus datos.',
    })
    abrir()
    fireEvent.click((await screen.findAllByRole('button', { name: 'Ver historial' }))[0])
    fireEvent.click(await screen.findByRole('button', { name: 'Borrar los datos de este cliente' }))

    fireEvent.click(screen.getByRole('button', { name: 'Sí, borrar sus datos' }))

    expect(await screen.findByText('Tiene 1 turno por delante. Cancelalos antes de borrar sus datos.')).toBeInTheDocument()
  })
})
