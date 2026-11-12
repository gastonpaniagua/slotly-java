import { Link } from 'react-router'

import { fechaLarga } from '../fechas'
import BarraProgreso from './BarraProgreso'

type Props = {
  fecha: string
  nombreProfesional: string
}

// Paso 4 de la reserva: elegir la hora (HU-07, HU-09). Se completa en el paso 29 del plan.
function PasoHora({ fecha, nombreProfesional }: Props) {
  return (
    <main className="reserva">
      <BarraProgreso paso={4} />
      <h1 className="reserva__titulo">Elegí la hora</h1>
      <p className="reserva__ayuda">{fechaLarga(fecha)}, con {nombreProfesional}.</p>
      <p className="seccion__vacio">Próximamente.</p>
      <footer className="reserva__pie">
        <div className="reserva__botones reserva__botones--uno">
          <Link className="btn btn-secondary" to="/reservar/dia">Atrás</Link>
        </div>
      </footer>
    </main>
  )
}

export default PasoHora
