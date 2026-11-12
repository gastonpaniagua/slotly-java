import { useEffect, type CSSProperties } from 'react'
import { Navigate, Route, Routes } from 'react-router'

import type { LocalPublico } from '../api/tipos'
import { variablesDeAcento } from '../acento'
import Home from '../home/Home'
import PaginaPrivacidad from '../PaginaPrivacidad'
import PaginaMisTurnos from '../reserva/PaginaMisTurnos'
import PaginaTurno from '../reserva/PaginaTurno'
import Reserva from '../reserva/Reserva'

type Props = {
  local: LocalPublico
}

// Todo lo que se ve en el subdominio de un local: la home y la reserva.
// Acá se aplican una sola vez el color del local y el título de la pestaña, para todas las pantallas.
function SitioDelLocal({ local }: Props) {
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
    <div className="sitio" style={estilo}>
      <Routes>
        <Route index element={<Home local={local} />} />
        <Route path="reservar/*" element={<Reserva local={local} />} />
        <Route path="privacidad" element={<PaginaPrivacidad />} />
        <Route path="turno/:id" element={<PaginaTurno />} />
        <Route path="mis-turnos" element={<PaginaMisTurnos />} />
        {/* Cualquier otra dirección vuelve a la home del local. */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  )
}

export default SitioDelLocal
