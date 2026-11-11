import type { ServicioPublico } from '../api/tipos'
import { formatearDuracion, formatearPrecio } from '../formato'

type Props = {
  servicios: ServicioPublico[]
}

// Los servicios visibles del local, en el orden que eligió (el back ya los manda filtrados y ordenados).
function ListaServicios({ servicios }: Props) {
  return (
    <section className="seccion" aria-labelledby="titulo-servicios">
      <h2 id="titulo-servicios" className="seccion__titulo">Servicios</h2>
      {servicios.length === 0 ? (
        // HU-01 CA3: un local sin servicios no es un error.
        <p className="seccion__vacio">Todavía no hay servicios para reservar.</p>
      ) : (
        <ul className="servicios">
          {servicios.map((servicio) => (
            <li key={servicio.id} className="servicio">
              <div className="servicio__datos">
                <h3 className="servicio__nombre">{servicio.nombre}</h3>
                {servicio.descripcion && <p className="servicio__descripcion">{servicio.descripcion}</p>}
                <p className="servicio__duracion">{formatearDuracion(servicio.duracionMin)}</p>
              </div>
              <p className="servicio__precio price">{formatearPrecio(servicio.precio)}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default ListaServicios
