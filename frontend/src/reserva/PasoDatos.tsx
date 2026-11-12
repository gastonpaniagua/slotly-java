import { Link } from 'react-router'

import { fechaLarga } from '../fechas'
import BarraProgreso from './BarraProgreso'

type Props = {
  fecha: string
  hora: string
  nombreProfesional: string
}

// Paso 5 de la reserva: dejar los datos y confirmar. Llega en la Etapa 4 (HU-10 a HU-15).
function PasoDatos({ fecha, hora, nombreProfesional }: Props) {
  return (
    <main className="reserva">
      <BarraProgreso paso={5} />
      <h1 className="reserva__titulo">Tus datos</h1>
      <p className="reserva__ayuda">{fechaLarga(fecha)} a las {hora}, con {nombreProfesional}.</p>
      <p className="seccion__vacio">Próximamente vas a poder dejar tus datos y confirmar el turno.</p>
      <footer className="reserva__pie">
        <div className="reserva__botones reserva__botones--uno">
          <Link className="btn btn-secondary" to="/reservar/hora">Atrás</Link>
        </div>
      </footer>
    </main>
  )
}

export default PasoDatos
