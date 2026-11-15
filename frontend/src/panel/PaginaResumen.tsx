import { useEffect, useState } from 'react'

import { verResumen } from '../api/admin'
import type { ResumenDeLaSemana } from '../api/tipos'
import { fechaLarga, textoDeLaSemana } from '../fechas'
import { formatearPrecio } from '../formato'
import { useSesion } from './sesion'

// Un vistazo a cómo viene el local (HU-33): turnos de hoy y de la semana, ingresos estimados y faltas.
function PaginaResumen() {
  const { sesionPerdida } = useSesion()
  const [resumen, setResumen] = useState<ResumenDeLaSemana | null>(null)
  const [error, setError] = useState(false)
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    let vigente = true
    verResumen().then((resultado) => {
      if (!vigente) {
        return
      }
      if (resultado.estado === 'ok') {
        setResumen(resultado.datos)
        setError(false)
      } else if (resultado.estado === 'sin-sesion') {
        sesionPerdida()
      } else {
        setError(true)
      }
    })
    return () => {
      vigente = false
    }
  }, [intento, sesionPerdida])

  return (
    <section>
      <h1 className="panel__titulo">Resumen</h1>
      {error && (
        <div className="reserva__error" role="alert">
          <p>No pudimos cargar el resumen.</p>
          <button type="button" className="btn btn-secondary" onClick={() => setIntento((n) => n + 1)}>Probar de nuevo</button>
        </div>
      )}
      {resumen === null && !error && <p className="panel__texto" role="status">Cargando resumen…</p>}
      {resumen && (
        <>
          <p className="panel__texto">Semana del {textoDeLaSemana(resumen.semanaDesde)}. Hoy es {fechaLarga(resumen.hoy)}.</p>
          <dl className="resumen-semana">
            <div className="resumen-semana__dato">
              <dt>Turnos de hoy</dt>
              <dd>{resumen.turnosHoy}</dd>
            </div>
            <div className="resumen-semana__dato">
              <dt>Turnos de la semana</dt>
              <dd>{resumen.turnosSemana}</dd>
            </div>
            <div className="resumen-semana__dato">
              <dt>Ingresos estimados de la semana</dt>
              <dd>{formatearPrecio(resumen.ingresosSemana)}</dd>
            </div>
            <div className="resumen-semana__dato">
              <dt>No vinieron esta semana</dt>
              <dd>{resumen.faltasSemana}</dd>
            </div>
          </dl>
          <p className="admin__detalle">
            Los ingresos son la suma de los precios de los turnos de la semana que no se cancelaron: es una estimación.
          </p>
        </>
      )}
    </section>
  )
}

export default PaginaResumen
