import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  crearProfesional, crearUsuarioProfesional, darDeAlta, darDeBaja, editarProfesional, listarProfesionales,
  listarServicios, subirImagen, turnosFuturosDe,
} from '../api/admin'
import type { ProfesionalAdmin, ServicioAdmin, UsuarioPanel } from '../api/tipos'
import PaginaProfesionales from './PaginaProfesionales'
import { ContextoSesion } from './sesion'

vi.mock('../api/admin', () => ({
  listarProfesionales: vi.fn(), listarServicios: vi.fn(), crearProfesional: vi.fn(), editarProfesional: vi.fn(),
  crearUsuarioProfesional: vi.fn(), turnosFuturosDe: vi.fn(), darDeBaja: vi.fn(), darDeAlta: vi.fn(), subirImagen: vi.fn(),
}))

const ADMIN: UsuarioPanel = { email: 'duenio@demo.com', rol: 'admin', profesionalId: null }

const SERVICIOS: ServicioAdmin[] = [
  { id: 'corte', nombre: 'Corte', descripcion: null, duracionMin: 30, precio: 8000, limpiezaMin: null, orden: 1, activo: true },
  { id: 'barba', nombre: 'Barba', descripcion: null, duracionMin: 20, precio: 5000, limpiezaMin: null, orden: 2, activo: true },
]
const ANA: ProfesionalAdmin = {
  id: 'ana', nombre: 'Ana', fotoUrl: null, activo: true, servicioIds: ['corte', 'barba'], usuarioEmail: 'ana@demo.com', turnosFuturos: 0,
}
const BRUNO: ProfesionalAdmin = {
  id: 'bruno', nombre: 'Bruno', fotoUrl: null, activo: true, servicioIds: ['barba'], usuarioEmail: null, turnosFuturos: 1,
}
const CARLA: ProfesionalAdmin = {
  id: 'carla', nombre: 'Carla', fotoUrl: null, activo: false, servicioIds: [], usuarioEmail: null, turnosFuturos: 0,
}

beforeEach(() => {
  vi.mocked(listarProfesionales).mockReset().mockResolvedValue({ estado: 'ok', datos: [ANA, BRUNO, CARLA] })
  vi.mocked(listarServicios).mockReset().mockResolvedValue({ estado: 'ok', datos: SERVICIOS })
  vi.mocked(crearProfesional).mockReset().mockResolvedValue({ estado: 'ok', datos: ANA })
  vi.mocked(editarProfesional).mockReset().mockResolvedValue({ estado: 'ok', datos: ANA })
  vi.mocked(crearUsuarioProfesional).mockReset()
  vi.mocked(turnosFuturosDe).mockReset()
  vi.mocked(darDeBaja).mockReset().mockResolvedValue({ estado: 'ok', datos: { ...BRUNO, activo: false } })
  vi.mocked(darDeAlta).mockReset().mockResolvedValue({ estado: 'ok', datos: { ...CARLA, activo: true } })
  vi.mocked(subirImagen).mockReset()
})

function abrir() {
  render(
    <ContextoSesion.Provider value={{ usuario: ADMIN, sesionPerdida: vi.fn() }}>
      <MemoryRouter><PaginaProfesionales /></MemoryRouter>
    </ContextoSesion.Provider>,
  )
}

async function itemDe(nombre: string) {
  const items = await screen.findAllByRole('listitem')
  const item = items.find((i) => within(i).queryByText(nombre) !== null)
  if (!item) {
    throw new Error(`No está ${nombre}`)
  }
  return item
}

