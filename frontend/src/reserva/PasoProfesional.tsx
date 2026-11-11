import { Link, useNavigate } from 'react-router'

import type { ProfesionalPublico } from '../api/tipos'
import BarraProgreso from './BarraProgreso'
import { CUALQUIERA } from './constantes'

type Props = {
  profesionales: ProfesionalPublico[]
  elegido: string | null
  alElegir: (id: string) => void
}

// Paso 2 de la reserva: elegir con quién atenderse, o "Cualquiera disponible" (HU-06).
// Solo llegan los profesionales activos que hacen todos los servicios elegidos (lo filtra el back).
function PasoProfesional({ profesionales, elegido, alElegir }: Props) {
  const navegar = useNavigate()

  // HU-06 CA2: nadie hace todos los servicios elegidos.
  if (profesionales.length === 0) {
    return (
      <main className="reserva">
        <BarraProgreso paso={2} />
        <h1 className="reserva__titulo">Elegí con quién</h1>
        <p className="reserva__aviso" role="alert">
          Ningún profesional hace esta combinación, probá sacando un servicio.
        </p>
        <footer className="reserva__pie">
          <div className="reserva__botones reserva__botones--uno">
            <Link className="btn btn-secondary" to="/reservar/servicios">Atrás</Link>
          </div>
        </footer>
      </main>
    )
  }

  // "Cualquiera disponible" va primero: es la opción para conseguir el turno más cercano.
  const opciones = [{ id: CUALQUIERA, nombre: 'Cualquiera disponible', fotoUrl: null }, ...profesionales]

  return (
    <main className="reserva">
      <BarraProgreso paso={2} />
      <h1 className="reserva__titulo">Elegí con quién</h1>

      <fieldset className="opciones-grupo">
        <legend className="solo-lectores">Profesional</legend>
        <ul className="opciones">
          {opciones.map((opcion) => (
            <li key={opcion.id}>
              <label className="opcion opcion--centrada">
                <input
                  type="radio"
                  name="profesional"
                  className="opcion__marca"
                  value={opcion.id}
                  checked={elegido === opcion.id}
                  onChange={() => alElegir(opcion.id)}
                />
                {opcion.fotoUrl ? (
                  <img className="opcion__foto" src={opcion.fotoUrl} alt="" />
                ) : (
                  <span className="opcion__foto opcion__foto--inicial" aria-hidden="true">
                    {opcion.id === CUALQUIERA ? '★' : opcion.nombre.charAt(0).toUpperCase()}
                  </span>
                )}
                <span className="opcion__datos">
                  <span className="opcion__nombre">{opcion.nombre}</span>
                  {opcion.id === CUALQUIERA && (
                    <span className="opcion__detalle">Te asignamos a quien tenga lugar.</span>
                  )}
                </span>
              </label>
            </li>
          ))}
        </ul>
      </fieldset>

      <footer className="reserva__pie">
        <div className="reserva__botones">
          <Link className="btn btn-secondary" to="/reservar/servicios">Atrás</Link>
          <button
            type="button"
            className="btn btn-primary"
            disabled={elegido === null}
            onClick={() => navegar('/reservar/dia')}
          >
            Siguiente
          </button>
        </div>
      </footer>
    </main>
  )
}

export default PasoProfesional
