import { act, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { ResultadoConfirmar } from '../api/reserva'
import type { ApartadoCreado } from '../api/tipos'
import { unServicio } from '../test/datos'
import { DATOS_VACIOS, type DatosCliente } from './datosCliente'
import PasoDatos from './PasoDatos'

// Reloj simulado: la cuenta regresiva avanza a mano (doc 06: sin esperas fijas).
beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
})

afterEach(() => {
  vi.useRealTimers()
})

const APARTADO: ApartadoCreado = {
  id: 'apartado-1',
  token: 'secreto',
  venceEn: '2026-11-16T12:05:00Z',
  segundosParaConfirmar: 300,
  profesionalId: 'ana',
  profesionalNombre: 'Ana',
  fecha: '2026-11-17',
  hora: '10:00',
  duracionMin: 60,
  precioTotal: 13000,
  transferencia: { alias: 'barberia.demo', cbu: '0000003100000000000001', titular: 'Juan Pérez' },
}

// El paso 5 con los datos guardados "arriba", como en Reserva, y una ruta para el paso 4.
function Prueba({ alConfirmar, datosIniciales = DATOS_VACIOS }: {
  alConfirmar: () => Promise<ResultadoConfirmar>
  datosIniciales?: DatosCliente
}) {
  const [datos, setDatos] = useState(datosIniciales)
  const [creadoEnMs] = useState(() => Date.now())
  return (
    <MemoryRouter initialEntries={['/reservar/datos']}>
      <Routes>
        <Route path="/reservar/datos" element={
          <PasoDatos apartado={APARTADO} creadoEnMs={creadoEnMs}
            servicios={[unServicio({ nombre: 'Corte' }), unServicio({ nombre: 'Barba' })]}
            datos={datos} alCambiarDatos={setDatos} alConfirmar={alConfirmar} />
        } />
        <Route path="/reservar/hora" element={<h1>Elegí la hora</h1>} />
      </Routes>
    </MemoryRouter>
  )
}

const COMPLETOS: DatosCliente = {
  nombre: 'Lucía', telefono: '11 2345-6789', email: '', formaPago: 'efectivo', consentimiento: true,
}

async function pasan(segundos: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(segundos * 1000)
  })
}

describe('Paso 5: tus datos', () => {
  it('muestra el resumen del turno apartado: día, hora, profesional asignado, servicios y total', () => {
    render(<Prueba alConfirmar={vi.fn()} />)

    const resumen = screen.getByRole('region', { name: 'Tu turno' })
    expect(resumen).toHaveTextContent('martes 17 de noviembre, 10:00')
    expect(resumen).toHaveTextContent('Con Ana')
    expect(resumen).toHaveTextContent('Corte + Barba')
    expect(resumen).toHaveTextContent('1 h · $ 13.000')
  })

  it('HU-11 CA4: muestra cuánto tiempo queda para confirmar, y baja', async () => {
    render(<Prueba alConfirmar={vi.fn()} />)
    expect(screen.getByRole('timer')).toHaveTextContent('5:00')

    await pasan(90)

    expect(screen.getByRole('timer')).toHaveTextContent('3:30')
  })

  it('HU-11 CA2: al vencerse el tiempo avisa, no deja confirmar y lleva a elegir otra hora', async () => {
    render(<Prueba alConfirmar={vi.fn()} datosIniciales={COMPLETOS} />)

    await pasan(300)

    expect(screen.getByRole('alert')).toHaveTextContent('Se venció el tiempo para confirmar. Elegí la hora de nuevo.')
    expect(screen.getByRole('button', { name: 'Confirmar' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Elegir otra hora' }))
    expect(screen.getByRole('heading', { name: 'Elegí la hora' })).toBeInTheDocument()
  })

  it('HU-10 CA2: sin aceptar la política no se envía y se marca el campo', async () => {
    const alConfirmar = vi.fn()
    render(<Prueba alConfirmar={alConfirmar} datosIniciales={{ ...COMPLETOS, consentimiento: false }} />)

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(screen.getByText('Tenés que aceptar la política de privacidad.')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /política de privacidad/ })).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('link', { name: 'política de privacidad' })).toHaveAttribute('href', '/privacidad')
    expect(alConfirmar).not.toHaveBeenCalled()
  })

  it('HU-10 CA4: con datos inválidos muestra cada error junto a su campo y no envía', () => {
    const alConfirmar = vi.fn()
    render(<Prueba alConfirmar={alConfirmar} datosIniciales={{ ...COMPLETOS, nombre: '', telefono: '123', email: 'ana@' }} />)

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(screen.getByLabelText('Nombre')).toHaveAccessibleDescription('Ingresá tu nombre.')
    expect(screen.getByLabelText('Celular')).toHaveAccessibleDescription('Ingresá un celular válido, por ejemplo 11 2345-6789.')
    expect(screen.getByLabelText('Mail (opcional)')).toHaveAccessibleDescription('Revisá el mail.')
    expect(screen.getByLabelText('Nombre')).toHaveFocus()
    expect(alConfirmar).not.toHaveBeenCalled()
  })

  it('HU-10 CA4: los errores que devuelve la API también se muestran junto al campo', async () => {
    const alConfirmar = vi.fn().mockResolvedValue({
      estado: 'validacion', errores: { telefono: 'Ingresá un celular válido, por ejemplo 11 2345-6789.' },
    })
    render(<Prueba alConfirmar={alConfirmar} datosIniciales={{ ...COMPLETOS, telefono: '11 1111 1111' }} />)

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByText('Ingresá un celular válido, por ejemplo 11 2345-6789.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar' })).toBeEnabled()
  })

  it('HU-10 CA5: con transferencia aparecen alias, CBU y titular con botón de copiar', () => {
    render(<Prueba alConfirmar={vi.fn()} />)
    expect(screen.queryByText('barberia.demo')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('radio', { name: 'Transferencia' }))

    expect(screen.getByText('barberia.demo')).toBeInTheDocument()
    expect(screen.getByText('0000003100000000000001')).toBeInTheDocument()
    expect(screen.getByText('Juan Pérez')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Copiar CBU' })).toBeInTheDocument()
  })

  it('HU-10 CA6: tocar "Confirmar" dos veces seguidas envía una sola vez', async () => {
    const alConfirmar = vi.fn().mockReturnValue(new Promise(() => {}))
    render(<Prueba alConfirmar={alConfirmar} datosIniciales={COMPLETOS} />)

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmando…' }))

    expect(alConfirmar).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Confirmando…' })).toBeDisabled()
  })

  it('HU-11 CA2: si la API dice que venció, avisa igual', async () => {
    render(<Prueba alConfirmar={vi.fn().mockResolvedValue({ estado: 'vencido' })} datosIniciales={COMPLETOS} />)

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Se venció el tiempo para confirmar.')
  })

  it('HU-14 CA1: con el máximo de turnos muestra el aviso de la API', async () => {
    render(<Prueba
      alConfirmar={vi.fn().mockResolvedValue({ estado: 'maximo', mensaje: 'Ya tenés 2 turnos reservados. Podés verlos en "Mi turno".' })}
      datosIniciales={COMPLETOS} />)

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ya tenés 2 turnos reservados.')
  })
})
