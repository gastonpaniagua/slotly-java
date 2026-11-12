import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { obtenerDias } from '../api/publico'
import { diasDeLaSemana, fechaLarga, lunesDe, nombreCortoDelDia, numeroDelDia, sumarDias, textoDeLaSemana } from '../fechas'
import BarraProgreso from './BarraProgreso'

type Props = {
  servicios: string[]
  profesional: string
  nombreProfesional: string
  atras: string
  fechaElegida: string | null
  alElegirFecha: (fecha: string) => void
}

type Limites = { hoy: string; ultimoDia: string }

// Paso 3 de la reserva: elegir el día en un calendario semanal (HU-08).
// Los días sin lugar (cerrados, completos o fuera del tope) aparecen en gris y no se pueden tocar.
function PasoDia({ servicios, profesional, nombreProfesional, atras, fechaElegida, alElegirFecha }: Props) {
  const navegar = useNavigate()
  // fecha → hay lugar. Se va completando de a 6 semanas.
  const [dias, setDias] = useState<Map<string, boolean>>(new Map())
  const [limites, setLimites] = useState<Limites | null>(null)
  const [semana, setSemana] = useState<string | null>(fechaElegida ? lunesDe(fechaElegida) : null)
  const [estado, setEstado] = useState<'cargando' | 'listo' | 'error'>('cargando')
  // undefined = la primera carga (el back decide desde qué semana); una fecha = cargar desde ese lunes.
  const [desdeACargar, setDesdeACargar] = useState<string | undefined>(undefined)
  const [intento, setIntento] = useState(0)
  // El día que ya estaba elegido al entrar (si volvió con "Atrás"): se respeta y no se elige otro.
  const fechaAlEntrar = useRef(fechaElegida)

  useEffect(() => {
    let vigente = true
    obtenerDias(servicios, profesional, desdeACargar).then((resultado) => {
      if (!vigente) {
        return
      }
      if (resultado.estado === 'error') {
        setEstado('error')
        return
      }
      const { hoy, ultimoDia, dias: nuevos } = resultado.datos
      setLimites({ hoy, ultimoDia })
      setDias((anteriores) => new Map([...anteriores, ...nuevos.map((d) => [d.fecha, d.hayLugar] as const)]))
      setEstado('listo')
      if (desdeACargar === undefined && fechaAlEntrar.current === null) {
        // HU-08 CA4: al entrar queda elegido el primer día con lugar.
        const primero = nuevos.find((d) => d.hayLugar)
        if (primero) {
          alElegirFecha(primero.fecha)
        }
        setSemana(lunesDe(primero?.fecha ?? hoy))
      }
    })
    return () => {
      vigente = false
    }
  }, [servicios, profesional, desdeACargar, intento, alElegirFecha])

  // HU-08 CA3: no se va a semanas anteriores a la actual ni después de la que tiene el último día permitido.
  const primeraSemana = limites ? lunesDe(limites.hoy) : null
  const ultimaSemana = limites ? lunesDe(limites.ultimoDia) : null

  function irASemana(lunes: string) {
    setSemana(lunes)
    // Si esa semana todavía no se pidió, se piden 6 semanas desde ahí.
    if (!dias.has(lunes)) {
      setEstado('cargando')
      setDesdeACargar(lunes)
    }
  }

  function reintentar() {
    setEstado('cargando')
    setIntento((n) => n + 1)
  }

  const hayAlgunDiaConLugar = [...dias.values()].some(Boolean)

  return (
    <main className="reserva">
      <BarraProgreso paso={3} />
      <h1 className="reserva__titulo">Elegí el día</h1>
      <p className="reserva__ayuda">Con {nombreProfesional}.</p>

      {estado === 'error' && (
        <div className="reserva__aviso" role="alert">
          <p>No pudimos cargar los días disponibles.</p>
          <button type="button" className="btn btn-secondary reserva__reintentar" onClick={reintentar}>
            Probar de nuevo
          </button>
        </div>
      )}

      {semana !== null && primeraSemana !== null && ultimaSemana !== null && (
        <div className="calendario">
          <div className="calendario__nav">
            <button
              type="button"
              className="btn btn-secondary calendario__flecha"
              aria-label="Semana anterior"
              disabled={semana <= primeraSemana}
              onClick={() => irASemana(sumarDias(semana, -7))}
            >
              ‹
            </button>
            <p className="calendario__semana">{textoDeLaSemana(semana)}</p>
            <button
              type="button"
              className="btn btn-secondary calendario__flecha"
              aria-label="Semana siguiente"
              disabled={semana >= ultimaSemana}
              onClick={() => irASemana(sumarDias(semana, 7))}
            >
              ›
            </button>
          </div>

          <div className="calendario__dias">
            {diasDeLaSemana(semana).map((fecha) => {
              const hayLugar = dias.get(fecha) === true
              return (
                <button
                  key={fecha}
                  type="button"
                  className="calendario__dia"
                  disabled={!hayLugar}
                  aria-pressed={fecha === fechaElegida}
                  aria-label={hayLugar ? fechaLarga(fecha) : `${fechaLarga(fecha)}, sin lugar`}
                  onClick={() => alElegirFecha(fecha)}
                >
                  <span className="calendario__nombre">{nombreCortoDelDia(fecha)}</span>
                  <span className="calendario__numero">{numeroDelDia(fecha)}</span>
                </button>
              )
            })}
          </div>
        </div>
      )}

      {estado === 'cargando' && <p className="reserva__ayuda" role="status">Buscando días con lugar…</p>}

      {estado === 'listo' && !hayAlgunDiaConLugar && (
        <p className="reserva__aviso" role="alert">
          No hay lugar en las próximas semanas. Probá con otro profesional o sacando un servicio.
        </p>
      )}

      <footer className="reserva__pie">
        {fechaElegida && <p className="reserva__resumen">{fechaLarga(fechaElegida)}</p>}
        <div className="reserva__botones">
          <Link className="btn btn-secondary" to={atras}>Atrás</Link>
          <button
            type="button"
            className="btn btn-primary"
            disabled={fechaElegida === null}
            onClick={() => navegar('/reservar/hora')}
          >
            Siguiente
          </button>
        </div>
      </footer>
    </main>
  )
}

export default PasoDia
