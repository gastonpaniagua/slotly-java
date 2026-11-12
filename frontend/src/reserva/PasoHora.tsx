import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'

import { obtenerHoras } from '../api/publico'
import type { ResultadoApartar } from '../api/reserva'
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
  // La hora que este cliente ya tiene apartada ese día: para los demás está ocupada, pero a él se le sigue mostrando.
  horaApartada: string | null
  alElegirHora: (hora: string | null) => void
  // "Siguiente": Reserva aparta la hora y, si sale bien, pasa al paso 5.
  alAvanzar: () => Promise<ResultadoApartar>
}

// Paso 4 de la reserva: elegir la hora (HU-07). La lista se actualiza sola (HU-09): cada 15 s y al volver a la
// pestaña. Si una hora se ocupa mientras el cliente mira, desaparece; si era la elegida, se desmarca y se avisa.
function PasoHora({ servicios, profesional, nombreProfesional, fecha, horaElegida, horaApartada, alElegirHora,
  alAvanzar }: Props) {
  const [horas, setHoras] = useState<string[] | null>(null)
  const [falloLaPrimera, setFalloLaPrimera] = useState(false)
  const [falloElRefresco, setFalloElRefresco] = useState(false)
  const [seOcupo, setSeOcupo] = useState(false)
  const [intento, setIntento] = useState(0)
  const [apartando, setApartando] = useState(false)
  const [avisoAlApartar, setAvisoAlApartar] = useState<string | null>(null)
  // La hora elegida y la apartada "al día": el refresco corre fuera del render y necesita el valor actual.
  const horaElegidaActual = useRef(horaElegida)
  const horaApartadaActual = useRef(horaApartada)
  useEffect(() => {
    horaElegidaActual.current = horaElegida
    horaApartadaActual.current = horaApartada
  }, [horaElegida, horaApartada])
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
      // HU-09 CA4: la hora elegida se acaba de ocupar (la que tiene apartada él no cuenta: está ocupada por él).
      const elegida = horaElegidaActual.current
      if (elegida !== null && !nuevas.includes(elegida) && elegida !== horaApartadaActual.current) {
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
    setAvisoAlApartar(null)
    alElegirHora(hora)
  }

  async function avanzar() {
    setApartando(true)
    setAvisoAlApartar(null)
    const resultado = await alAvanzar()
    if (resultado.estado === 'ok') {
      return // Reserva ya pasó al paso 5
    }
    setApartando(false)
    if (resultado.estado === 'ocupado') {
      // HU-12 CA1: se ocupó entre que la eligió y avanzó: se desmarca y la lista se actualiza ya.
      alElegirHora(null)
      setAvisoAlApartar('Ese horario se acaba de ocupar, elegí otro.')
      setIntento((n) => n + 1)
    } else if (resultado.estado === 'demasiados') {
      setAvisoAlApartar('Demasiados intentos, esperá un minuto.')
    } else {
      setAvisoAlApartar('No pudimos apartar el horario. Probá de nuevo.')
    }
  }

  // Las horas libres más la que este cliente ya tiene apartada, ordenadas.
  const horasAMostrar = horas === null ? null
    : [...new Set(horaApartada === null ? horas : [...horas, horaApartada])].sort()

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

      {avisoAlApartar && <p className="reserva__aviso" role="alert">{avisoAlApartar}</p>}

      {falloLaPrimera && (
        <div className="reserva__aviso" role="alert">
          <p>No pudimos cargar las horas.</p>
          <button type="button" className="btn btn-secondary reserva__reintentar" onClick={reintentar}>
            Probar de nuevo
          </button>
        </div>
      )}

      {horas === null && !falloLaPrimera && <p className="reserva__ayuda" role="status">Buscando horas libres…</p>}

      {horasAMostrar !== null && horasAMostrar.length === 0 && (
        <p className="reserva__aviso">Ya no quedan horas libres este día. Volvé y elegí otro día.</p>
      )}

      {horasAMostrar !== null && horasAMostrar.length > 0 && (
        <div className="horas" role="group" aria-label="Horas libres">
          {horasAMostrar.map((hora) => (
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
            disabled={horaElegida === null || apartando}
            onClick={avanzar}
          >
            {apartando ? 'Apartando…' : 'Siguiente'}
          </button>
        </div>
      </footer>
    </main>
  )
}

export default PasoHora
