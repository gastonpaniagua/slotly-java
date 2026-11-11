import { useEffect, type CSSProperties } from 'react'

import type { LocalPublico } from '../api/tipos'
import { variablesDeAcento } from '../acento'
import Encabezado from './Encabezado'
import Horarios from './Horarios'
import ListaServicios from './ListaServicios'
import Ubicacion from './Ubicacion'
import './home.css'

type Props = {
  local: LocalPublico
}

// La home pública del local (HU-01, HU-02, HU-03). Todo lo que usa --accent-* toma el color del local.
function Home({ local }: Props) {
  // Título de la pestaña y descripción con los datos del local (HU-03 CA1 y CA4).
  useEffect(() => {
    document.title = local.nombre
    document
      .querySelector('meta[name="description"]')
      ?.setAttribute('content', local.descripcion ?? `Reservá tu turno en ${local.nombre}.`)
  }, [local.nombre, local.descripcion])

  // Las variables se calculan para que cualquier color se lea bien (contraste AA). Sin color, el de Slotly.
  const estilo = variablesDeAcento(local.colorAcento) as CSSProperties

  return (
    <main className="home" style={estilo}>
      <Encabezado local={local} />
      <ListaServicios servicios={local.servicios} />
      <Horarios horarios={local.horarios} />
      {/* HU-02 CA4: sin dirección no hay sección de ubicación (ni un mapa vacío). */}
      {local.direccion && (
        <Ubicacion
          nombre={local.nombre}
          direccion={local.direccion}
          ciudad={local.ciudad}
          latitud={local.latitud}
          longitud={local.longitud}
        />
      )}
    </main>
  )
}

export default Home
