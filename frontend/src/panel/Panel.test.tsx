import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ingresar, obtenerUsuario, salir } from '../api/panel'
import type { UsuarioPanel } from '../api/tipos'
import { unLocal } from '../test/datos'
import PaginaIngresar from './PaginaIngresar'
import PanelConSesion from './PanelConSesion'
import { destinoDespuesDeIngresar, useSesion } from './sesion'

vi.mock('../api/panel', () => ({ ingresar: vi.fn(), obtenerUsuario: vi.fn(), salir: vi.fn() }))

const ANA: UsuarioPanel = { email: 'ana@demo.com', rol: 'profesional', profesionalId: 'ana' }

beforeEach(() => {
  vi.mocked(ingresar).mockReset()
  vi.mocked(obtenerUsuario).mockReset()
  vi.mocked(salir).mockReset().mockResolvedValue()
})

// Muestra dónde quedó el router, para comprobar a qué página se navegó.
function Direccion() {
  const ubicacion = useLocation()
  return <p data-testid="direccion">{ubicacion.pathname + ubicacion.search}</p>
}

// Una página del panel de prueba que puede avisar "sesión perdida", como lo hará la agenda ante un 401.
function PaginaDePrueba() {
  const { usuario, sesionPerdida } = useSesion()
  return (
    <>
      <h1>Página de {usuario.email}</h1>
      <button type="button" onClick={sesionPerdida}>Pedir algo</button>
    </>
  )
}

