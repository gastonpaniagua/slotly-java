import { useEffect, useState } from 'react'

import { guardarHorario, verHorario } from '../api/admin'
import type { FranjaHorario, ProfesionalAdmin, TurnoFuturo } from '../api/tipos'
import { fechaLarga } from '../fechas'
import { useSesion } from './sesion'

// Los días en el orden de la semana argentina (lunes primero), con el número que usa la base (0 domingo).
const DIAS = [
  { numero: 1, nombre: 'Lunes' },
  { numero: 2, nombre: 'Martes' },
  { numero: 3, nombre: 'Miércoles' },
  { numero: 4, nombre: 'Jueves' },
  { numero: 5, nombre: 'Viernes' },
  { numero: 6, nombre: 'Sábado' },
  { numero: 0, nombre: 'Domingo' },
]

type Props = {
  profesional: ProfesionalAdmin
  alCerrar: () => void
}

// El horario semanal de un profesional, con pausas (HU-26): por cada día, sus franjas. Una pausa son dos franjas
// el mismo día (10 a 13 y 16 a 20). Se guarda todo junto.
function EditorHorario({ profesional, alCerrar }: Props) {
  const { sesionPerdida } = useSesion()
  const [franjas, setFranjas] = useState<FranjaHorario[] | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  // Después de guardar: los turnos que quedaron fuera del horario nuevo (CA3).
  const [guardado, setGuardado] = useState<TurnoFuturo[] | null>(null)

  useEffect(() => {
    let vigente = true
    verHorario(profesional.id).then((resultado) => {
      if (!vigente) {
        return
      }
      if (resultado.estado === 'ok') {
        setFranjas(resultado.datos)
      } else if (resultado.estado === 'sin-sesion') {
        sesionPerdida()
      } else {
        setAviso('No pudimos cargar el horario.')
      }
    })
    return () => {
      vigente = false
    }
  }, [profesional.id, sesionPerdida])

  function cambiar(indice: number, campo: 'desde' | 'hasta', valor: string) {
    setFranjas((actuales) => actuales && actuales.map((f, i) => (i === indice ? { ...f, [campo]: valor } : f)))
    setGuardado(null)
  }

  // Una franja nueva: la mañana si el día está vacío; si ya tiene, la tarde (para armar la pausa).
  function agregar(diaSemana: number) {
    const tiene = (franjas ?? []).some((f) => f.diaSemana === diaSemana)
    const nueva = tiene ? { diaSemana, desde: '16:00', hasta: '20:00' } : { diaSemana, desde: '10:00', hasta: '13:00' }
    setFranjas((actuales) => [...(actuales ?? []), nueva])
    setGuardado(null)
  }

  function quitar(indice: number) {
    setFranjas((actuales) => actuales && actuales.filter((_, i) => i !== indice))
    setGuardado(null)
  }

  async function guardar() {
    if (franjas === null) {
      return
    }
    setEnviando(true)
    setAviso(null)
    const resultado = await guardarHorario(profesional.id, franjas)
    setEnviando(false)
    if (resultado.estado === 'ok') {
      setFranjas(resultado.datos.franjas)
      setGuardado(resultado.datos.turnosAfuera)
    } else if (resultado.estado === 'validacion') {
      // CA2: franjas que se pisan o al revés, con el día y las horas que hay que revisar.
      setAviso(resultado.errores.franjas ?? resultado.mensaje)
    } else if (resultado.estado === 'sin-sesion') {
      sesionPerdida()
    } else {
      setAviso('No pudimos guardar el horario. Probá de nuevo.')
    }
  }

  return (
    <section className="admin__formulario" aria-label={`Horario de ${profesional.nombre}`}>
      <h2 className="mis-turnos__titulo">Horario de {profesional.nombre}</h2>
      <p className="panel__texto">Para una pausa, cargá dos franjas el mismo día (por ejemplo, 10 a 13 y 16 a 20).</p>

      {franjas === null && !aviso && <p className="panel__texto" role="status">Cargando horario…</p>}
      {franjas !== null && (
        <div className="horario">
          {DIAS.map((dia) => (
            <div key={dia.numero} className="horario__dia" role="group" aria-label={dia.nombre}>
              <span className="horario__nombre">{dia.nombre}</span>
              <div className="horario__franjas">
                {franjas.map((franja, indice) => franja.diaSemana !== dia.numero ? null : (
                  <div key={indice} className="horario__franja">
                    <input type="time" step={300} value={franja.desde} aria-label={`${dia.nombre}, desde`}
                      onChange={(e) => cambiar(indice, 'desde', e.target.value)} />
                    <span aria-hidden="true">a</span>
                    <input type="time" step={300} value={franja.hasta} aria-label={`${dia.nombre}, hasta`}
                      onChange={(e) => cambiar(indice, 'hasta', e.target.value)} />
                    <button type="button" className="link" onClick={() => quitar(indice)}
                      aria-label={`Quitar ${dia.nombre} de ${franja.desde} a ${franja.hasta}`}>Quitar</button>
                  </div>
                ))}
                {!franjas.some((f) => f.diaSemana === dia.numero) && <span className="admin__detalle">No trabaja</span>}
                <button type="button" className="link" onClick={() => agregar(dia.numero)}>Agregar franja</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {aviso && <p className="campo__error" role="alert">{aviso}</p>}
      {guardado !== null && (
        <div className="turno__aviso" role="status">
          <p>Horario guardado.</p>
          {guardado.length > 0 && (
            <>
              <p>
                {guardado.length === 1 ? 'Hay 1 turno' : `Hay ${guardado.length} turnos`} fuera del horario nuevo. No se
                cancelan solos: movelos o cancelalos desde la agenda.
              </p>
              <ul>
                {guardado.map((turno) => (
                  <li key={turno.id}>
                    {fechaLarga(turno.inicio.slice(0, 10))}, {turno.inicio.slice(11, 16)}
                    {turno.clienteNombre && ` · ${turno.clienteNombre}`}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}

      <div className="turno__acciones">
        <button type="button" className="btn btn-primary" onClick={guardar} disabled={enviando || franjas === null}>
          {enviando ? 'Guardando…' : 'Guardar horario'}
        </button>
        <button type="button" className="btn btn-secondary" onClick={alCerrar} disabled={enviando}>
          {guardado !== null ? 'Listo' : 'Cancelar'}
        </button>
      </div>
    </section>
  )
}

export default EditorHorario
