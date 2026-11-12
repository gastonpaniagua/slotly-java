import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router'

import type { ResultadoConfirmar } from '../api/reserva'
import type { ApartadoCreado, ServicioPublico } from '../api/tipos'
import { fechaLarga } from '../fechas'
import { formatearDuracion, formatearPrecio } from '../formato'
import BarraProgreso from './BarraProgreso'
import { validarDatos, type DatosCliente, type ErroresDeDatos } from './datosCliente'
import DatosTransferencia from './DatosTransferencia'

type Props = {
  apartado: ApartadoCreado
  // Cuándo se creó el apartado según este navegador: la cuenta regresiva se mide desde ahí.
  creadoEnMs: number
  servicios: ServicioPublico[]
  datos: DatosCliente
  alCambiarDatos: (datos: DatosCliente) => void
  alConfirmar: () => Promise<ResultadoConfirmar>
}

// Paso 5 de la reserva: los datos del cliente y "Confirmar" (HU-10). La hora ya está apartada (HU-11):
// arriba se ve cuánto tiempo queda para confirmar (CA4).
function PasoDatos({ apartado, creadoEnMs, servicios, datos, alCambiarDatos, alConfirmar }: Props) {
  const navegar = useNavigate()
  const [ahoraMs, setAhoraMs] = useState(() => Date.now())
  const [errores, setErrores] = useState<ErroresDeDatos>({})
  const [enviando, setEnviando] = useState(false)
  const [vencido, setVencido] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)

  // La cuenta regresiva: se mide desde que se creó el apartado en este navegador, así no depende de que el reloj
  // del celular esté en hora (solo de que avance).
  useEffect(() => {
    const cadaSegundo = setInterval(() => setAhoraMs(Date.now()), 1000)
    return () => clearInterval(cadaSegundo)
  }, [])
  const segundosRestantes = Math.max(0, apartado.segundosParaConfirmar - Math.floor((ahoraMs - creadoEnMs) / 1000))
  const seVencio = vencido || segundosRestantes === 0

  function cambiar<K extends keyof DatosCliente>(campo: K, valor: DatosCliente[K]) {
    alCambiarDatos({ ...datos, [campo]: valor })
    setErrores((anteriores) => ({ ...anteriores, [campo]: undefined }))
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setAviso(null)
    const encontrados = validarDatos(datos)
    if (Object.keys(encontrados).length > 0) {
      // HU-10 CA2 y CA4: no se envía; cada error se marca junto a su campo y el foco va al primero.
      setErrores(encontrados)
      document.getElementById(`campo-${Object.keys(encontrados)[0]}`)?.focus()
      return
    }
    setEnviando(true) // HU-10 CA6: mientras se envía, el botón no se puede volver a tocar
    const resultado = await alConfirmar()
    if (resultado.estado === 'ok') {
      return // Reserva ya pasó a la confirmación
    }
    setEnviando(false)
    switch (resultado.estado) {
      case 'validacion':
        setErrores(resultado.errores)
        break
      case 'vencido':
        setVencido(true)
        break
      case 'maximo':
        setAviso(resultado.mensaje)
        break
      case 'demasiados':
        setAviso('Demasiados intentos, esperá un minuto.')
        break
      default:
        setAviso('No pudimos confirmar el turno. Probá de nuevo.')
    }
  }

  const minutos = Math.floor(segundosRestantes / 60)
  const segundos = String(segundosRestantes % 60).padStart(2, '0')

  return (
    <main className="reserva">
      <BarraProgreso paso={5} />
      <h1 className="reserva__titulo">Tus datos</h1>

      <section className="resumen" aria-label="Tu turno">
        <p className="resumen__cuando">{fechaLarga(apartado.fecha)}, {apartado.hora}</p>
        <p>Con {apartado.profesionalNombre}</p>
        <p>{servicios.map((s) => s.nombre).join(' + ')}</p>
        <p className="resumen__total price">
          {formatearDuracion(apartado.duracionMin)} · {formatearPrecio(apartado.precioTotal)}
        </p>
      </section>

      {seVencio ? (
        // HU-11 CA2
        <div className="reserva__aviso" role="alert">
          <p>Se venció el tiempo para confirmar. Elegí la hora de nuevo.</p>
          <button type="button" className="btn btn-primary reserva__reintentar" onClick={() => navegar('/reservar/hora')}>
            Elegir otra hora
          </button>
        </div>
      ) : (
        // HU-11 CA4
        <p className="cuenta-regresiva" role="timer" aria-live="off">
          Te guardamos este horario por <strong>{minutos}:{segundos}</strong>
        </p>
      )}

      <form className="formulario" noValidate onSubmit={enviar}>
        <Campo id="nombre" etiqueta="Nombre" error={errores.nombre}>
          <input id="campo-nombre" type="text" autoComplete="name" maxLength={80} value={datos.nombre}
            aria-invalid={Boolean(errores.nombre)} aria-describedby={errores.nombre ? 'error-nombre' : undefined}
            onChange={(e) => cambiar('nombre', e.target.value)} />
        </Campo>

        <Campo id="telefono" etiqueta="Celular" ayuda="Te escribimos por WhatsApp si cambia algo." error={errores.telefono}>
          <input id="campo-telefono" type="tel" autoComplete="tel" inputMode="tel" placeholder="11 2345-6789"
            value={datos.telefono} aria-invalid={Boolean(errores.telefono)}
            aria-describedby={errores.telefono ? 'error-telefono' : undefined}
            onChange={(e) => cambiar('telefono', e.target.value)} />
        </Campo>

        <Campo id="email" etiqueta="Mail (opcional)" error={errores.email}>
          <input id="campo-email" type="email" autoComplete="email" value={datos.email}
            aria-invalid={Boolean(errores.email)} aria-describedby={errores.email ? 'error-email' : undefined}
            onChange={(e) => cambiar('email', e.target.value)} />
        </Campo>

        <fieldset className="campo" aria-describedby={errores.formaPago ? 'error-formaPago' : undefined}>
          <legend className="campo__etiqueta">¿Cómo vas a pagar?</legend>
          <div className="campo__opciones">
            {(['efectivo', 'transferencia'] as const).map((forma) => (
              <label key={forma} className="campo__opcion">
                <input id={forma === 'efectivo' ? 'campo-formaPago' : undefined} type="radio" name="formaPago"
                  value={forma} checked={datos.formaPago === forma} onChange={() => cambiar('formaPago', forma)} />
                {forma === 'efectivo' ? 'Efectivo, en el local' : 'Transferencia'}
              </label>
            ))}
          </div>
          {errores.formaPago && <p id="error-formaPago" className="campo__error">{errores.formaPago}</p>}
        </fieldset>

        {datos.formaPago === 'transferencia' && apartado.transferencia && (
          // HU-10 CA5
          <DatosTransferencia datos={apartado.transferencia} />
        )}

        <div className="campo">
          <label className="campo__consentimiento">
            <input id="campo-consentimiento" type="checkbox" checked={datos.consentimiento}
              aria-invalid={Boolean(errores.consentimiento)}
              aria-describedby={errores.consentimiento ? 'error-consentimiento' : undefined}
              onChange={(e) => cambiar('consentimiento', e.target.checked)} />
            <span>
              Acepto la <a className="link" href="/privacidad" target="_blank" rel="noopener noreferrer">política de
              privacidad</a>.
            </span>
          </label>
          {errores.consentimiento && <p id="error-consentimiento" className="campo__error">{errores.consentimiento}</p>}
        </div>

        {aviso && <p className="reserva__aviso" role="alert">{aviso}</p>}

        <footer className="reserva__pie">
          <div className="reserva__botones">
            <button type="button" className="btn btn-secondary" onClick={() => navegar('/reservar/hora')}>Atrás</button>
            <button type="submit" className="btn btn-primary" disabled={enviando || seVencio}>
              {enviando ? 'Confirmando…' : 'Confirmar'}
            </button>
          </div>
        </footer>
      </form>
    </main>
  )
}

type CampoProps = {
  id: string
  etiqueta: string
  ayuda?: string
  error?: string
  children: ReactNode
}

// Etiqueta + control + error debajo (HU-10 CA4: "veo el error junto al campo").
function Campo({ id, etiqueta, ayuda, error, children }: CampoProps) {
  return (
    <div className="campo">
      <label className="campo__etiqueta" htmlFor={`campo-${id}`}>{etiqueta}</label>
      {children}
      {ayuda && !error && <p className="campo__ayuda">{ayuda}</p>}
      {error && <p id={`error-${id}`} className="campo__error">{error}</p>}
    </div>
  )
}

export default PasoDatos
