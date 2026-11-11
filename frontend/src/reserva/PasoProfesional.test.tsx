import { fireEvent, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { obtenerProfesionales } from '../api/publico'
import type { ProfesionalPublico } from '../api/tipos'
import SitioDelLocal from '../local/SitioDelLocal'
import { unLocal, unServicio } from '../test/datos'
import { renderConRouter } from '../test/router'

// Se simula la API: acá se prueba qué hace el paso 2 con cada respuesta (el filtro real lo prueba el back).
vi.mock('../api/publico', () => ({ obtenerProfesionales: vi.fn() }))

beforeEach(() => {
  vi.mocked(obtenerProfesionales).mockReset()
})

function responderProfesionales(...nombres: string[]) {
  const profesionales: ProfesionalPublico[] = nombres.map((nombre) => ({ id: nombre.toLowerCase(), nombre, fotoUrl: null }))
  vi.mocked(obtenerProfesionales).mockResolvedValue({ estado: 'ok', profesionales })
}

// Abre el paso 1, elige Corte y Barba, toca "Siguiente" y espera a llegar a la pantalla con ese título.
async function elegirServiciosYAvanzar(tituloEsperado: string) {
  const local = unLocal({
    servicios: [unServicio({ id: 'corte', nombre: 'Corte' }), unServicio({ id: 'barba', nombre: 'Barba' })],
  })
  renderConRouter(<SitioDelLocal local={local} />, '/reservar/servicios')
  fireEvent.click(screen.getByRole('checkbox', { name: /Corte/ }))
  fireEvent.click(screen.getByRole('checkbox', { name: /Barba/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  await screen.findByRole('heading', { name: tituloEsperado })
}

describe('Paso 2: profesional', () => {
  it('HU-06 CA1: muestra "Cualquiera disponible" y los profesionales que hacen todos los servicios', async () => {
    responderProfesionales('Ana', 'Bruno')

    await elegirServiciosYAvanzar('Elegí con quién')

    expect(obtenerProfesionales).toHaveBeenCalledWith(['corte', 'barba'])
    expect(screen.getAllByRole('radio').map((r) => r.closest('label')?.textContent))
      .toEqual([expect.stringContaining('Cualquiera disponible'), 'AAna', 'BBruno'])
  })

  it('HU-06 CA2: si nadie hace la combinación lo dice, y "Atrás" vuelve al paso 1', async () => {
    responderProfesionales()

    await elegirServiciosYAvanzar('Elegí con quién')

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Ningún profesional hace esta combinación, probá sacando un servicio.')
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: 'Atrás' }))
    expect(screen.getByRole('heading', { name: 'Elegí los servicios' })).toBeInTheDocument()
  })

  it('HU-06 CA3: con un solo profesional se salta el paso 2, queda elegido, y "Atrás" desde el paso 3 vuelve al 1', async () => {
    responderProfesionales('Bruno')

    await elegirServiciosYAvanzar('Elegí el día')

    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '3')
    expect(screen.getByText('Con Bruno.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: 'Atrás' }))
    expect(screen.getByRole('heading', { name: 'Elegí los servicios' })).toBeInTheDocument()
  })

  it('"Siguiente" se habilita al elegir, pasa al paso 3, y "Atrás" vuelve al 2 con la elección marcada', async () => {
    responderProfesionales('Ana', 'Bruno')
    await elegirServiciosYAvanzar('Elegí con quién')

    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
    fireEvent.click(screen.getByRole('radio', { name: /Ana/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    expect(screen.getByText('Con Ana.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: 'Atrás' }))
    expect(screen.getByRole('radio', { name: /Ana/ })).toBeChecked()
  })

  it('"Cualquiera disponible" también se puede elegir', async () => {
    responderProfesionales('Ana', 'Bruno')
    await elegirServiciosYAvanzar('Elegí con quién')

    fireEvent.click(screen.getByRole('radio', { name: /Cualquiera disponible/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    expect(screen.getByText('Con Cualquiera disponible.')).toBeInTheDocument()
  })

  it('si falla la búsqueda, se queda en el paso 1 con un aviso', async () => {
    vi.mocked(obtenerProfesionales).mockResolvedValue({ estado: 'error' })

    await elegirServiciosYAvanzar('Elegí los servicios')

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos buscar los profesionales. Probá de nuevo.')
    expect(screen.getByRole('heading', { name: 'Elegí los servicios' })).toBeInTheDocument()
  })
})
