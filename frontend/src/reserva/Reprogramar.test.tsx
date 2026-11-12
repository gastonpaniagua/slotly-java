import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  cancelarTurno, obtenerDiasParaReprogramar, obtenerHorasParaReprogramar, obtenerTurno, reprogramarTurno,
} from '../api/reserva'
import type { TurnoPublico } from '../api/tipos'
import { unLocal } from '../test/datos'
import { leerTurnos, recordarTurno } from './memoria'
import PaginaReprogramar from './PaginaReprogramar'
import PaginaTurno from './PaginaTurno'

vi.mock('../api/reserva', () => ({
  obtenerTurno: vi.fn(), cancelarTurno: vi.fn(), obtenerDiasParaReprogramar: vi.fn(),
  obtenerHorasParaReprogramar: vi.fn(), reprogramarTurno: vi.fn(),
}))

const VIEJO: TurnoPublico = {
  id: 'viejo', estado: 'confirmado', fecha: '2026-11-17', hora: '10:00', profesionalNombre: 'Ana',
  servicios: [{ nombre: 'Corte', duracionMin: 30, precio: 8000 }], duracionMin: 30, precioTotal: 8000,
  formaPago: 'efectivo', transferencia: null, yaPaso: false, puedeCambiar: true,
}
const NUEVO: TurnoPublico = { ...VIEJO, id: 'nuevo', fecha: '2026-11-18', hora: '11:00' }

beforeEach(() => {
  vi.mocked(cancelarTurno).mockReset()
  vi.mocked(obtenerTurno).mockReset()
    .mockImplementation(async (id) => ({ estado: 'ok', turno: id === 'nuevo' ? NUEVO : VIEJO }))
  vi.mocked(obtenerDiasParaReprogramar).mockReset().mockResolvedValue({
    estado: 'ok',
    datos: { hoy: '2026-11-16', ultimoDia: '2026-12-16', dias: [
      { fecha: '2026-11-17', hayLugar: false }, { fecha: '2026-11-18', hayLugar: true },
    ] },
  })
  vi.mocked(obtenerHorasParaReprogramar).mockReset()
    .mockResolvedValue({ estado: 'ok', datos: { fecha: '2026-11-18', horas: ['10:00', '11:00'] } })
  vi.mocked(reprogramarTurno).mockReset()
    .mockResolvedValue({ estado: 'ok', id: 'nuevo', token: 'token-nuevo', turno: NUEVO })
})

function abrir(ruta: string) {
  render(
    <MemoryRouter initialEntries={[ruta]}>
      <Routes>
        <Route path="/turno/:id" element={<PaginaTurno local={unLocal()} />} />
        <Route path="/turno/:id/reprogramar/*" element={<PaginaReprogramar />} />
      </Routes>
    </MemoryRouter>,
  )
}

// Desde "Mi turno": Reprogramar → el primer día con lugar queda elegido → Siguiente → una hora.
async function irALasHoras(hora: string) {
  abrir('/turno/viejo#t=secreto')
  fireEvent.click(await screen.findByRole('link', { name: 'Reprogramar' }))
  expect(await screen.findByRole('button', { name: 'miércoles 18 de noviembre' })).toHaveAttribute('aria-pressed', 'true')
  fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  fireEvent.click(await screen.findByRole('button', { name: hora }))
}

describe('Reprogramar', () => {
  it('HU-21 CA1 y CA2: desde Mi turno elige otro día y hora, y queda el turno nuevo con su propio link', async () => {
    recordarTurno({ id: 'viejo', token: 'secreto', fecha: '2026-11-17', hora: '10:00' })
    await irALasHoras('11:00')

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar cambio' }))

    expect(await screen.findByText('Listo, cambiaste tu turno. Este es tu turno nuevo.')).toBeInTheDocument()
    expect(screen.getByText('miércoles 18 de noviembre, 11:00')).toBeInTheDocument()
    expect(obtenerDiasParaReprogramar).toHaveBeenCalledWith('viejo', 'secreto', undefined)
    expect(obtenerHorasParaReprogramar).toHaveBeenCalledWith('viejo', 'secreto', '2026-11-18')
    expect(reprogramarTurno).toHaveBeenCalledWith('viejo', 'secreto', '2026-11-18', '11:00')
    expect(obtenerTurno).toHaveBeenLastCalledWith('nuevo', 'token-nuevo')
    // "Mis turnos" ahora tiene el link nuevo y no el viejo.
    expect(leerTurnos()).toEqual([{ id: 'nuevo', token: 'token-nuevo', fecha: '2026-11-18', hora: '11:00' }])
  })

  it('HU-21 CA1: recuerda cuál es el turno que se está cambiando, con el mismo profesional', async () => {
    abrir('/turno/viejo/reprogramar/dia#t=secreto')

    expect(await screen.findByText('Estás cambiando tu turno del martes 17 de noviembre, 10:00. Si no confirmás, queda como está.'))
      .toBeInTheDocument()
    expect(screen.getByText('Con Ana.')).toBeInTheDocument()
  })

  it('HU-21 CA3: si el horario nuevo se ocupó, lo avisa, lo desmarca y el turno viejo queda como estaba', async () => {
    vi.mocked(reprogramarTurno).mockResolvedValue({ estado: 'ocupado' })
    await irALasHoras('11:00')

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar cambio' }))

    expect(await screen.findByText('Ese horario se acaba de ocupar, elegí otro.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '11:00' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('HU-21 CA4: si justo se pasó el plazo, lo dice y no cambia nada', async () => {
    vi.mocked(reprogramarTurno).mockResolvedValue({ estado: 'fuera-de-plazo' })
    await irALasHoras('11:00')

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar cambio' }))

    expect(await screen.findByText('Ya falta poco para tu turno y no se puede cambiar desde acá. Escribile al local.'))
      .toBeInTheDocument()
  })

  it('HU-21 CA4: un turno que ya no se puede cambiar no ofrece reprogramar, ni entrando directo', async () => {
    vi.mocked(obtenerTurno).mockResolvedValue({ estado: 'ok', turno: { ...VIEJO, puedeCambiar: false } })

    abrir('/turno/viejo/reprogramar/dia#t=secreto')

    expect(await screen.findByRole('heading', { name: 'Este turno no se puede cambiar desde acá' })).toBeInTheDocument()
    expect(obtenerDiasParaReprogramar).not.toHaveBeenCalled()
  })

  it('"Atrás" en el calendario vuelve a Mi turno sin cambiar nada', async () => {
    abrir('/turno/viejo/reprogramar/dia#t=secreto')
    await screen.findByRole('button', { name: 'miércoles 18 de noviembre' })

    fireEvent.click(screen.getByRole('link', { name: 'Atrás' }))

    expect(await screen.findByRole('heading', { name: 'Tu turno' })).toBeInTheDocument()
    expect(reprogramarTurno).not.toHaveBeenCalled()
  })
})
