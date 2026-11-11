import { Link } from 'react-router'

import BarraProgreso from './BarraProgreso'

type Props = {
  profesional: string
  atras: string
}

// Paso 3 de la reserva: elegir el día. Llega en la Etapa 3 (disponibilidad); por ahora confirma con quién sería.
function PasoDia({ profesional, atras }: Props) {
  return (
    <main className="reserva">
      <BarraProgreso paso={3} />
      <h1 className="reserva__titulo">Elegí el día</h1>
      <p className="reserva__ayuda">Con {profesional}.</p>
      <p className="seccion__vacio">Próximamente vas a poder elegir el día y la hora.</p>
      <footer className="reserva__pie">
        <div className="reserva__botones reserva__botones--uno">
          <Link className="btn btn-secondary" to={atras}>Atrás</Link>
        </div>
      </footer>
    </main>
  )
}

export default PasoDia
