import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'

import { pedirCodigo, verificarCodigo } from '../api/acceso'
import { leerCliente, recordarAcceso } from './memoria'
import './reserva.css'

// Entrar con el teléfono y un código (HU-22), en /entrar: para ver los turnos desde otro celular o computadora.
// Primero el celular; después el código de 6 números que le llega (en la v1, por un envío simulado).
function PaginaEntrar() {
  const navegar = useNavigate()
  const [telefono, setTelefono] = useState(() => leerCliente()?.telefono ?? '')
  const [codigo, setCodigo] = useState('')
  const [etapa, setEtapa] = useState<'telefono' | 'codigo'>('telefono')
  const [vigencia, setVigencia] = useState(10)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function pedir(evento?: FormEvent) {
    evento?.preventDefault()
    if (telefono.trim() === '') {
      setError('Ingresá tu celular.')
      return
    }
    setEnviando(true)
    setError(null)
    const resultado = await pedirCodigo(telefono.trim())
    setEnviando(false)
    switch (resultado.estado) {
      case 'ok':
        setVigencia(resultado.vigenciaMinutos)
        setCodigo('')
        setEtapa('codigo')
        break
      case 'validacion':
        setError(resultado.mensaje)
        break
      case 'demasiados':
        setError('Pediste muchos códigos. Probá de nuevo en una hora.')
        break
      default:
        setError('No pudimos pedir el código. Probá de nuevo.')
    }
  }

  async function entrar(evento: FormEvent) {
    evento.preventDefault()
    if (!/^\d{6}$/.test(codigo.trim())) {
      setError('El código tiene 6 números.')
      return
    }
    setEnviando(true)
    setError(null)
    const resultado = await verificarCodigo(telefono.trim(), codigo.trim())
    setEnviando(false)
    switch (resultado.estado) {
      case 'ok':
        recordarAcceso(resultado.acceso)
        navegar('/mis-turnos', { replace: true })
        break
      case 'invalido':
        setError('El código no es válido o venció. Revisalo o pedí uno nuevo.')
        break
      case 'validacion':
        setError(resultado.mensaje)
        break
      case 'demasiados':
        setError('Demasiados intentos, esperá un minuto.')
        break
      default:
        setError('No pudimos verificar el código. Probá de nuevo.')
    }
  }

  return (
    <main className="reserva">
      <h1 className="reserva__titulo">Entrar con mi teléfono</h1>

      {etapa === 'telefono' ? (
        <form className="formulario" onSubmit={pedir} noValidate>
          <p className="reserva__ayuda">Te mandamos un código para ver tus turnos de este local.</p>
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="entrar-telefono">Celular</label>
            <input id="entrar-telefono" type="tel" autoComplete="tel" value={telefono}
              onChange={(e) => setTelefono(e.target.value)} aria-invalid={error !== null} />
            <p className="campo__ayuda">El mismo con el que reservaste.</p>
          </div>
          {error && <p className="campo__error" role="alert">{error}</p>}
          <button type="submit" className="btn btn-primary btn-lg" disabled={enviando}>
            {enviando ? 'Pidiendo…' : 'Pedir código'}
          </button>
        </form>
      ) : (
        <form className="formulario" onSubmit={entrar} noValidate>
          {/* HU-22 CA2: el mismo mensaje tenga o no turnos ese teléfono. */}
          <p className="reserva__ayuda" role="status">
            Si tenés turnos en este local, te mandamos un código de 6 números a {telefono.trim()}.
            Vence en {vigencia} minutos.
          </p>
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="entrar-codigo">Código</label>
            <input id="entrar-codigo" type="text" inputMode="numeric" autoComplete="one-time-code" maxLength={6}
              value={codigo} onChange={(e) => setCodigo(e.target.value)} aria-invalid={error !== null} />
          </div>
          {error && <p className="campo__error" role="alert">{error}</p>}
          <button type="submit" className="btn btn-primary btn-lg" disabled={enviando}>
            {enviando ? 'Entrando…' : 'Entrar'}
          </button>
          <div className="turno__acciones">
            <button type="button" className="btn btn-secondary" onClick={() => pedir()} disabled={enviando}>
              Pedir otro código
            </button>
            <button type="button" className="btn btn-secondary" disabled={enviando}
              onClick={() => { setEtapa('telefono'); setError(null) }}>
              Cambiar el celular
            </button>
          </div>
        </form>
      )}

      <Link className="link" to="/">Volver al inicio</Link>
    </main>
  )
}

export default PaginaEntrar
