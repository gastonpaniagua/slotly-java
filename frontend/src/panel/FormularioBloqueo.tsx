import { useState, type FormEvent } from 'react'

import { crearBloqueo, type TurnoEnElRango } from '../api/agenda'
import type { ProfesionalDeAgenda } from '../api/tipos'
import { fechaLarga, sumarDias } from '../fechas'
import { useSesion } from './sesion'

type Props = {
  // El día que se está mirando en la agenda: el bloqueo arranca ahí.
  dia: string
  // A quiénes puede bloquear el admin (los de su agenda). El profesional solo se bloquea a sí mismo.
  profesionales: ProfesionalDeAgenda[]
  alGuardar: () => void
  alCancelar: () => void
}

const TODO_EL_LOCAL = 'todos'

// Bloquear días u horas (HU-27): un feriado de todo el local, unas vacaciones, "el jueves salgo a las 18".
function FormularioBloqueo({ dia, profesionales, alGuardar, alCancelar }: Props) {
  const { usuario, sesionPerdida } = useSesion()
  const esAdmin = usuario.rol === 'admin'
  const [profesional, setProfesional] = useState(TODO_EL_LOCAL)
  const [todoElDia, setTodoElDia] = useState(false)
  const [fechaDesde, setFechaDesde] = useState(dia)
  const [horaDesde, setHoraDesde] = useState('10:00')
  const [fechaHasta, setFechaHasta] = useState(dia)
  const [horaHasta, setHoraHasta] = useState('13:00')
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState<string | null>(null)
  // CA2: los turnos que están en el rango y no dejan guardar el bloqueo.
  const [turnosEnElRango, setTurnosEnElRango] = useState<TurnoEnElRango[]>([])
  const [enviando, setEnviando] = useState(false)

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    // Días enteros: de las 00:00 del primero a las 00:00 del día siguiente al último.
    const desde = todoElDia ? `${fechaDesde}T00:00` : `${fechaDesde}T${horaDesde}`
    const hasta = todoElDia ? `${sumarDias(fechaHasta, 1)}T00:00` : `${fechaHasta}T${horaHasta}`
    setEnviando(true)
    setError(null)
    setTurnosEnElRango([])
    const resultado = await crearBloqueo({
      profesional: esAdmin ? profesional : null,
      desde,
      hasta,
      motivo: motivo.trim() === '' ? null : motivo.trim(),
    })
    setEnviando(false)
    if (resultado.estado === 'ok') {
      alGuardar()
    } else if (resultado.estado === 'sin-sesion') {
      sesionPerdida()
    } else if (resultado.estado === 'validacion') {
      setError(Object.values(resultado.errores)[0] ?? resultado.mensaje)
    } else if (resultado.estado === 'error' && resultado.codigo === 'TURNOS_EN_RANGO') {
      setError(`${resultado.mensaje ?? 'Hay turnos en ese rango.'} No se guardó: reprogramalos o cancelalos antes.`)
      setTurnosEnElRango((resultado.problema?.turnos as TurnoEnElRango[] | undefined) ?? [])
    } else {
      setError(resultado.estado === 'error' && resultado.mensaje ? resultado.mensaje : 'No pudimos guardar el bloqueo.')
    }
  }

  return (
    <form className="formulario admin__formulario" onSubmit={guardar} noValidate aria-label="Bloquear">
      <h2 className="mis-turnos__titulo">Bloquear</h2>
      {esAdmin && (
        <div className="campo">
          <label className="campo__etiqueta" htmlFor="bloqueo-profesional">A quién</label>
          <select id="bloqueo-profesional" className="admin__select" value={profesional} onChange={(e) => setProfesional(e.target.value)}>
            <option value={TODO_EL_LOCAL}>Todo el local</option>
            {profesionales.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
          </select>
        </div>
      )}
      <label className="admin__opcion">
        <input type="checkbox" checked={todoElDia} onChange={(e) => setTodoElDia(e.target.checked)} />
        Días enteros
      </label>
      <div className="admin__fila">
        <div className="campo">
          <label className="campo__etiqueta" htmlFor="bloqueo-fecha-desde">{todoElDia ? 'Desde el día' : 'Desde'}</label>
          <input id="bloqueo-fecha-desde" type="date" value={fechaDesde} onChange={(e) => setFechaDesde(e.target.value)} />
          {!todoElDia && (
            <input type="time" step={300} aria-label="Hora desde" value={horaDesde} onChange={(e) => setHoraDesde(e.target.value)} />
          )}
        </div>
        <div className="campo">
          <label className="campo__etiqueta" htmlFor="bloqueo-fecha-hasta">{todoElDia ? 'Hasta el día' : 'Hasta'}</label>
          <input id="bloqueo-fecha-hasta" type="date" value={fechaHasta} onChange={(e) => setFechaHasta(e.target.value)} />
          {!todoElDia && (
            <input type="time" step={300} aria-label="Hora hasta" value={horaHasta} onChange={(e) => setHoraHasta(e.target.value)} />
          )}
        </div>
      </div>
      <div className="campo">
        <label className="campo__etiqueta" htmlFor="bloqueo-motivo">Motivo</label>
        <input id="bloqueo-motivo" type="text" maxLength={120} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
        <p className="campo__ayuda">Opcional. Por ejemplo: Feriado, Vacaciones, Médico.</p>
      </div>

      {error && <p className="campo__error" role="alert">{error}</p>}
      {turnosEnElRango.length > 0 && (
        <ul aria-label="Turnos en el rango">
          {turnosEnElRango.map((turno) => (
            <li key={turno.id}>
              {fechaLarga(turno.inicio.slice(0, 10))}, {turno.inicio.slice(11, 16)} · {turno.profesionalNombre}
              {turno.clienteNombre && ` · ${turno.clienteNombre}`}
            </li>
          ))}
        </ul>
      )}

      <div className="turno__acciones">
        <button type="submit" className="btn btn-primary" disabled={enviando}>{enviando ? 'Guardando…' : 'Bloquear'}</button>
        <button type="button" className="btn btn-secondary" onClick={alCancelar} disabled={enviando}>Cancelar</button>
      </div>
    </form>
  )
}

export default FormularioBloqueo
