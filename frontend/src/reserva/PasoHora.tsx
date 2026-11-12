import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { obtenerHoras } from '../api/publico'
import { fechaLarga } from '../fechas'
import BarraProgreso from './BarraProgreso'

// Cada cuánto se vuelve a pedir la lista mientras el cliente mira (HU-09 CA1).
const REFRESCO_MS = 15_000

type Props = {
  servicios: string[]
  profesional: string
  nombreProfesional: string
  fecha: string
  horaElegida: string | null
  alElegirHora: (hora: string | null) => void
}

// Paso 4 de la reserva: elegir la hora (HU-07). La lista se actualiza sola (HU-09): cada 15 s y al volver a la
// pestaña. Si una hora se ocupa mientras el cliente mira, desaparece; si era la elegida, se desmarca y se avisa.
function PasoHora({ servicios, profesional, nombreProfesional, fecha, horaElegida, alElegirHora }: Props) {
  const navegar = useNavigate()
  const [horas, setHoras] = useState<string[] | null>(null)
  const [falloLaPrimera, setFalloLaPrimera] = useState(false)
  const [falloElRefresco, setFalloElRefresco] = useState(false)
  const [seOcupo, setSeOcupo] = useState(false)
  const [intento, setIntento] = useState(0)
  // La hora elegida "al día": el refresco corre fuera del render y necesita el valor actual, no el del principio.
  const horaElegidaActual = useRef(horaElegida)
  useEffect(() => {
    horaElegidaActual.current = horaElegida
  }, [horaElegida])
  // Si ya llegó alguna lista: decide si un error es "no pudimos cargar" o "no pudimos actualizar".
  const yaHayLista = useRef(false)

  useEffect(() => {
    let vigente = true

    async function cargar() {
      const resultado = await obtenerHoras(servicios, profesional, fecha)
      if (!vigente) {
        return
      }
      if (resultado.estado === 'error') {
        // HU-09 CA3: si ya había una lista se sigue mostrando, con un aviso discreto; el próximo refresco reintenta.
        if (yaHayLista.current) {
          setFalloElRefresco(true)
        } else {
          setFalloLaPrimera(true)
        }
        return
      }
      const nuevas = resultado.datos.horas
      yaHayLista.current = true
      setHoras(nuevas)
      setFalloLaPrimera(false)
      setFalloElRefresco(false)
      // HU-09 CA4: la hora elegida se acaba de ocupar.
      if (horaElegidaActual.current !== null && !nuevas.includes(horaElegidaActual.current)) {
        alElegirHora(null)
        setSeOcupo(true)
      }
    }

    cargar()
    const cadaTanto = setInterval(cargar, REFRESCO_MS)
    // HU-09 CA2: al volver a la pestaña se refresca en ese momento.
    function alVolver() {
      if (document.visibilityState === 'visible') {
        cargar()
      }
    }
    document.addEventListener('visibilitychange', alVolver)
    window.addEventListener('focus', alVolver)

    return () => {
      vigente = false
      clearInterval(cadaTanto)
      document.removeEventListener('visibilitychange', alVolver)
      window.removeEventListener('focus', alVolver)
    }
  }, [servicios, profesional, fecha, intento, alElegirHora])

  function elegir(hora: string) {
    setSeOcupo(false)
    alElegirHora(hora)
  }

  function reintentar() {
    setFalloLaPrimera(false)
    setIntento((n) => n + 1)
  }

  return (
    <main className="reserva">
      <BarraProgreso paso={4} />
      <h1 className="reserva__titulo">Elegí la hora</h1>
      <p className="reserva__ayuda">{fechaLarga(fecha)}, con {nombreProfesional}.</p>

      {seOcupo && (
        <p className="reserva__aviso" role="alert">Esa hora se acaba de ocupar. Elegí otra.</p>
      )}

      {falloLaPrimera && (
        <div className="reserva__aviso" role="alert">
          <p>No pudimos cargar las horas.</p>
          <button type="button" className="btn btn-secondary reserva__reintentar" onClick={reintentar}>
            Probar de nuevo
          </button>
        </div>
      )}

      {horas === null && !falloLaPrimera && <p className="reserva__ayuda" role="status">Buscando horas libres…</p>}

      {horas !== null && horas.length === 0 && (
        <p className="reserva__aviso">Ya no quedan horas libres este día. Volvé y elegí otro día.</p>
      )}

      {horas !== null && horas.length > 0 && (
        <div className="horas" role="group" aria-label="Horas libres">
          {horas.map((hora) => (
            <button
              key={hora}
              type="button"
              className="horas__hora"
              aria-pressed={hora === horaElegida}
              onClick={() => elegir(hora)}
            >
              {hora}
            </button>
          ))}
        </div>
      )}

      {falloElRefresco && (
        <p className="horas__aviso-discreto" role="status">
          No pudimos actualizar las horas. Lo volvemos a intentar en unos segundos.
        </p>
      )}

      <footer className="reserva__pie">
        {horaElegida && <p className="reserva__resumen">{fechaLarga(fecha)} · {horaElegida}</p>}
        <div className="reserva__botones">
          <Link className="btn btn-secondary" to="/reservar/dia">Atrás</Link>
          <button
            type="button"
            className="btn btn-primary"
            disabled={horaElegida === null}
            onClick={() => navegar('/reservar/datos')}
          >
            Siguiente
          </button>
        </div>
      </footer>
    </main>
  )
}

export default PasoHora
