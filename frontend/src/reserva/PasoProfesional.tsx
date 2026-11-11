import { Link } from 'react-router'

import BarraProgreso from './BarraProgreso'

// Paso 2 de la reserva: elegir profesional (HU-06). Se completa en el paso 24 del plan.
function PasoProfesional() {
  return (
    <main className="reserva">
      <BarraProgreso paso={2} />
      <h1 className="reserva__titulo">Elegí con quién</h1>
      <p className="reserva__ayuda">Próximamente.</p>
      <footer className="reserva__pie">
        <div className="reserva__botones">
          <Link className="btn btn-secondary" to="/reservar/servicios">Atrás</Link>
        </div>
      </footer>
    </main>
  )
}

export default PasoProfesional
