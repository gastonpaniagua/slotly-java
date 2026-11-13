import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { crearServicio, editarServicio, listarServicios, ordenarServicios } from '../api/admin'
import type { ServicioAdmin, UsuarioPanel } from '../api/tipos'
import PaginaServicios from './PaginaServicios'
import { ContextoSesion } from './sesion'
import SoloAdmin from './SoloAdmin'

vi.mock('../api/admin', () => ({
  listarServicios: vi.fn(), crearServicio: vi.fn(), editarServicio: vi.fn(), ordenarServicios: vi.fn(),
}))

const ADMIN: UsuarioPanel = { email: 'duenio@demo.com', rol: 'admin', profesionalId: null }
const PROFESIONAL: UsuarioPanel = { email: 'ana@demo.com', rol: 'profesional', profesionalId: 'ana' }

const CORTE: ServicioAdmin = {
  id: 'corte', nombre: 'Corte', descripcion: 'Con lavado.', duracionMin: 30, precio: 8000, limpiezaMin: null, orden: 1, activo: true,
}
const BARBA: ServicioAdmin = {
  id: 'barba', nombre: 'Barba', descripcion: null, duracionMin: 20, precio: 5000, limpiezaMin: 5, orden: 2, activo: false,
}

beforeEach(() => {
  vi.mocked(listarServicios).mockReset().mockResolvedValue({ estado: 'ok', datos: [CORTE, BARBA] })
  vi.mocked(crearServicio).mockReset()
  vi.mocked(editarServicio).mockReset().mockResolvedValue({ estado: 'ok', datos: CORTE })
  vi.mocked(ordenarServicios).mockReset().mockResolvedValue({ estado: 'ok', datos: [BARBA, CORTE] })
})

function abrir(usuario: UsuarioPanel = ADMIN) {
  render(
    <ContextoSesion.Provider value={{ usuario, sesionPerdida: vi.fn() }}>
      <MemoryRouter>
        <SoloAdmin><PaginaServicios /></SoloAdmin>
      </MemoryRouter>
    </ContextoSesion.Provider>,
  )
}

describe('Servicios del local', () => {
  it('HU-24: lista todos los servicios en orden, con duración, precio y los ocultos marcados', async () => {
    abrir()

    const items = await screen.findAllByRole('listitem')
    expect(items[0]).toHaveTextContent('Corte')
    expect(items[0]).toHaveTextContent('30 min · $ 8.000')
    expect(items[1]).toHaveTextContent('Barba')
    expect(items[1]).toHaveTextContent('5 min de limpieza')
    expect(within(items[1]).getByText('Oculto')).toBeInTheDocument()
    expect(within(items[1]).getByRole('button', { name: 'Mostrar' })).toBeInTheDocument()
  })

  it('HU-24 CA1: "Nuevo servicio" guarda los datos como números y recarga la lista', async () => {
    vi.mocked(crearServicio).mockResolvedValue({ estado: 'ok', datos: CORTE })
    abrir()
    fireEvent.click(await screen.findByRole('button', { name: 'Nuevo servicio' }))

    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Color' } })
    fireEvent.change(screen.getByLabelText('Duración (min)'), { target: { value: '90' } })
    fireEvent.change(screen.getByLabelText('Precio ($)'), { target: { value: '30000' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    await vi.waitFor(() => expect(listarServicios).toHaveBeenCalledTimes(2))
    expect(crearServicio).toHaveBeenCalledWith({
      nombre: 'Color', descripcion: null, duracionMin: 90, precio: 30000, limpiezaMin: null, activo: true,
    })
    expect(screen.queryByRole('form', { name: 'Nuevo servicio' })).not.toBeInTheDocument()
  })

  it('HU-24 CA4: los errores de la API se muestran junto a cada campo y el formulario queda abierto', async () => {
    vi.mocked(crearServicio).mockResolvedValue({
      estado: 'validacion', mensaje: 'Revisá los datos marcados.',
      errores: { duracionMin: 'La duración tiene que ser múltiplo de 5 minutos.', precio: 'El precio no puede ser negativo.' },
    })
    abrir()
    fireEvent.click(await screen.findByRole('button', { name: 'Nuevo servicio' }))
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Raro' } })
    fireEvent.change(screen.getByLabelText('Duración (min)'), { target: { value: '42' } })
    fireEvent.change(screen.getByLabelText('Precio ($)'), { target: { value: '-1' } })

    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText('La duración tiene que ser múltiplo de 5 minutos.')).toBeInTheDocument()
    expect(screen.getByLabelText('Duración (min)')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('El precio no puede ser negativo.')).toBeInTheDocument()
    expect(screen.getByRole('form', { name: 'Nuevo servicio' })).toBeInTheDocument()
  })

  it('HU-24 CA2: "Editar" trae los datos del servicio y guarda los cambios', async () => {
    abrir()
    fireEvent.click((await screen.findAllByRole('button', { name: 'Editar' }))[0])
    expect(screen.getByLabelText('Descripción')).toHaveValue('Con lavado.')

    fireEvent.change(screen.getByLabelText('Precio ($)'), { target: { value: '9500' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    await vi.waitFor(() => expect(editarServicio).toHaveBeenCalledWith('corte', {
      nombre: 'Corte', descripcion: 'Con lavado.', duracionMin: 30, precio: 9500, limpiezaMin: null, activo: true,
    }))
  })

  it('HU-24 CA3: "Ocultar" y "Mostrar" cambian solo si se ofrece', async () => {
    abrir()

    fireEvent.click(await screen.findByRole('button', { name: 'Ocultar' }))

    await vi.waitFor(() => expect(editarServicio).toHaveBeenCalledWith('corte', expect.objectContaining({ activo: false })))
  })

  it('HU-24: las flechas cambian el orden', async () => {
    abrir()

    fireEvent.click(await screen.findByRole('button', { name: 'Bajar Corte' }))

    await vi.waitFor(() => expect(ordenarServicios).toHaveBeenCalledWith(['barba', 'corte']))
    // La lista nueva (la que devuelve la API) deja a Corte último: ya no se puede bajar más.
    await vi.waitFor(() => expect(screen.getByRole('button', { name: 'Bajar Corte' })).toBeDisabled())
    expect(screen.getAllByRole('listitem')[0]).toHaveTextContent('Barba')
  })

  it('HU-24 CA5: un profesional no ve la sección (y la API ni se llama)', () => {
    abrir(PROFESIONAL)

    expect(screen.getByRole('heading', { name: 'No tenés permiso para ver esto' })).toBeInTheDocument()
    expect(listarServicios).not.toHaveBeenCalled()
  })
})
