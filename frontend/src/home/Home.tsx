import type { LocalPublico } from '../api/tipos'
import Encabezado from './Encabezado'
import Horarios from './Horarios'
import ListaServicios from './ListaServicios'
import Ubicacion from './Ubicacion'
import './home.css'

type Props = {
  local: LocalPublico
}

// La home pública del local (HU-01, HU-02, HU-03). El color y el título los pone SitioDelLocal.
function Home({ local }: Props) {
  return (
    <main className="home">
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
