import { Link } from 'react-router'

import { fechaLarga } from '../fechas'
import { leerTurnos, linkDelTurno } from './memoria'
import './reserva.css'

// "Mis turnos": los turnos reservados desde este navegador (HU-10 CA9, HU-14 CA1). Cada uno lleva a su "Mi turno".
function PaginaMisTurnos() {
  const turnos = leerTurnos()

  return (
    <main className="reserva">
      <h1 className="reserva__titulo">Mis turnos</h1>
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
      <Link className="btn btn-secondary" to="/">Volver al inicio</Link>
    </main>
  )
}

export default PaginaMisTurnos