function abrir(ruta: string) {
  render(
    <MemoryRouter initialEntries={[ruta]}>
      <Routes>
        <Route path="/ingresar" element={<><PaginaIngresar local={unLocal()} /><Direccion /></>} />
        <Route element={<PanelConSesion local={unLocal({ nombre: 'Barbería Demo' })} />}>
          <Route path="/agenda" element={<><PaginaDePrueba /><Direccion /></>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

function completarYEnviar(email: string, password: string) {
  fireEvent.change(screen.getByLabelText('Mail'), { target: { value: email } })
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
}

describe('Ingresar al panel', () => {
  beforeEach(() => {
    vi.mocked(obtenerUsuario).mockResolvedValue({ estado: 'sin-sesion' })
  })

  it('HU-16 CA1: con mail y contraseña correctos entra a la agenda', async () => {
    vi.mocked(ingresar).mockResolvedValue({ estado: 'ok', usuario: ANA })
    abrir('/ingresar')
    vi.mocked(obtenerUsuario).mockResolvedValue({ estado: 'ok', datos: ANA })

    completarYEnviar('  ana@demo.com ', 'clave')

    expect(await screen.findByRole('heading', { name: 'Página de ana@demo.com' })).toBeInTheDocument()
    expect(screen.getByTestId('direccion')).toHaveTextContent('/agenda')
    expect(ingresar).toHaveBeenCalledWith('ana@demo.com', 'clave')
  })

  it('HU-16 CA2: con datos incorrectos muestra el mensaje genérico, borra la contraseña y no avanza', async () => {
    vi.mocked(ingresar).mockResolvedValue({ estado: 'invalido' })
    abrir('/ingresar')

    completarYEnviar('ana@demo.com', 'mal')

    expect(await screen.findByRole('alert')).toHaveTextContent('Mail o contraseña incorrectos.')
    expect(screen.getByLabelText('Contraseña')).toHaveValue('')
    expect(screen.getByLabelText('Mail')).toHaveValue('ana@demo.com')
  })

  it('HU-16 CA3: con demasiados intentos lo dice', async () => {
    vi.mocked(ingresar).mockResolvedValue({ estado: 'demasiados' })
    abrir('/ingresar')

    completarYEnviar('ana@demo.com', 'clave')

    expect(await screen.findByRole('alert')).toHaveTextContent('Demasiados intentos. Probá de nuevo en 15 minutos.')
  })

  it('sin completar los campos no manda nada', () => {
    abrir('/ingresar')

    fireEvent.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Completá tu mail y tu contraseña.')
    expect(ingresar).not.toHaveBeenCalled()
  })

  it('con una sesión ya abierta, el login pasa directo a la agenda', async () => {
    vi.mocked(obtenerUsuario).mockResolvedValue({ estado: 'ok', datos: ANA })

    abrir('/ingresar')

    expect(await screen.findByRole('heading', { name: 'Página de ana@demo.com' })).toBeInTheDocument()
  })
})

describe('Páginas del panel', () => {
  it('HU-16 CA6: sin sesión lleva al login y, después de entrar, vuelve a la página que quería', async () => {
    vi.mocked(obtenerUsuario).mockResolvedValue({ estado: 'sin-sesion' })
    vi.mocked(ingresar).mockResolvedValue({ estado: 'ok', usuario: ANA })
    abrir('/agenda?dia=2026-11-17')

    expect(await screen.findByRole('heading', { name: 'Ingresar al panel' })).toBeInTheDocument()
    expect(screen.getByTestId('direccion')).toHaveTextContent('/ingresar?volver=%2Fagenda%3Fdia%3D2026-11-17')

    vi.mocked(obtenerUsuario).mockResolvedValue({ estado: 'ok', datos: ANA })
    completarYEnviar('ana@demo.com', 'clave')

    expect(await screen.findByRole('heading', { name: 'Página de ana@demo.com' })).toBeInTheDocument()
    expect(screen.getByTestId('direccion')).toHaveTextContent('/agenda?dia=2026-11-17')
  })

  it('HU-16 CA1: con sesión muestra la página, el local y el usuario', async () => {
    vi.mocked(obtenerUsuario).mockResolvedValue({ estado: 'ok', datos: ANA })

    abrir('/agenda')

    expect(await screen.findByRole('heading', { name: 'Página de ana@demo.com' })).toBeInTheDocument()
    expect(screen.getByText('Barbería Demo')).toBeInTheDocument()
    expect(screen.getByText('ana@demo.com')).toBeInTheDocument()
  })

  it('HU-16 CA7: "Salir" cierra la sesión en el servidor y vuelve al login', async () => {
    vi.mocked(obtenerUsuario).mockResolvedValue({ estado: 'ok', datos: ANA })
    abrir('/agenda')
    await screen.findByRole('heading', { name: 'Página de ana@demo.com' })
    vi.mocked(obtenerUsuario).mockResolvedValue({ estado: 'sin-sesion' })

    fireEvent.click(screen.getByRole('button', { name: 'Salir' }))

    expect(await screen.findByRole('heading', { name: 'Ingresar al panel' })).toBeInTheDocument()
    expect(salir).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('direccion')).toHaveTextContent(/^\/ingresar$/)
  })

  it('HU-16 CA7: si la API dice que la sesión ya no existe (se cerró en otra pestaña), lleva al login', async () => {
    vi.mocked(obtenerUsuario).mockResolvedValue({ estado: 'ok', datos: ANA })
    abrir('/agenda')
    await screen.findByRole('heading', { name: 'Página de ana@demo.com' })
    vi.mocked(obtenerUsuario).mockResolvedValue({ estado: 'sin-sesion' })

    fireEvent.click(screen.getByRole('button', { name: 'Pedir algo' }))

    expect(await screen.findByRole('heading', { name: 'Ingresar al panel' })).toBeInTheDocument()
    expect(screen.getByTestId('direccion')).toHaveTextContent('/ingresar?volver=%2Fagenda')
  })

  it('si no se puede saber si hay sesión (error de red), lo dice con "Probar de nuevo"', async () => {
    vi.mocked(obtenerUsuario).mockResolvedValueOnce({ estado: 'error' })
      .mockResolvedValueOnce({ estado: 'ok', datos: ANA })
    abrir('/agenda')

    fireEvent.click(await screen.findByRole('button', { name: 'Probar de nuevo' }))

    expect(await screen.findByRole('heading', { name: 'Página de ana@demo.com' })).toBeInTheDocument()
  })
})

describe('destinoDespuesDeIngresar', () => {
  it('HU-16 CA6: vuelve a la página del panel que se quería', () => {
    expect(destinoDespuesDeIngresar('/agenda?dia=2026-11-17')).toBe('/agenda?dia=2026-11-17')
  })

  it('sin destino, o con uno de otro sitio, va a la agenda', () => {
    expect(destinoDespuesDeIngresar(null)).toBe('/agenda')
    expect(destinoDespuesDeIngresar('https://otro-sitio.com')).toBe('/agenda')
    expect(destinoDespuesDeIngresar('//otro-sitio.com')).toBe('/agenda')
    expect(destinoDespuesDeIngresar('/ingresar')).toBe('/agenda')
  })
})
