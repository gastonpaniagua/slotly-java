import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'

import { obtenerTurno } from '../api/reserva'
import type { TurnoPublico } from '../api/tipos'
import { fechaLarga } from '../fechas'
import { formatearDuracion, formatearPrecio } from '../formato'
import BarraProgreso from './BarraProgreso'
import DatosTransferencia from './DatosTransferencia'
import './reserva.css'

// "Mi turno" (/turno/{id}#t={token}) y también la pantalla de confirmación (paso 6, HU-10 CA1).
// Siempre pide el turno a la API con el token: si se recarga, se ve el mismo turno y no se crea otro (CA7).
// Recién reservado, la navegación trae { recienConfirmado: true } y se muestra "¡Listo!" con la barra completa.
function PaginaTurno() {
  const { id = '' } = useParams()
  const ubicacion = useLocation()
  const token = new URLSearchParams(ubicacion.hash.replace(/^#/, '')).get('t') ?? ''
  const recienConfirmado = (ubicacion.state as { recienConfirmado?: boolean } | null)?.recienConfirmado === true
  const [turno, setTurno] = useState<TurnoPublico | null | 'no-encontrado'>(null)

  useEffect(() => {
    let vigente = true
    obtenerTurno(id, token).then((resultado) => {
      if (vigente) {
        setTurno(resultado ?? 'no-encontrado')
      }
    })
    return () => {
      vigente = false
    }
  }, [id, token])

  if (turno === null) {
    return <main className="reserva"><p className="reserva__ayuda" role="status">Buscando tu turno…</p></main>
  }

  if (turno === 'no-encontrado') {
    return (
      <main className="reserva">
        <h1 className="reserva__titulo">No encontramos ese turno</h1>
        <p className="reserva__ayuda">Revisá que el link esté completo. Si el turno ya pasó, el link deja de andar.</p>
        <Link className="btn btn-secondary" to="/">Volver al inicio</Link>
      </main>
    )
  }

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
      </section>

      {/* HU-10 CA5: los datos para transferir, también en la confirmación. */}
      {turno.transferencia && <DatosTransferencia datos={turno.transferencia} />}

      {recienConfirmado && (
        <p className="reserva__ayuda">
          Guardá esta página: es el link de tu turno. También lo vas a encontrar en "Mis turnos", en la página del local.
        </p>
      )}

      <Link className="btn btn-secondary" to="/">Volver al inicio</Link>
    </main>
  )
}

export default PaginaTurno
