import { Link } from 'react-router'

import type { LocalPublico } from '../api/tipos'

type Props = {
  local: LocalPublico
}

// Nombre, logo y descripción del local (HU-03 CA1). Sin logo, se muestra la inicial en un círculo del color del local.
// Si hay servicios para reservar, el botón principal lleva al paso 1 de la reserva.
function Encabezado({ local }: Props) {
  return (
    <header className="encabezado">
      {local.logoUrl ? (
        <img className="encabezado__logo" src={local.logoUrl} alt={`Logo de ${local.nombre}`} />
      ) : (
        <div className="encabezado__logo encabezado__logo--inicial" aria-hidden="true">
          {local.nombre.charAt(0).toUpperCase()}
        </div>
      )}
      <h1 className="encabezado__nombre">{local.nombre}</h1>
      {local.descripcion && <p className="encabezado__descripcion">{local.descripcion}</p>}
      {local.servicios.length > 0 && (
        <Link className="btn btn-primary btn-lg encabezado__reservar" to="/reservar/servicios">
          Reservar turno
        </Link>
      )}
      {/* HU-10 CA9 y HU-22: sus turnos a mano; desde otro dispositivo, ahí se entra con el teléfono. */}
      <Link className="link" to="/mis-turnos">Mis turnos</Link>
    </header>
  )
}

export default Encabezado
