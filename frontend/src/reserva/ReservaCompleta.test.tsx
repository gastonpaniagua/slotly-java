import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { obtenerDias, obtenerHoras, obtenerProfesionales } from '../api/publico'
import { apartar, confirmar, obtenerTurno } from '../api/reserva'
import type { ApartadoCreado, TurnoPublico } from '../api/tipos'
import SitioDelLocal from '../local/SitioDelLocal'
import { unLocal, unServicio } from '../test/datos'
import { renderConRouter } from '../test/router'

// La reserva de punta a punta, con la API simulada: que los pasos estén bien conectados entre sí.
vi.mock('../api/publico', () => ({ obtenerProfesionales: vi.fn(), obtenerDias: vi.fn(), obtenerHoras: vi.fn() }))
vi.mock('../api/reserva', () => ({ apartar: vi.fn(), confirmar: vi.fn(), obtenerTurno: vi.fn() }))

const TURNO: TurnoPublico = {
  id: 'apartado-10:00', estado: 'confirmado', fecha: '2026-11-17', hora: '10:00', profesionalNombre: 'Bruno',
  servicios: [{ nombre: 'Corte', duracionMin: 30, precio: 8000 }], duracionMin: 30, precioTotal: 8000,
  formaPago: 'efectivo', transferencia: null, yaPaso: false, puedeCambiar: true,
}

function apartadoDe(hora: string): ApartadoCreado {
  return {
    id: `apartado-${hora}`, token: `token-${hora}`, venceEn: '2026-11-16T12:05:00Z', segundosParaConfirmar: 300,
    profesionalId: 'bruno', profesionalNombre: 'Bruno', fecha: '2026-11-17', hora, duracionMin: 30, precioTotal: 8000,
    transferencia: { alias: null, cbu: null, titular: null },
  }
}

beforeEach(() => {
  vi.mocked(obtenerProfesionales).mockReset().mockResolvedValue({
    estado: 'ok', profesionales: [{ id: 'bruno', nombre: 'Bruno', fotoUrl: null }],
  })
  vi.mocked(obtenerDias).mockReset().mockResolvedValue({
    estado: 'ok', datos: { hoy: '2026-11-16', ultimoDia: '2026-12-16', dias: [{ fecha: '2026-11-17', hayLugar: true }] },
  })
  vi.mocked(obtenerHoras).mockReset().mockResolvedValue({
    estado: 'ok', datos: { fecha: '2026-11-17', horas: ['10:00', '10:30'] },
  })
  vi.mocked(apartar).mockReset().mockImplementation(async (pedido) => ({ estado: 'ok', apartado: apartadoDe(pedido.hora) }))
  vi.mocked(confirmar).mockReset().mockResolvedValue({ estado: 'ok', turno: TURNO })
  vi.mocked(obtenerTurno).mockReset().mockResolvedValue({ estado: 'ok', turno: TURNO })
})

// Corte → (Bruno es el único) → martes 17 (elegido solo) → la hora pedida → paso 5.
async function llegarAlPaso5(hora: string) {
  renderConRouter(<SitioDelLocal local={unLocal({ servicios: [unServicio({ id: 'corte', nombre: 'Corte' })] })} />,
    '/reservar/servicios')
  fireEvent.click(screen.getByRole('checkbox', { name: /Corte/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  await screen.findByRole('button', { name: 'martes 17 de noviembre' })
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  fireEvent.click(await screen.findByRole('button', { name: hora }))
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  await screen.findByRole('heading', { name: 'Tus datos' })
}

function completarDatos() {
  fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: '  Lucía  ' } })
  fireEvent.change(screen.getByLabelText('Celular'), { target: { value: '11 2345-6789' } })
  fireEvent.click(screen.getByRole('radio', { name: 'Efectivo, en el local' }))
  fireEvent.click(screen.getByRole('checkbox', { name: /política de privacidad/ }))
}

