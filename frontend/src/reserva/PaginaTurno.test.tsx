import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { cancelarTurno, obtenerTurno } from '../api/reserva'
import type { LocalPublico, TurnoPublico } from '../api/tipos'
import { unLocal } from '../test/datos'
import PaginaTurno from './PaginaTurno'

vi.mock('../api/reserva', () => ({ obtenerTurno: vi.fn(), cancelarTurno: vi.fn() }))

const TURNO: TurnoPublico = {
  id: 'abc', estado: 'confirmado', fecha: '2026-11-17', hora: '10:30', profesionalNombre: 'Bruno',
  servicios: [{ nombre: 'Corte', duracionMin: 30, precio: 8000 }, { nombre: 'Barba', duracionMin: 20, precio: 5000 }],
  duracionMin: 50, precioTotal: 13000, formaPago: 'transferencia',
  transferencia: { alias: 'barberia.demo', cbu: '0000003100000000000001', titular: 'Juan Pérez' },
  yaPaso: false, puedeCambiar: true,
}

const LOCAL = unLocal({ direccion: 'San Martín 1200', ciudad: 'Villa María', whatsapp: '+5493534000000' })

beforeEach(() => {
  vi.mocked(obtenerTurno).mockReset()
  vi.mocked(cancelarTurno).mockReset()
})

function conTurno(datos: Partial<TurnoPublico> = {}) {
  vi.mocked(obtenerTurno).mockResolvedValue({ estado: 'ok', turno: { ...TURNO, ...datos } })
}

