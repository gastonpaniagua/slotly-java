import { useState } from 'react'
import { Navigate, Route, Routes } from 'react-router'

import type { LocalPublico } from '../api/tipos'
import PasoProfesional from './PasoProfesional'
import PasoServicios from './PasoServicios'
import './reserva.css'

type Props = {
  local: LocalPublico
}

// El asistente de reserva. Lo que el cliente va eligiendo vive acá, arriba de los pasos:
// así, al volver con "Atrás" (o con el botón del navegador), la selección sigue marcada (HU-05 CA3).
function Reserva({ local }: Props) {
  const [serviciosElegidos, setServiciosElegidos] = useState<string[]>([])

  return (
    <Routes>
      <Route
        path="servicios"
        element={
          <PasoServicios
            servicios={local.servicios}
            elegidos={serviciosElegidos}
            alCambiarElegidos={setServiciosElegidos}
          />
        }
      />
      <Route
        path="profesional"
        element={
          // Si se entra directo (o se recarga) sin servicios elegidos, se vuelve al paso 1.
          serviciosElegidos.length === 0
            ? <Navigate to="/reservar/servicios" replace />
            : <PasoProfesional />
        }
      />
      <Route path="*" element={<Navigate to="/reservar/servicios" replace />} />
    </Routes>
  )
}

export default Reserva