describe('Profesionales del local', () => {
  it('HU-25: lista cada profesional con sus servicios, su usuario y los dados de baja marcados', async () => {
    abrir()

    const ana = await itemDe('Ana')
    expect(ana).toHaveTextContent('Corte, Barba')
    expect(ana).toHaveTextContent('Entra al panel con ana@demo.com')
    const bruno = await itemDe('Bruno')
    expect(bruno).toHaveTextContent('Sin usuario del panel · 1 turnos por delante')
    expect(within(bruno).getByRole('button', { name: 'Crear usuario' })).toBeInTheDocument()
    const carla = await itemDe('Carla')
    expect(within(carla).getByText('Dado de baja')).toBeInTheDocument()
    expect(within(carla).getByRole('button', { name: 'Dar de alta' })).toBeInTheDocument()
  })

  it('HU-25 CA1: alta con nombre, foto subida y los servicios elegidos', async () => {
    vi.mocked(subirImagen).mockResolvedValue({ estado: 'ok', datos: { id: 'img', url: '/api/publico/imagenes/img' } })
    abrir()
    fireEvent.click(await screen.findByRole('button', { name: 'Nuevo profesional' }))

    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Dani' } })
    const foto = new File(['png'], 'dani.png', { type: 'image/png' })
    fireEvent.change(screen.getByLabelText('Foto'), { target: { files: [foto] } })
    expect(await screen.findByAltText('Foto elegida')).toHaveAttribute('src', '/api/publico/imagenes/img')
    fireEvent.click(screen.getByRole('checkbox', { name: 'Corte' }))
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    await vi.waitFor(() => expect(crearProfesional).toHaveBeenCalledWith({
      nombre: 'Dani', fotoUrl: '/api/publico/imagenes/img', servicioIds: ['corte'],
    }))
    expect(subirImagen).toHaveBeenCalledWith(foto)
  })

  it('D10: una foto que no es una imagen válida lo dice junto al campo', async () => {
    vi.mocked(subirImagen).mockResolvedValue({ estado: 'validacion', errores: {}, mensaje: 'La imagen tiene que ser PNG, JPG o WebP.' })
    abrir()
    fireEvent.click(await screen.findByRole('button', { name: 'Nuevo profesional' }))

    fireEvent.change(screen.getByLabelText('Foto'), { target: { files: [new File(['x'], 'x.gif')] } })

    expect(await screen.findByRole('alert')).toHaveTextContent('La imagen tiene que ser PNG, JPG o WebP.')
  })

  it('HU-25: "Editar" trae sus datos y guarda los servicios cambiados', async () => {
    abrir()
    fireEvent.click(within(await itemDe('Ana')).getByRole('button', { name: 'Editar' }))
    expect(screen.getByRole('checkbox', { name: 'Barba' })).toBeChecked()

    fireEvent.click(screen.getByRole('checkbox', { name: 'Barba' }))
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    await vi.waitFor(() => expect(editarProfesional).toHaveBeenCalledWith('ana', {
      nombre: 'Ana', fotoUrl: null, servicioIds: ['corte'],
    }))
  })

  it('HU-25 CA2: "Crear usuario" le da mail y contraseña inicial', async () => {
    vi.mocked(crearUsuarioProfesional).mockResolvedValue({ estado: 'ok', datos: { ...BRUNO, usuarioEmail: 'bruno@demo.com' } })
    abrir()
    fireEvent.click(within(await itemDe('Bruno')).getByRole('button', { name: 'Crear usuario' }))

    fireEvent.change(screen.getByLabelText('Mail'), { target: { value: 'bruno@demo.com' } })
    fireEvent.change(screen.getByLabelText('Contraseña inicial'), { target: { value: 'clave-inicial' } })
    fireEvent.click(within(screen.getByRole('form', { name: 'Usuario de Bruno' })).getByRole('button', { name: 'Crear usuario' }))

    await vi.waitFor(() => expect(crearUsuarioProfesional).toHaveBeenCalledWith('bruno', 'bruno@demo.com', 'clave-inicial'))
  })

  it('HU-25 CA4: un mail repetido se marca en el campo del mail', async () => {
    vi.mocked(crearUsuarioProfesional).mockResolvedValue({
      estado: 'validacion', mensaje: 'Ese mail ya tiene un usuario en este local.',
      errores: { email: 'Ese mail ya tiene un usuario en este local.' },
    })
    abrir()
    fireEvent.click(within(await itemDe('Bruno')).getByRole('button', { name: 'Crear usuario' }))
    fireEvent.change(screen.getByLabelText('Mail'), { target: { value: 'ana@demo.com' } })
    fireEvent.change(screen.getByLabelText('Contraseña inicial'), { target: { value: 'clave-inicial' } })

    fireEvent.click(within(screen.getByRole('form', { name: 'Usuario de Bruno' })).getByRole('button', { name: 'Crear usuario' }))

    expect(await screen.findByText('Ese mail ya tiene un usuario en este local.')).toBeInTheDocument()
    expect(screen.getByLabelText('Mail')).toHaveAttribute('aria-invalid', 'true')
  })

  it('HU-25 CA3: con turnos por delante, la baja los muestra y pide confirmar', async () => {
    vi.mocked(turnosFuturosDe).mockResolvedValue({
      estado: 'ok', datos: [{ id: 't1', inicio: '2026-11-17T10:00', clienteNombre: 'Lucía' }],
    })
    abrir()

    fireEvent.click(within(await itemDe('Bruno')).getByRole('button', { name: 'Dar de baja' }))

    const confirmacion = await screen.findByRole('group', { name: 'Dar de baja a Bruno' })
    expect(confirmacion).toHaveTextContent('Bruno tiene 1 turno por delante')
    expect(confirmacion).toHaveTextContent('martes 17 de noviembre, 10:00 · Lucía')
    expect(darDeBaja).not.toHaveBeenCalled()
    fireEvent.click(within(confirmacion).getByRole('button', { name: 'Dar de baja igual' }))
    await vi.waitFor(() => expect(darDeBaja).toHaveBeenCalledWith('bruno', true))
  })

  it('HU-25: sin turnos por delante, la baja igual pide un "sí"', async () => {
    vi.mocked(turnosFuturosDe).mockResolvedValue({ estado: 'ok', datos: [] })
    abrir()

    fireEvent.click(within(await itemDe('Ana')).getByRole('button', { name: 'Dar de baja' }))

    const confirmacion = await screen.findByRole('group', { name: 'Dar de baja a Ana' })
    fireEvent.click(within(confirmacion).getByRole('button', { name: 'Cancelar' }))
    expect(darDeBaja).not.toHaveBeenCalled()
  })

  it('HU-25: "Dar de alta" vuelve a activarlo', async () => {
    abrir()

    fireEvent.click(within(await itemDe('Carla')).getByRole('button', { name: 'Dar de alta' }))

    await vi.waitFor(() => expect(darDeAlta).toHaveBeenCalledWith('carla'))
  })
})
