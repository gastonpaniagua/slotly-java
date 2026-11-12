import { act, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { obtenerHoras } from '../api/publico'
import PasoHora from './PasoHora'

// Se simula la API: el cálculo de horas lo prueba el back. Acá: el refresco y qué ve el cliente.
vi.mock('../api/publico', () => ({ obtenerHoras: vi.fn() }))

// Reloj simulado: los 15 s del refresco se adelantan a mano, sin esperarlos de verdad (doc 06: sin esperas fijas).
beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  // Por defecto, una respuesta que nunca llega: así un refresco de más (después de lo que el test preparó)
  // queda esperando en vez de romper. Cada test prepara sus respuestas con responder(...).
  vi.mocked(obtenerHoras).mockReset().mockReturnValue(new Promise(() => {}))
})

afterEach(() => {
  vi.useRealTimers()
})

function responder(...horas: string[]) {
  vi.mocked(obtenerHoras).mockResolvedValueOnce({ estado: 'ok', datos: { fecha: '2026-11-17', horas } })
}

// El paso 4 con la hora elegida guardada "arriba", como en Reserva, y una ruta para el paso 5.
function Prueba() {
  const [hora, setHora] = useState<string | null>(null)
  return (
    <MemoryRouter initialEntries={['/reservar/hora']}>
      <Routes>
        <Route
          path="/reservar/hora"
          element={
            <PasoHora servicios={['corte']} profesional="bruno" nombreProfesional="Bruno" fecha="2026-11-17"
              horaElegida={hora} alElegirHora={setHora} />
          }
        />
        <Route path="/reservar/datos" element={<h1>Tus datos</h1>} />
      </Routes>
    </MemoryRouter>
  )
}

async function pasan15Segundos() {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(15_000)
  })
}

function hora(texto: string) {
  return screen.queryByRole('button', { name: texto })
}

describe('Paso 4: hora', () => {
  it('HU-07: muestra las horas libres del día y pide con los servicios, el profesional y la fecha', async () => {
    responder('10:00', '10:15', '10:30')

    render(<Prueba />)

    expect(await screen.findByRole('button', { name: '10:00' })).toBeInTheDocument()
    expect(hora('10:30')).toBeInTheDocument()
    expect(screen.getByText('martes 17 de noviembre, con Bruno.')).toBeInTheDocument()
    expect(obtenerHoras).toHaveBeenCalledWith(['corte'], 'bruno', '2026-11-17')
  })

  it('elegir una hora habilita "Siguiente", que lleva al paso 5', async () => {
    responder('10:00', '10:15')
    render(<Prueba />)

    fireEvent.click(await screen.findByRole('button', { name: '10:15' }))

    expect(hora('10:15')).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(screen.getByRole('heading', { name: 'Tus datos' })).toBeInTheDocument()
  })

  it('HU-09 CA1: si alguien reserva una hora, desaparece en el próximo refresco (15 s) sin recargar', async () => {
    responder('10:00', '10:15')
    render(<Prueba />)
    await screen.findByRole('button', { name: '10:00' })

    responder('10:15')   // alguien reservó las 10:00
    await pasan15Segundos()

    expect(hora('10:00')).not.toBeInTheDocument()
    expect(hora('10:15')).toBeInTheDocument()
    expect(obtenerHoras).toHaveBeenCalledTimes(2)
  })

  it('HU-09 CA2: al volver a la pestaña se refresca en ese momento', async () => {
    responder('10:00', '10:15')
    render(<Prueba />)
    await screen.findByRole('button', { name: '10:00' })

    responder('10:15')
    await act(async () => {
      window.dispatchEvent(new Event('focus'))
    })

    expect(await screen.findByRole('button', { name: '10:15' })).toBeInTheDocument()
    expect(hora('10:00')).not.toBeInTheDocument()
    expect(obtenerHoras).toHaveBeenCalledTimes(2)
  })

  it('HU-09 CA3: si un refresco falla, se sigue viendo la última lista con un aviso discreto, y el siguiente reintenta', async () => {
    responder('10:00', '10:15')
    render(<Prueba />)
    await screen.findByRole('button', { name: '10:00' })

    vi.mocked(obtenerHoras).mockResolvedValueOnce({ estado: 'error' })
    await pasan15Segundos()

    expect(hora('10:00')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('No pudimos actualizar las horas')

    responder('10:00')
    await pasan15Segundos()

    expect(screen.queryByText(/No pudimos actualizar las horas/)).not.toBeInTheDocument()
    expect(hora('10:15')).not.toBeInTheDocument()
  })

  it('HU-09 CA4: si la hora elegida se ocupa, se deselecciona y avisa "Esa hora se acaba de ocupar"', async () => {
    responder('10:00', '10:15')
    render(<Prueba />)
    fireEvent.click(await screen.findByRole('button', { name: '10:00' }))
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeEnabled()

    responder('10:15')   // alguien reservó justo las 10:00
    await pasan15Segundos()

    expect(screen.getByRole('alert')).toHaveTextContent('Esa hora se acaba de ocupar')
    expect(hora('10:00')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
  })

  it('si la primera carga falla, avisa y "Probar de nuevo" vuelve a pedir', async () => {
    vi.mocked(obtenerHoras).mockResolvedValueOnce({ estado: 'error' })
    render(<Prueba />)

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar las horas.')
    responder('11:00')
    fireEvent.click(screen.getByRole('button', { name: 'Probar de nuevo' }))

    expect(await screen.findByRole('button', { name: '11:00' })).toBeInTheDocument()
  })

  it('si ya no quedan horas ese día, lo dice', async () => {
    responder()
    render(<Prueba />)

    expect(await screen.findByText('Ya no quedan horas libres este día. Volvé y elegí otro día.')).toBeInTheDocument()
  })
})
