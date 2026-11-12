import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'

import { ingresar, obtenerUsuario } from '../api/panel'
import type { LocalPublico } from '../api/tipos'
import { destinoDespuesDeIngresar } from './sesion'
import './panel.css'

type Props = {
  local: LocalPublico
}

const MENSAJES = {
  vacio: 'Completá tu mail y tu contraseña.',
  invalido: 'Mail o contraseña incorrectos.',
  demasiados: 'Demasiados intentos. Probá de nuevo en 15 minutos.',
  error: 'No pudimos conectarnos. Probá de nuevo.',
}

// El login del panel (HU-16), en /ingresar del subdominio del local.
function PaginaIngresar({ local }: Props) {
  const navegar = useNavigate()
  const [parametros] = useSearchParams()
  const destino = destinoDespuesDeIngresar(parametros.get('volver'))
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Si ya hay una sesión abierta, no tiene sentido pedir el login de nuevo. (Además, este pedido deja la cookie
  // del token CSRF que va a necesitar el POST.)
  useEffect(() => {
    let vigente = true
    obtenerUsuario().then((resultado) => {
      if (vigente && resultado.estado === 'ok') {
        navegar(destino, { replace: true })
      }
    })
    return () => {
      vigente = false
    }
  }, [navegar, destino])

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    if (email.trim() === '' || password === '') {
      setError(MENSAJES.vacio)
      return
    }
    setEnviando(true)
    setError(null)
    const resultado = await ingresar(email.trim(), password)
    setEnviando(false)
    if (resultado.estado === 'ok') {
      // replace: "Atrás" desde la agenda no vuelve al login.
      navegar(destino, { replace: true })
      return
    }
    setPassword('')
    setError(MENSAJES[resultado.estado])
  }

  return (
    <main className="ingresar">
      <form className="ingresar__tarjeta formulario" onSubmit={enviar} noValidate>
        <div>
          <p className="ingresar__local">{local.nombre}</p>
          <h1 className="ingresar__titulo">Ingresar al panel</h1>
        </div>
        <div className="campo">
          <label className="campo__etiqueta" htmlFor="ingresar-email">Mail</label>
          <input id="ingresar-email" type="email" autoComplete="username" value={email}
            onChange={(e) => setEmail(e.target.value)} aria-invalid={error !== null} />
        </div>
        <div className="campo">
          <label className="campo__etiqueta" htmlFor="ingresar-password">Contraseña</label>
          <input id="ingresar-password" type="password" autoComplete="current-password" value={password}
            onChange={(e) => setPassword(e.target.value)} aria-invalid={error !== null} />
        </div>
        {error && <p className="campo__error" role="alert">{error}</p>}
        <button type="submit" className="btn btn-primary btn-lg" disabled={enviando}>
          {enviando ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>
    </main>
  )
}

export default PaginaIngresar
