import { useState } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router'

import { obtenerProfesionales } from '../api/publico'
import type { LocalPublico, ProfesionalPublico } from '../api/tipos'
import { CUALQUIERA } from './constantes'
import PasoDia from './PasoDia'
import PasoHora from './PasoHora'
import PasoProfesional from './PasoProfesional'
import PasoServicios from './PasoServicios'
import './reserva.css'

type Props = {
  local: LocalPublico
}

// El asistente de reserva. Lo que el cliente va eligiendo vive acá, arriba de los pasos:
// así, al volver con "Atrás" (o con el botón del navegador), la selección sigue marcada (HU-05 CA3).
function Reserva({ local }: Props) {
  const navegar = useNavigate()
  const [serviciosElegidos, setServiciosElegidos] = useState<string[]>([])
  // Quiénes hacen todos los servicios elegidos (null = todavía no se pidieron para esta selección).
  const [profesionales, setProfesionales] = useState<ProfesionalPublico[] | null>(null)
  // El id del profesional elegido, CUALQUIERA, o null si todavía no eligió.
  const [profesionalElegido, setProfesionalElegido] = useState<string | null>(null)
  // true si había un solo profesional y el paso 2 se salteó (HU-06 CA3).
  const [pasoProfesionalSalteado, setPasoProfesionalSalteado] = useState(false)
  // El día elegido en el paso 3 ("2026-11-17"), o null.
  const [fechaElegida, setFechaElegida] = useState<string | null>(null)
  const [buscando, setBuscando] = useState(false)
  const [errorAlBuscar, setErrorAlBuscar] = useState(false)

  // Si cambian los servicios, lo elegido después ya no vale: se vuelve a pedir y a elegir.
  function cambiarServicios(ids: string[]) {
    setServiciosElegidos(ids)
    setProfesionales(null)
    setProfesionalElegido(null)
    setPasoProfesionalSalteado(false)
    setFechaElegida(null)
  }

  // Con otro profesional cambian los días con lugar: el día se vuelve a elegir.
  function elegirProfesional(id: string) {
    if (id !== profesionalElegido) {
      setProfesionalElegido(id)
      setFechaElegida(null)
    }
  }

  // "Siguiente" en el paso 1: se piden los profesionales antes de avanzar, para saber si hay que saltar el paso 2.
  async function avanzarDesdeServicios() {
    setBuscando(true)
    setErrorAlBuscar(false)
    const resultado = await obtenerProfesionales(serviciosElegidos)
    setBuscando(false)
    if (resultado.estado === 'error') {
      setErrorAlBuscar(true)
      return
    }
    setProfesionales(resultado.profesionales)
    if (resultado.profesionales.length === 1) {
      // HU-06 CA3: con un solo profesional no hay nada que elegir; queda elegido y se pasa al día.
      setProfesionalElegido(resultado.profesionales[0].id)
      setPasoProfesionalSalteado(true)
      navegar('/reservar/dia')
    } else {
      setPasoProfesionalSalteado(false)
      navegar('/reservar/profesional')
    }
  }

  const nombreElegido = profesionalElegido === CUALQUIERA
    ? 'Cualquiera disponible'
    : profesionales?.find((p) => p.id === profesionalElegido)?.nombre ?? null

  return (
    <Routes>
      <Route
        path="servicios"
        element={
          <PasoServicios
            servicios={local.servicios}
            elegidos={serviciosElegidos}
            alCambiarElegidos={cambiarServicios}
            alAvanzar={avanzarDesdeServicios}
            buscando={buscando}
            error={errorAlBuscar}
          />
        }
      />
      <Route
        path="profesional"
        element={
          // Si se entra directo (o se recarga) sin haber pasado por el paso 1, se vuelve ahí.
          profesionales === null
            ? <Navigate to="/reservar/servicios" replace />
            : (
              <PasoProfesional
                profesionales={profesionales}
                elegido={profesionalElegido}
                alElegir={elegirProfesional}
              />
            )
        }
      />
      <Route
        path="dia"
        element={
          nombreElegido === null || profesionalElegido === null
            ? <Navigate to="/reservar/servicios" replace />
            : (
              <PasoDia
                // Si cambian los servicios o el profesional, el calendario arranca de cero.
                key={`${profesionalElegido}|${serviciosElegidos.join(',')}`}
                servicios={serviciosElegidos}
                profesional={profesionalElegido}
                nombreProfesional={nombreElegido}
                // HU-06 CA3: si el paso 2 se salteó, "Atrás" vuelve al paso 1.
                atras={pasoProfesionalSalteado ? '/reservar/servicios' : '/reservar/profesional'}
                fechaElegida={fechaElegida}
                alElegirFecha={setFechaElegida}
              />
            )
        }
      />
      <Route
        path="hora"
        element={
          nombreElegido === null || fechaElegida === null
            ? <Navigate to="/reservar/servicios" replace />
            : <PasoHora fecha={fechaElegida} nombreProfesional={nombreElegido} />
        }
      />
      <Route path="*" element={<Navigate to="/reservar/servicios" replace />} />
    </Routes>
  )
}

export default Reserva
