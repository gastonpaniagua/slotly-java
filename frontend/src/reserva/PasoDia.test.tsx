import { fireEvent, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { obtenerDias, obtenerProfesionales } from '../api/publico'
import type { DiaConLugar } from '../api/tipos'
import SitioDelLocal from '../local/SitioDelLocal'
import { unLocal, unServicio } from '../test/datos'
import { renderConRouter } from '../test/router'

// Se simula la API: el cálculo real de días lo prueba el back. Acá: qué muestra y qué deja hacer el calendario.
vi.mock('../api/publico', () => ({ obtenerProfesionales: vi.fn(), obtenerDias: vi.fn() }))

// Hoy es el lunes 16/11/2026 y el último día permitido, el domingo 29/11 (dos semanas).
const HOY = '2026-11-16'
const ULTIMO = '2026-11-29'

beforeEach(() => {
  vi.mocked(obtenerProfesionales).mockReset().mockResolvedValue({
    estado: 'ok', profesionales: [{ id: 'bruno', nombre: 'Bruno', fotoUrl: null }],
  })
  vi.mocked(obtenerDias).mockReset()
})

// Días del 16/11 al 29/11 con lugar solo en las fechas indicadas.
function responderDias(...conLugar: string[]) {
  const dias: DiaConLugar[] = Array.from({ length: 14 }, (_, i) => {
    const fecha = `2026-11-${String(16 + i).padStart(2, '0')}`
    return { fecha, hayLugar: conLugar.includes(fecha) }
  })
  vi.mocked(obtenerDias).mockResolvedValue({ estado: 'ok', datos: { hoy: HOY, ultimoDia: ULTIMO, dias } })
}

// Elige un servicio y avanza: como hay un solo profesional (Bruno), se salta directo al paso 3.
async function llegarAlCalendario() {
  renderConRouter(<SitioDelLocal local={unLocal({ servicios: [unServicio({ id: 'corte', nombre: 'Corte' })] })} />,
    '/reservar/servicios')
  fireEvent.click(screen.getByRole('checkbox', { name: /Corte/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  await screen.findByRole('heading', { name: 'Elegí el día' })
  await vi.waitFor(() => expect(obtenerDias).toHaveBeenCalled())
}

function dia(nombre: RegExp) {
  return screen.getByRole('button', { name: nombre })
}

describe('Paso 3: día', () => {
  it('pide los días con los servicios y el profesional elegidos, sin fechas (las decide el back)', async () => {
    responderDias('2026-11-17')

    await llegarAlCalendario()

    expect(obtenerDias).toHaveBeenCalledWith(['corte'], 'bruno', undefined)
  })

  it('HU-08 CA1 y CA2: los días con lugar se pueden tocar; los demás están en gris y deshabilitados', async () => {
    responderDias('2026-11-17', '2026-11-19')

    await llegarAlCalendario()

    expect(await screen.findByRole('button', { name: 'martes 17 de noviembre' })).toBeEnabled()
    expect(dia(/jueves 19 de noviembre/)).toBeEnabled()
    expect(dia(/lunes 16 de noviembre, sin lugar/)).toBeDisabled()
    expect(dia(/domingo 22 de noviembre, sin lugar/)).toBeDisabled()
  })

  it('HU-08 CA4: al entrar queda elegido el primer día con lugar', async () => {
    responderDias('2026-11-19', '2026-11-20')

    await llegarAlCalendario()

    expect(await screen.findByRole('button', { name: 'jueves 19 de noviembre' })).toHaveAttribute('aria-pressed', 'true')
    expect(dia(/viernes 20 de noviembre/)).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeEnabled()
  })

  it('HU-08 CA4: si la primera semana no tiene lugar, muestra la semana del primer día con lugar', async () => {
    responderDias('2026-11-25')

    await llegarAlCalendario()

    expect(await screen.findByText('23 al 29 de noviembre')).toBeInTheDocument()
    expect(dia(/miércoles 25 de noviembre/)).toHaveAttribute('aria-pressed', 'true')
  })

  it('HU-08 CA3: no se puede ir antes de esta semana ni después de la semana del último día', async () => {
    responderDias('2026-11-17', '2026-11-24')

    await llegarAlCalendario()

    expect(await screen.findByRole('button', { name: 'Semana anterior' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Semana siguiente' }))
    expect(screen.getByText('23 al 29 de noviembre')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Semana siguiente' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Semana anterior' })).toBeEnabled()
    // Las dos semanas vinieron en el primer pedido: navegar no vuelve a llamar a la API.
    expect(obtenerDias).toHaveBeenCalledTimes(1)
  })

  it('elegir otro día y avanzar lleva al paso 4; "Atrás" vuelve con el día marcado', async () => {
    responderDias('2026-11-17', '2026-11-19')
    await llegarAlCalendario()

    fireEvent.click(await screen.findByRole('button', { name: 'jueves 19 de noviembre' }))
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    expect(screen.getByRole('heading', { name: 'Elegí la hora' })).toBeInTheDocument()
    expect(screen.getByText('jueves 19 de noviembre, con Bruno.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: 'Atrás' }))
    expect(await screen.findByRole('button', { name: 'jueves 19 de noviembre' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('sin ningún día con lugar lo avisa y no deja avanzar', async () => {
    responderDias()

    await llegarAlCalendario()

    expect(await screen.findByRole('alert')).toHaveTextContent('No hay lugar en las próximas semanas')
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
  })

  it('si falla la carga muestra un aviso y "Probar de nuevo" vuelve a pedir', async () => {
    vi.mocked(obtenerDias).mockResolvedValueOnce({ estado: 'error' })
    await llegarAlCalendario()
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar los días disponibles.')

    responderDias('2026-11-17')
    fireEvent.click(screen.getByRole('button', { name: 'Probar de nuevo' }))

    expect(await screen.findByRole('button', { name: 'martes 17 de noviembre' })).toHaveAttribute('aria-pressed', 'true')
  })
})
