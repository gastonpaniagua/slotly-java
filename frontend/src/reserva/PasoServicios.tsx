import { Link, useNavigate } from 'react-router'

import type { ServicioPublico } from '../api/tipos'
import { formatearDuracion, formatearPrecio } from '../formato'
import BarraProgreso from './BarraProgreso'

type Props = {
  servicios: ServicioPublico[]
  elegidos: string[]
  alCambiarElegidos: (ids: string[]) => void
}

// Paso 1 de la reserva: elegir uno o varios servicios y ver el total de tiempo y precio (HU-05).
function PasoServicios({ servicios, elegidos, alCambiarElegidos }: Props) {
  const navegar = useNavigate()

  function alternar(id: string) {
    alCambiarElegidos(elegidos.includes(id) ? elegidos.filter((e) => e !== id) : [...elegidos, id])
  }

  const seleccion = servicios.filter((s) => elegidos.includes(s.id))
  const minutos = seleccion.reduce((suma, s) => suma + s.duracionMin, 0)
  const pesos = seleccion.reduce((suma, s) => suma + s.precio, 0)

  return (
    <main className="reserva">
      <BarraProgreso paso={1} />
      <h1 className="reserva__titulo">Elegí los servicios</h1>
      <p className="reserva__ayuda">Podés elegir más de uno.</p>

      <ul className="opciones">
        {servicios.map((servicio) => (
          <li key={servicio.id}>
            <label className="opcion">
              <input
                type="checkbox"
                className="opcion__marca"
                checked={elegidos.includes(servicio.id)}
                onChange={() => alternar(servicio.id)}
              />
              <span className="opcion__datos">
                <span className="opcion__nombre">{servicio.nombre}</span>
                {servicio.descripcion && <span className="opcion__descripcion">{servicio.descripcion}</span>}
                <span className="opcion__detalle">{formatearDuracion(servicio.duracionMin)}</span>
              </span>
              <span className="opcion__precio price">{formatearPrecio(servicio.precio)}</span>
            </label>
          </li>
        ))}
      </ul>

      <footer className="reserva__pie">
        {/* HU-05 CA2: sin servicios elegidos no hay resumen. */}
        {seleccion.length > 0 && (
          <p className="reserva__resumen" role="status" aria-label="Resumen">
            <span className="price">{formatearDuracion(minutos)} · {formatearPrecio(pesos)}</span>
          </p>
        )}
        <div className="reserva__botones">
          <Link className="btn btn-secondary" to="/">Atrás</Link>
          <button
            type="button"
            className="btn btn-primary"
            disabled={seleccion.length === 0}
            onClick={() => navegar('/reservar/profesional')}
          >
            Siguiente
          </button>
        </div>
      </footer>
    </main>
  )
}

export default PasoServicios
