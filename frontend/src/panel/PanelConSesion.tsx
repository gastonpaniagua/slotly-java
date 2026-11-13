import { useCallback, useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'

import { obtenerUsuario, salir } from '../api/panel'
import type { LocalPublico, UsuarioPanel } from '../api/tipos'
import PantallaMensaje from '../PantallaMensaje'
import { ContextoSesion, linkIngresar } from './sesion'
import './panel.css'

type Props = {
  local: LocalPublico
}

// Las secciones del admin. Se suman a medida que se hacen (Etapa 7).
const SECCIONES_ADMIN = [
  { ruta: '/agenda', nombre: 'Agenda' },
  { ruta: '/admin/servicios', nombre: 'Servicios' },
]

type Estado = { tipo: 'cargando' } | { tipo: 'error' } | { tipo: 'ok'; usuario: UsuarioPanel }

// El marco de todas las páginas del panel: pide "quién soy" y, sin sesión, lleva al login recordando a qué página
// se quería entrar (HU-16 CA6). Lo vuelve a pedir al cambiar de página: si la sesión se cerró en otra pestaña,
// cualquier paso lleva al login (CA7).
function PanelConSesion({ local }: Props) {
  const ubicacion = useLocation()
  const navegar = useNavigate()
  const [estado, setEstado] = useState<Estado>({ tipo: 'cargando' })
  const [intento, setIntento] = useState(0)
  const aqui = ubicacion.pathname + ubicacion.search

  const sesionPerdida = useCallback(() => {
    navegar(linkIngresar(aqui), { replace: true })
  }, [navegar, aqui])

  useEffect(() => {
    let vigente = true
    obtenerUsuario().then((resultado) => {
      if (!vigente) {
        return
      }
      if (resultado.estado === 'sin-sesion') {
        sesionPerdida()
      } else if (resultado.estado === 'ok') {
        setEstado({ tipo: 'ok', usuario: resultado.datos })
      } else {
        setEstado({ tipo: 'error' })
      }
    })
    return () => {
      vigente = false
    }
  }, [sesionPerdida, intento])

  async function cerrarSesion() {
    await salir()
    navegar('/ingresar', { replace: true })
  }

  if (estado.tipo === 'error') {
    return (
      <PantallaMensaje
        titulo="No pudimos cargar el panel"
        texto="Revisá tu conexión y probá de nuevo."
        accion={{ texto: 'Probar de nuevo', alHacerClic: () => setIntento((n) => n + 1) }}
      />
    )
  }
  if (estado.tipo === 'cargando') {
    return <p className="panel__cargando" role="status">Cargando…</p>
  }

  return (
    <ContextoSesion.Provider value={{ usuario: estado.usuario, sesionPerdida }}>
      <div className="panel">
        <header className="panel__encabezado">
          <p className="panel__local">{local.nombre}</p>
          <div className="panel__usuario">
            <span className="panel__email">{estado.usuario.email}</span>
            <button type="button" className="btn btn-secondary" onClick={cerrarSesion}>Salir</button>
          </div>
        </header>
        {/* El menú: el admin ve todas las secciones; el profesional, solo su agenda (doc 00). */}
        {estado.usuario.rol === 'admin' && (
          <nav className="panel__menu" aria-label="Secciones del panel">
            {SECCIONES_ADMIN.map((seccion) => (
              <NavLink key={seccion.ruta} to={seccion.ruta} className="panel__seccion">{seccion.nombre}</NavLink>
            ))}
          </nav>
        )}
        <main className="panel__contenido">
          <Outlet />
        </main>
      </div>
    </ContextoSesion.Provider>
  )
}

export default PanelConSesion
