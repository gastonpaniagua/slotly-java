import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { obtenerMisTurnos, type ResultadoMisTurnos } from '../api/acceso'
import type { TurnoDelCliente } from '../api/tipos'
import { fechaLarga } from '../fechas'
import { leerAcceso, leerTurnos, linkDelTurno, olvidarAcceso } from './memoria'
import './reserva.css'

const ESTADOS: Record<string, string> = {
  confirmado: 'Pasado',
  completado: 'Fuiste',
  ausente: 'No fuiste',
  cancelado: 'Cancelado',
}

// "Mis turnos". Si entró con su teléfono (HU-22), los pide a la API: próximos e historial, con "Reservar lo mismo"
// (HU-23). Si no, muestra los turnos reservados desde este navegador (HU-10 CA9) y ofrece entrar con el teléfono.
function PaginaMisTurnos() {
  const navegar = useNavigate()
  const [acceso, setAcceso] = useState(() => leerAcceso())
  const [respuesta, setRespuesta] = useState<{ token: string; resultado: ResultadoMisTurnos } | null>(null)
  const [vencio, setVencio] = useState(false)

  useEffect(() => {
    if (acceso === null) {
      return
    }
    let vigente = true
    obtenerMisTurnos(acceso.token).then((resultado) => {
      if (!vigente) {
        return
      }
      if (resultado.estado === 'sin-acceso') {
        olvidarAcceso()
        setAcceso(null)
        setVencio(true)
      } else {
        setRespuesta({ token: acceso.token, resultado })
      }
    })
    return () => {
      vigente = false
    }
  }, [acceso])

  function salir() {
    olvidarAcceso()
    setAcceso(null)
  }

  // HU-23 CA2 y CA3: la reserva arranca con los mismos servicios y profesional (Reserva decide en qué paso).
  function reservarLoMismo(turno: TurnoDelCliente) {
    navegar('/reservar/servicios', { state: { repetir: { servicioIds: turno.servicioIds, profesionalId: turno.profesionalId } } })
  }

  const resultado = acceso !== null && respuesta?.token === acceso.token ? respuesta.resultado : null

  return (
    <main className="reserva">
      <h1 className="reserva__titulo">Mis turnos</h1>
      {vencio && <p className="reserva__aviso" role="alert">Tu acceso venció. Volvé a entrar con tu teléfono.</p>}

      {acceso === null && <TurnosDeEsteNavegador />}

      {acceso !== null && resultado === null && <p className="reserva__ayuda" role="status">Buscando tus turnos…</p>}
      {resultado?.estado === 'error' && (
        <p className="reserva__aviso" role="alert">No pudimos cargar tus turnos. Probá de nuevo en un rato.</p>
      )}
      {resultado?.estado === 'ok' && (
        <>
          <section aria-labelledby="titulo-proximos">
            <h2 id="titulo-proximos" className="mis-turnos__titulo">Próximos</h2>
            {resultado.datos.proximos.length === 0
              ? <p className="reserva__ayuda">No tenés turnos reservados.</p>
              : <ListaDeTurnos turnos={resultado.datos.proximos} />}
          </section>
          <section aria-labelledby="titulo-historial">
            <h2 id="titulo-historial" className="mis-turnos__titulo">Historial</h2>
            {resultado.datos.historial.length === 0
              ? <p className="reserva__ayuda">Todavía no hay turnos anteriores.</p>
              : <ListaDeTurnos turnos={resultado.datos.historial} conEstado alRepetir={reservarLoMismo} />}
          </section>
          <button type="button" className="btn btn-secondary" onClick={salir}>Salir en este navegador</button>
        </>
      )}

      <Link className="btn btn-secondary" to="/">Volver al inicio</Link>
    </main>
  )
}

type ListaProps = {
  turnos: TurnoDelCliente[]
  conEstado?: boolean
  alRepetir?: (turno: TurnoDelCliente) => void
}

function ListaDeTurnos({ turnos, conEstado = false, alRepetir }: ListaProps) {
  return (
    <ul className="mis-turnos__lista">
      {turnos.map((t) => (
        <li key={t.turno.id} className="mis-turnos__item">
          {/* Sin # en el link: el turno se abre con el acceso del cliente. */}
          <Link className="mis-turnos__link" to={`/turno/${t.turno.id}`}>
            <span className="opcion__nombre">{fechaLarga(t.turno.fecha)}, {t.turno.hora}</span>
            <span className="opcion__detalle">
              {t.turno.servicios.map((s) => s.nombre).join(' + ')} · con {t.turno.profesionalNombre}
              {conEstado && ` · ${ESTADOS[t.turno.estado] ?? t.turno.estado}`}
            </span>
          </Link>
          {alRepetir && (
            <button type="button" className="btn btn-secondary" onClick={() => alRepetir(t)}>Reservar lo mismo</button>
          )}
        </li>
      ))}
    </ul>
  )
}

// Sin haber entrado con el teléfono: los links guardados en este navegador.
function TurnosDeEsteNavegador() {
  const turnos = leerTurnos()
  return (
    <>
      {turnos.length === 0 ? (
        <p className="reserva__ayuda">Todavía no reservaste turnos desde este navegador.</p>
      ) : (
        <ul className="opciones">
          {turnos.map((turno) => (
            <li key={turno.id}>
              <Link className="opcion" to={linkDelTurno(turno)}>
                <span className="opcion__datos">
                  <span className="opcion__nombre">{fechaLarga(turno.fecha)}, {turno.hora}</span>
                  <span className="opcion__detalle">Ver el turno</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <div className="turno__aviso">
        <p>¿Reservaste desde otro celular o computadora? Entrá con tu teléfono para ver todos tus turnos.</p>
        <Link className="btn btn-primary" to="/entrar">Entrar con mi teléfono</Link>
      </div>
    </>
  )
}

export default PaginaMisTurnos
