import { useState, type FormEvent } from 'react'

import { crearTurnoManual } from '../api/agenda'
import type { ProfesionalDeAgenda, ServicioPublico } from '../api/tipos'
import { fechaLarga } from '../fechas'
import { useSesion } from './sesion'

type Props = {
  // El hueco que se tocó: el día y de quién es la agenda.
  fecha: string
  profesional: ProfesionalDeAgenda
  servicios: ServicioPublico[]
  alGuardar: () => void
  alCancelar: () => void
}

// HU-28: cargar en dos toques un turno para quien llama o entra sin reserva. Solo el nombre es obligatorio
// (y la hora y un servicio); el teléfono, si lo deja, une el turno a ese cliente.
function FormularioTurnoManual({ fecha, profesional, servicios, alGuardar, alCancelar }: Props) {
  const { usuario, sesionPerdida } = useSesion()
  const [hora, setHora] = useState('10:00')
  const [elegidos, setElegidos] = useState<string[]>([])
  const [nombre, setNombre] = useState('')
  const [telefono, setTelefono] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [aviso, setAviso] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  function alternar(id: string) {
    setElegidos((actuales) => (actuales.includes(id) ? actuales.filter((s) => s !== id) : [...actuales, id]))
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    setErrores({})
    setAviso(null)
    const resultado = await crearTurnoManual({
      // El admin carga en la agenda que tocó; el profesional, siempre en la suya.
      profesional: usuario.rol === 'admin' ? profesional.id : null,
      fecha,
      hora,
      servicioIds: elegidos,
      clienteNombre: nombre,
      clienteTelefono: telefono.trim() === '' ? null : telefono,
    })
    setEnviando(false)
    if (resultado.estado === 'ok') {
      alGuardar()
    } else if (resultado.estado === 'validacion') {
      setErrores(resultado.errores)
      setAviso(Object.keys(resultado.errores).length === 0 ? resultado.mensaje : null)
    } else if (resultado.estado === 'sin-sesion') {
      sesionPerdida()
    } else {
      // CA2: se pisa con otro turno o un bloqueo.
      setAviso(resultado.estado === 'error' && resultado.mensaje ? resultado.mensaje : 'No pudimos guardar el turno.')
    }
  }

  const titulo = `Nuevo turno con ${profesional.nombre}`
  return (
    <form className="formulario admin__formulario" onSubmit={guardar} noValidate aria-label={titulo}>
      <h2 className="mis-turnos__titulo">{titulo}</h2>
      <p className="panel__texto">{fechaLarga(fecha)}</p>
      <div className="campo">
        <label className="campo__etiqueta" htmlFor="manual-hora">Hora</label>
        <input id="manual-hora" type="time" step={300} value={hora} onChange={(e) => setHora(e.target.value)} />
      </div>
      <fieldset className="campo">
        <legend className="campo__etiqueta">Servicios</legend>
        {servicios.map((servicio) => (
          <label key={servicio.id} className="admin__opcion">
            <input type="checkbox" checked={elegidos.includes(servicio.id)} onChange={() => alternar(servicio.id)} />
            {servicio.nombre}
          </label>
        ))}
        {errores.servicioIds && <p className="campo__error">{errores.servicioIds}</p>}
      </fieldset>
      <div className="campo">
        <label className="campo__etiqueta" htmlFor="manual-nombre">Nombre del cliente</label>
        <input id="manual-nombre" type="text" value={nombre} onChange={(e) => setNombre(e.target.value)}
          aria-invalid={Boolean(errores.clienteNombre)} />
        {errores.clienteNombre && <p className="campo__error">{errores.clienteNombre}</p>}
      </div>
      <div className="campo">
        <label className="campo__etiqueta" htmlFor="manual-telefono">Celular</label>
        <input id="manual-telefono" type="tel" value={telefono} onChange={(e) => setTelefono(e.target.value)}
          aria-invalid={Boolean(errores.clienteTelefono)} />
        {errores.clienteTelefono ? <p className="campo__error">{errores.clienteTelefono}</p>
          : <p className="campo__ayuda">Opcional.</p>}
      </div>
      {aviso && <p className="campo__error" role="alert">{aviso}</p>}
      <div className="turno__acciones">
        <button type="submit" className="btn btn-primary" disabled={enviando}>{enviando ? 'Guardando…' : 'Guardar turno'}</button>
        <button type="button" className="btn btn-secondary" onClick={alCancelar} disabled={enviando}>Cancelar</button>
      </div>
    </form>
  )
}

export default FormularioTurnoManual
