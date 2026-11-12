import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'

import { obtenerTurno, type ResultadoTurno } from '../api/reserva'
import type { LocalPublico, TurnoPublico } from '../api/tipos'
import { fechaLarga } from '../fechas'
import { formatearDuracion, formatearPrecio, linkDeWhatsApp } from '../formato'
import BarraProgreso from './BarraProgreso'
import DatosTransferencia from './DatosTransferencia'
import './reserva.css'

type Props = {
  local: LocalPublico
}

// "Mi turno" (/turno/{id}#t={token}, HU-19) y también la pantalla de confirmación (paso 6, HU-10 CA1).
// Siempre pide el turno a la API con el token: si se recarga, se ve el mismo turno y no se crea otro (CA7).
// Recién reservado, la navegación trae { recienConfirmado: true } y se muestra "¡Listo!" con la barra completa.
function PaginaTurno({ local }: Props) {
  const { id = '' } = useParams()
  const ubicacion = useLocation()
  const token = new URLSearchParams(ubicacion.hash.replace(/^#/, '')).get('t') ?? ''
  const recienConfirmado = (ubicacion.state as { recienConfirmado?: boolean } | null)?.recienConfirmado === true
  const [intento, setIntento] = useState(0)
  const clave = `${id}|${token}|${intento}`
  const [respuesta, setRespuesta] = useState<{ clave: string; resultado: ResultadoTurno } | null>(null)

  useEffect(() => {
    let vigente = true
    obtenerTurno(id, token).then((resultado) => {
      if (vigente) {
        setRespuesta({ clave, resultado })
      }
    })
    return () => {
      vigente = false
    }
  }, [id, token, clave])

  const resultado = respuesta?.clave === clave ? respuesta.resultado : null

  if (resultado === null) {
    return <main className="reserva"><p className="reserva__ayuda" role="status">Buscando tu turno…</p></main>
  }

  // HU-19 CA2 y CA3: un link roto o vencido lo dice, y ofrece entrar con el teléfono para ver los turnos igual.
  if (resultado.estado === 'invalido' || resultado.estado === 'vencido') {
    const vencido = resultado.estado === 'vencido'
    return (
      <main className="reserva">
        <h1 className="reserva__titulo">{vencido ? 'Este link venció' : 'Este link no es válido'}</h1>
        <p className="reserva__ayuda">
          {vencido
            ? 'Los links de los turnos dejan de andar 30 días después del turno.'
            : 'Revisá que el link esté completo, tal como lo recibiste.'}
          {' '}Podés ver tus turnos entrando con tu teléfono.
        </p>
        <div className="turno__acciones">
          <Link className="btn btn-primary" to="/entrar">Entrar con mi teléfono</Link>
          <Link className="btn btn-secondary" to="/">Volver al inicio</Link>
        </div>
      </main>
    )
  }

  if (resultado.estado === 'error') {
    return (
      <main className="reserva">
        <h1 className="reserva__titulo">No pudimos cargar tu turno</h1>
        <p className="reserva__ayuda">Revisá tu conexión y probá de nuevo.</p>
        <button type="button" className="btn btn-secondary" onClick={() => setIntento((n) => n + 1)}>
          Probar de nuevo
        </button>
      </main>
    )
  }

  const { turno } = resultado
  return (
    <main className="reserva">
      {recienConfirmado && <BarraProgreso paso={6} />}
      <h1 className="reserva__titulo">{recienConfirmado ? '¡Listo! Tu turno quedó reservado' : 'Tu turno'}</h1>
      {turno.estado === 'cancelado' && <p className="reserva__aviso" role="alert">Este turno está cancelado.</p>}

      <section className="resumen" aria-label="Tu turno">
        <p className="resumen__cuando">{fechaLarga(turno.fecha)}, {turno.hora}</p>
        <p>Con {turno.profesionalNombre}</p>
        <ul className="resumen__servicios">
          {turno.servicios.map((s) => (
            <li key={s.nombre}>{s.nombre} · {formatearDuracion(s.duracionMin)} · {formatearPrecio(s.precio)}</li>
          ))}
        </ul>
        <p className="resumen__total price">
          Total: {formatearDuracion(turno.duracionMin)} · {formatearPrecio(turno.precioTotal)}
        </p>
        <p>Pago: {turno.formaPago === 'transferencia' ? 'Transferencia' : 'Efectivo, en el local'}</p>
        {/* HU-19 CA1: la dirección, para saber a dónde ir. */}
        {local.direccion && (
          <p>Dónde: {local.direccion}{local.ciudad ? `, ${local.ciudad}` : ''}</p>
        )}
      </section>

      {/* HU-10 CA5: los datos para transferir, también en la confirmación. */}
      {turno.transferencia && turno.estado === 'confirmado' && <DatosTransferencia datos={turno.transferencia} />}

      <Cambios turno={turno} local={local} />

      {recienConfirmado && (
        <p className="reserva__ayuda">
          Guardá esta página: es el link de tu turno. También lo vas a encontrar en "Mis turnos", en la página del local.
        </p>
      )}

      <Link className="btn btn-secondary" to="/">Volver al inicio</Link>
    </main>
  )
}

// Qué puede hacer el cliente con su turno. Cancelar y reprogramar llegan en los pasos 42 y 44 de la Etapa 6;
// fuera de plazo, el local decide (HU-20 CA2).
function Cambios({ turno, local }: { turno: TurnoPublico; local: LocalPublico }) {
  if (turno.estado !== 'confirmado') {
    return null
  }
  if (turno.yaPaso) {
    return <p className="reserva__ayuda">Este turno ya pasó.</p>
  }
  if (!turno.puedeCambiar) {
    return (
      <div className="turno__aviso" role="note">
        <p>Falta poco para tu turno: para cancelarlo o cambiarlo, escribile al local.</p>
        {local.whatsapp && (
          <a className="btn btn-secondary" href={linkDeWhatsApp(local.whatsapp)} target="_blank" rel="noreferrer">
            Escribir por WhatsApp
          </a>
        )}
      </div>
    )
  }
  return null
}

export default PaginaTurno
