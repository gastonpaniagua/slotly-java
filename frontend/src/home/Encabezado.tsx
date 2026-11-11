import type { LocalPublico } from '../api/tipos'

type Props = {
  local: LocalPublico
}

// Nombre, logo y descripción del local (HU-03 CA1). Sin logo, se muestra la inicial en un círculo del color del local.
function Encabezado({ local }: Props) {
  return (
    <header className="encabezado">
      <div className="encabezado__contenido">
        {local.logoUrl ? (
          <img className="encabezado__logo" src={local.logoUrl} alt={`Logo de ${local.nombre}`} />
        ) : (
          <div className="encabezado__logo encabezado__logo--inicial" aria-hidden="true">
            {local.nombre.charAt(0).toUpperCase()}
          </div>
        )}
        <h1 className="encabezado__nombre">{local.nombre}</h1>
        {local.descripcion && <p className="encabezado__descripcion">{local.descripcion}</p>}
      </div>
    </header>
  )
}

export default Encabezado