describe('Reserva completa', () => {
  it('HU-11 CA1: al tocar Siguiente en el paso 4 se aparta la hora elegida', async () => {
    await llegarAlPaso5('10:00')

    expect(apartar).toHaveBeenCalledWith({
      servicios: ['corte'], profesional: 'bruno', fecha: '2026-11-17', hora: '10:00', anterior: undefined,
    })
  })

  it('HU-10 CA1: confirma con los datos y muestra el turno con el día en palabras', async () => {
    await llegarAlPaso5('10:00')
    completarDatos()

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByRole('heading', { name: '¡Listo! Tu turno quedó reservado' })).toBeInTheDocument()
    expect(screen.getByText('martes 17 de noviembre, 10:00')).toBeInTheDocument()
    expect(confirmar).toHaveBeenCalledWith('apartado-10:00', {
      token: 'token-10:00', nombre: 'Lucía', telefono: '11 2345-6789', email: null, formaPago: 'efectivo',
      consentimiento: true,
    })
  })

  it('HU-11 CA3 y HU-12 CA2: al volver y elegir otra hora, se libera el apartado anterior y los datos se conservan', async () => {
    await llegarAlPaso5('10:00')
    completarDatos()

    fireEvent.click(screen.getByRole('button', { name: 'Atrás' }))
    fireEvent.click(await screen.findByRole('button', { name: '10:30' }))
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    await screen.findByRole('heading', { name: 'Tus datos' })
    expect(apartar).toHaveBeenLastCalledWith(expect.objectContaining({
      hora: '10:30', anterior: { id: 'apartado-10:00', token: 'token-10:00' },
    }))
    expect(screen.getByLabelText('Nombre')).toHaveValue('  Lucía  ')
    expect(screen.getByLabelText('Celular')).toHaveValue('11 2345-6789')
  })

  it('HU-10 CA9: después de reservar, el navegador recuerda los datos y el turno, y la home ofrece "Mis turnos"', async () => {
    await llegarAlPaso5('10:00')
    completarDatos()
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))
    await screen.findByRole('heading', { name: '¡Listo! Tu turno quedó reservado' })

    fireEvent.click(screen.getByRole('link', { name: 'Volver al inicio' }))
    fireEvent.click(screen.getByRole('link', { name: 'Mis turnos' }))
    fireEvent.click(screen.getByRole('link', { name: /martes 17 de noviembre, 10:00/ }))
    expect(await screen.findByRole('heading', { name: 'Tu turno' })).toBeInTheDocument()
    expect(obtenerTurno).toHaveBeenLastCalledWith('apartado-10:00', 'token-10:00')

    // Una reserva nueva arranca con nombre y celular precargados.
    fireEvent.click(screen.getByRole('link', { name: 'Volver al inicio' }))
    fireEvent.click(screen.getByRole('link', { name: 'Reservar turno' }))
    fireEvent.click(screen.getByRole('checkbox', { name: /Corte/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    await screen.findByRole('button', { name: 'martes 17 de noviembre' })
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    fireEvent.click(await screen.findByRole('button', { name: '10:30' }))
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    await screen.findByRole('heading', { name: 'Tus datos' })
    expect(screen.getByLabelText('Nombre')).toHaveValue('Lucía')
    expect(screen.getByLabelText('Celular')).toHaveValue('11 2345-6789')
  })

  it('la política de privacidad tiene su página en el sitio del local', () => {
    renderConRouter(<SitioDelLocal local={unLocal()} />, '/privacidad')

    expect(screen.getByRole('heading', { name: 'Política de privacidad' })).toBeInTheDocument()
    expect(screen.getByText(/Ley 25.326/)).toBeInTheDocument()
  })

  it('HU-23 CA2: "Reservar lo mismo" con todo activo arranca en el paso 3 con el mismo profesional', async () => {
    vi.mocked(obtenerProfesionales).mockResolvedValue({
      estado: 'ok', profesionales: [{ id: 'ana', nombre: 'Ana', fotoUrl: null }, { id: 'bruno', nombre: 'Bruno', fotoUrl: null }],
    })

    repetir(['corte'], 'bruno')

    expect(await screen.findByRole('heading', { name: 'Elegí el día' })).toBeInTheDocument()
    expect(screen.getByText('Con Bruno.')).toBeInTheDocument()
    // Los días los pide un efecto, que corre después de dibujar: se espera el pedido, no se da por hecho.
    await vi.waitFor(() => expect(obtenerDias).toHaveBeenCalledWith(['corte'], 'bruno', undefined))
  })

  it('HU-23 CA3: si un servicio ya no está activo, arranca en el paso 1 con los que quedan elegidos y un aviso', async () => {
    repetir(['corte', 'color-que-ya-no-existe'], 'bruno')

    expect(await screen.findByText(/Algún servicio de ese turno ya no está disponible/)).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /Corte/ })).toBeChecked()
    expect(obtenerProfesionales).not.toHaveBeenCalled()
  })

  it('HU-23: si el profesional de ese turno ya no está, arranca en el paso 2 con un aviso', async () => {
    vi.mocked(obtenerProfesionales).mockResolvedValue({
      estado: 'ok', profesionales: [{ id: 'ana', nombre: 'Ana', fotoUrl: null }, { id: 'carla', nombre: 'Carla', fotoUrl: null }],
    })

    repetir(['corte'], 'bruno')

    expect(await screen.findByText('El profesional de ese turno ya no está disponible para esos servicios. Elegí otro.'))
      .toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Elegí con quién' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Ana/ })).toBeInTheDocument()
  })
})

// "Reservar lo mismo" desde Mis turnos: la reserva arranca con ese estado de navegación (HU-23).
function repetir(servicioIds: string[], profesionalId: string) {
  render(
    <MemoryRouter initialEntries={[{ pathname: '/reservar/servicios', state: { repetir: { servicioIds, profesionalId } } }]}>
      <SitioDelLocal local={unLocal({ servicios: [unServicio({ id: 'corte', nombre: 'Corte' })] })} />
    </MemoryRouter>,
  )
}
