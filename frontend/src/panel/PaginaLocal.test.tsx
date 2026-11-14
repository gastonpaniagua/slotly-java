import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { guardarLocal, subirImagen, verLocal } from '../api/admin'
import type { ConfiguracionDelLocal, UsuarioPanel } from '../api/tipos'
import PaginaLocal from './PaginaLocal'
import { ContextoSesion } from './sesion'

vi.mock('../api/admin', () => ({ verLocal: vi.fn(), guardarLocal: vi.fn(), subirImagen: vi.fn() }))

const ADMIN: UsuarioPanel = { email: 'duenio@demo.com', rol: 'admin', profesionalId: null }
const CONFIGURACION: ConfiguracionDelLocal = {
  nombre: 'Barbería Demo', descripcion: null, logoUrl: null, colorAcento: '#2e7d32', whatsapp: '+5493534000000',
  instagram: 'barberia.demo', tiktok: null, facebook: null, direccion: 'San Martín 1200', ciudad: 'Villa María',
  alias: null, titularCuenta: null, cbu: null, linkResena: null,
  intervaloMin: 15, limpiezaMin: 0, anticipacionMin: 60, topeDias: 30, cancelarHastaHoras: 3, maxTurnosFuturos: 2,
}

beforeEach(() => {
  vi.mocked(verLocal).mockReset().mockResolvedValue({ estado: 'ok', datos: CONFIGURACION })
  vi.mocked(guardarLocal).mockReset().mockImplementation(async (configuracion) => ({ estado: 'ok', datos: configuracion }))
  vi.mocked(subirImagen).mockReset()
})

function abrir() {
  render(
    <ContextoSesion.Provider value={{ usuario: ADMIN, sesionPerdida: vi.fn() }}>
      <PaginaLocal />
    </ContextoSesion.Provider>,
  )
}

describe('Configuración del local', () => {
  it('HU-32 CA1 y CA2: trae la configuración y guarda los cambios (los vacíos como null, las reglas como números)', async () => {
    abrir()
    fireEvent.change(await screen.findByLabelText('Nombre'), { target: { value: 'Barbería Nueva' } })
    fireEvent.change(screen.getByLabelText('Instagram'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Intervalo (min)'), { target: { value: '30' } })

    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText(/Guardado\. Las reservas nuevas ya usan estos datos/)).toBeInTheDocument()
    expect(guardarLocal).toHaveBeenCalledWith({ ...CONFIGURACION, nombre: 'Barbería Nueva', instagram: null, intervaloMin: 30 })
  })

  it('HU-32 CA3: los valores fuera de rango se marcan en su campo', async () => {
    vi.mocked(guardarLocal).mockResolvedValue({
      estado: 'validacion', mensaje: 'Revisá los datos marcados.',
      errores: { intervaloMin: 'El intervalo va de 5 a 60 minutos.', cbu: 'El CBU tiene 22 números.' },
    })
    abrir()
    fireEvent.change(await screen.findByLabelText('Intervalo (min)'), { target: { value: '61' } })

    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText('El intervalo va de 5 a 60 minutos.')).toBeInTheDocument()
    expect(screen.getByText('El CBU tiene 22 números.')).toBeInTheDocument()
    expect(screen.getByLabelText('Intervalo (min)')).toHaveAttribute('aria-invalid', 'true')
  })

  it('HU-32 CA4: al elegir un color con poco contraste, avisa antes de guardar', async () => {
    abrir()

    fireEvent.change(await screen.findByLabelText('Color'), { target: { value: '#ff0000' } })

    expect(screen.getByText('Con este color, el texto de los botones va a costar leerse. Probá uno más oscuro o más claro.'))
      .toBeInTheDocument()
    expect(guardarLocal).not.toHaveBeenCalled()
  })

  it('HU-32 CA1: el logo se sube al elegirlo y se guarda su URL', async () => {
    vi.mocked(subirImagen).mockResolvedValue({ estado: 'ok', datos: { id: 'logo', url: '/api/publico/imagenes/logo' } })
    abrir()

    fireEvent.change(await screen.findByLabelText('Logo'), { target: { files: [new File(['png'], 'logo.png', { type: 'image/png' })] } })
    expect(await screen.findByAltText('Logo elegido')).toHaveAttribute('src', '/api/publico/imagenes/logo')
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    await vi.waitFor(() => expect(guardarLocal).toHaveBeenCalledWith(expect.objectContaining({ logoUrl: '/api/publico/imagenes/logo' })))
  })
})