function abrir(entrada: string | { pathname: string; hash: string; state: unknown }, local: LocalPublico = LOCAL) {
  render(
    <MemoryRouter initialEntries={[entrada]}>
      <Routes>
        <Route path="/turno/:id" element={<PaginaTurno local={local} />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Mi turno', () => {
  it('pide el turno con el id de la dirección y el token que está después del #', async () => {
    conTurno()

    abrir('/turno/abc#t=x%2Fy')

    await screen.findByRole('heading', { name: 'Tu turno' })
    expect(obtenerTurno).toHaveBeenCalledWith('abc', 'x/y')
  })

  it('HU-10 CA1 y CA5: recién reservado muestra "¡Listo!", el día en palabras, profesional, servicios, total, pago y transferencia', async () => {
    conTurno()

    abrir({ pathname: '/turno/abc', hash: '#t=secreto', state: { recienConfirmado: true } })

    expect(await screen.findByRole('heading', { name: '¡Listo! Tu turno quedó reservado' })).toBeInTheDocument()
    const resumen = screen.getByRole('region', { name: 'Tu turno' })
    expect(resumen).toHaveTextContent('martes 17 de noviembre, 10:30')
    expect(resumen).toHaveTextContent('Con Bruno')
    expect(resumen).toHaveTextContent('Corte · 30 min · $ 8.000')
    expect(resumen).toHaveTextContent('Total: 50 min · $ 13.000')
    expect(resumen).toHaveTextContent('Pago: Transferencia')
    expect(screen.getByText('barberia.demo')).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '6')
  })

  it('HU-19 CA1: muestra la dirección del local', async () => {
    conTurno()

    abrir('/turno/abc#t=secreto')

    expect(await screen.findByText('Dónde: San Martín 1200, Villa María')).toBeInTheDocument()
  })

  it('HU-10 CA7: al recargar (sin el aviso de recién reservado) se ve el mismo turno, sin crear otro', async () => {
    conTurno()

    abrir('/turno/abc#t=secreto')

    expect(await screen.findByRole('heading', { name: 'Tu turno' })).toBeInTheDocument()
    expect(screen.getByText('martes 17 de noviembre, 10:30')).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('HU-19 CA2: con un link roto dice "Este link no es válido" y ofrece entrar con el teléfono', async () => {
    vi.mocked(obtenerTurno).mockResolvedValue({ estado: 'invalido' })

    abrir('/turno/abc#t=inventado')

    expect(await screen.findByRole('heading', { name: 'Este link no es válido' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Entrar con mi teléfono' })).toHaveAttribute('href', '/entrar')
  })

  it('HU-19 CA3: con un link vencido dice "Este link venció"', async () => {
    vi.mocked(obtenerTurno).mockResolvedValue({ estado: 'vencido' })

    abrir('/turno/abc#t=secreto')

    expect(await screen.findByRole('heading', { name: 'Este link venció' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Entrar con mi teléfono' })).toBeInTheDocument()
  })

  it('si la API no responde, lo dice y "Probar de nuevo" lo vuelve a pedir', async () => {
    vi.mocked(obtenerTurno).mockResolvedValueOnce({ estado: 'error' })
      .mockResolvedValueOnce({ estado: 'ok', turno: TURNO })

    abrir('/turno/abc#t=secreto')
    fireEvent.click(await screen.findByRole('button', { name: 'Probar de nuevo' }))

    expect(await screen.findByRole('heading', { name: 'Tu turno' })).toBeInTheDocument()
  })

  it('un turno cancelado lo avisa', async () => {
    conTurno({ estado: 'cancelado', puedeCambiar: false })

    abrir('/turno/abc#t=secreto')

    expect(await screen.findByRole('alert')).toHaveTextContent('Este turno está cancelado.')
  })

  it('HU-20 CA2: con menos de 3 horas, en vez de cambiarlo pide escribirle al local por WhatsApp', async () => {
    conTurno({ puedeCambiar: false })

    abrir('/turno/abc#t=secreto')

    expect(await screen.findByText('Falta poco para tu turno: para cancelarlo o cambiarlo, escribile al local.'))
      .toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Escribir por WhatsApp' }))
      .toHaveAttribute('href', 'https://wa.me/5493534000000')
  })

  it('un turno que ya pasó lo dice y no ofrece cambios', async () => {
    conTurno({ yaPaso: true, puedeCambiar: false })

    abrir('/turno/abc#t=secreto')

    expect(await screen.findByText('Este turno ya pasó.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Escribir por WhatsApp' })).not.toBeInTheDocument()
  })

  it('HU-20 CA1: "Cancelar turno" pide confirmación y, al confirmar, muestra el turno cancelado', async () => {
    conTurno()
    vi.mocked(cancelarTurno).mockResolvedValue({ estado: 'ok', turno: { ...TURNO, estado: 'cancelado', puedeCambiar: false } })
    abrir('/turno/abc#t=secreto')

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar turno' }))
    expect(screen.getByText('¿Seguro que querés cancelar tu turno? El horario queda libre para otra persona.'))
      .toBeInTheDocument()
    expect(cancelarTurno).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Sí, cancelar' }))

    expect(await screen.findByText('Cancelaste tu turno. El horario quedó libre para otra persona.')).toBeInTheDocument()
    expect(cancelarTurno).toHaveBeenCalledWith('abc', 'secreto')
    expect(screen.queryByRole('button', { name: 'Cancelar turno' })).not.toBeInTheDocument()
  })

  it('HU-20: "No, mantener el turno" vuelve atrás sin cancelar', async () => {
    conTurno()
    abrir('/turno/abc#t=secreto')

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar turno' }))
    fireEvent.click(screen.getByRole('button', { name: 'No, mantener el turno' }))

    expect(screen.getByRole('button', { name: 'Cancelar turno' })).toBeInTheDocument()
    expect(cancelarTurno).not.toHaveBeenCalled()
  })

  it('HU-20 CA2: si justo se pasó el plazo, lo avisa y muestra la opción de escribirle al local', async () => {
    conTurno()
    vi.mocked(cancelarTurno).mockResolvedValue({ estado: 'fuera-de-plazo' })
    abrir('/turno/abc#t=secreto')
    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar turno' }))
    conTurno({ puedeCambiar: false })

    fireEvent.click(screen.getByRole('button', { name: 'Sí, cancelar' }))

    expect(await screen.findByRole('link', { name: 'Escribir por WhatsApp' })).toBeInTheDocument()
    expect(screen.getByText('Ya falta poco para tu turno y no se puede cancelar desde acá. Escribile al local.'))
      .toBeInTheDocument()
  })

  it('HU-20 CA2: fuera de plazo no aparece "Cancelar turno"', async () => {
    conTurno({ puedeCambiar: false })

    abrir('/turno/abc#t=secreto')

    await screen.findByRole('link', { name: 'Escribir por WhatsApp' })
    expect(screen.queryByRole('button', { name: 'Cancelar turno' })).not.toBeInTheDocument()
  })
})
