import { useState, type FormEvent } from 'react'

import { moverTurno } from '../api/agenda'
import type { TurnoDeAgenda } from '../api/tipos'
import { fechaLarga } from '../fechas'
import { useSesion } from './sesion'

type Props = {
  turno: TurnoDeAgenda
  alGuardar: () => void
  alCancelar: () => void
}

// HU-29: mover un turno a otro día u hora, con el mismo profesional. Si el hueco está ocupado, el turno no cambia.
function FormularioMover({ turno, alGuardar, alCancelar }: Props) {
  const { sesionPerdida } = useSesion()
  const [fecha, setFecha] = useState(turno.inicio.slice(0, 10))
  const [hora, setHora] = useState(turno.inicio.slice(11, 16))
  const [aviso, setAviso] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    setAviso(null)
    const resultado = await moverTurno(turno.id, fecha, hora)
    setEnviando(false)
    if (resultado.estado === 'ok') {
      alGuardar()
    } else if (resultado.estado === 'sin-sesion') {
      sesionPerdida()
    } else {
      // CA2: el hueco nuevo se pisa con otro turno o un bloqueo.
      setAviso(resultado.estado === 'error' && resultado.mensaje ? resultado.mensaje : 'No pudimos mover el turno.')
    }
  }

  const quien = turno.clienteNombre ?? 'el cliente'
  return (
    <form className="formulario admin__formulario" onSubmit={guardar} noValidate aria-label={`Mover el turno de ${quien}`}>
      <h2 className="mis-turnos__titulo">Mover el turno de {quien}</h2>
      <p className="panel__texto">Ahora: {fechaLarga(turno.inicio.slice(0, 10))}, {turno.inicio.slice(11, 16)}</p>
      <div className="admin__fila">
        <div className="campo">
          <label className="campo__etiqueta" htmlFor="mover-fecha">Día nuevo</label>
          <input id="mover-fecha" type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
        </div>
        <div className="campo">
          <label className="campo__etiqueta" htmlFor="mover-hora">Hora nueva</label>
          <input id="mover-hora" type="time" step={300} value={hora} onChange={(e) => setHora(e.target.value)} />
        </div>
      </div>
      {aviso && <p className="campo__error" role="alert">{aviso}</p>}
      <div className="turno__acciones">
        <button type="submit" className="btn btn-primary" disabled={enviando}>{enviando ? 'Moviendo…' : 'Mover'}</button>
        <button type="button" className="btn btn-secondary" onClick={alCancelar} disabled={enviando}>Cancelar</button>
      </div>
    </form>
  )
}

export default FormularioMover
