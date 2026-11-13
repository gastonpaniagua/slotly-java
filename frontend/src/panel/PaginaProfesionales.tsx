import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'

import {
  crearProfesional, crearUsuarioProfesional, darDeAlta, darDeBaja, editarProfesional, listarProfesionales,
  listarServicios, subirImagen, turnosFuturosDe,
} from '../api/admin'
import type { ResultadoGuardar } from '../api/panel'
import type { ProfesionalAdmin, ServicioAdmin, TurnoFuturo } from '../api/tipos'
import { fechaLarga } from '../fechas'
import EditorHorario from './EditorHorario'
import { useSesion } from './sesion'

// Lo que se está haciendo con un profesional: editarlo, darle usuario o confirmar su baja (con sus turnos).
type Accion =
  | { tipo: 'editar'; profesional: ProfesionalAdmin | null }
  | { tipo: 'usuario'; profesional: ProfesionalAdmin }
  | { tipo: 'horario'; profesional: ProfesionalAdmin }
  | { tipo: 'baja'; profesional: ProfesionalAdmin; turnos: TurnoFuturo[] }

// Profesionales del local (HU-25): alta con foto y servicios, su horario (HU-26), su usuario del panel, baja y alta.
function PaginaProfesionales() {
  const { sesionPerdida } = useSesion()
  const [profesionales, setProfesionales] = useState<ProfesionalAdmin[] | null>(null)
  const [servicios, setServicios] = useState<ServicioAdmin[]>([])
  const [error, setError] = useState<string | null>(null)
  const [intento, setIntento] = useState(0)
  const [accion, setAccion] = useState<Accion | null>(null)

  useEffect(() => {
    let vigente = true
    Promise.all([listarProfesionales(), listarServicios()]).then(([lista, deServicios]) => {
      if (!vigente) {
        return
      }
      if (lista.estado === 'sin-sesion' || deServicios.estado === 'sin-sesion') {
        sesionPerdida()
      } else if (lista.estado === 'ok' && deServicios.estado === 'ok') {
        setProfesionales(lista.datos)
        setServicios(deServicios.datos)
      } else {
        setError('No pudimos cargar los profesionales.')
      }
    })
    return () => {
      vigente = false
    }
  }, [intento, sesionPerdida])

  function terminar() {
    setAccion(null)
    setIntento((n) => n + 1)
  }

  // CA3: antes de la baja se piden sus turnos por delante, para mostrarlos y pedir confirmación.
  async function pedirBaja(profesional: ProfesionalAdmin) {
    const turnos = await turnosFuturosDe(profesional.id)
    if (turnos.estado === 'ok') {
      setAccion({ tipo: 'baja', profesional, turnos: turnos.datos })
    } else if (turnos.estado === 'sin-sesion') {
      sesionPerdida()
    } else {
      setError('No pudimos revisar sus turnos. Probá de nuevo.')
    }
  }

  async function volverAlAlta(profesional: ProfesionalAdmin) {
    const resultado = await darDeAlta(profesional.id)
    if (resultado.estado === 'ok') {
      terminar()
    } else if (resultado.estado === 'sin-sesion') {
      sesionPerdida()
    } else {
      setError('No pudimos darlo de alta. Probá de nuevo.')
    }
  }

  const nombreDe = (id: string) => servicios.find((s) => s.id === id)?.nombre ?? 'Servicio'

  return (
    <section>
      <div className="admin__barra">
        <h1 className="panel__titulo">Profesionales</h1>
        {accion === null && (
          <button type="button" className="btn btn-primary" onClick={() => setAccion({ tipo: 'editar', profesional: null })}>
            Nuevo profesional
          </button>
        )}
      </div>

      {error && <p className="reserva__aviso" role="alert">{error}</p>}

      {accion?.tipo === 'editar' && (
        <FormularioProfesional profesional={accion.profesional} servicios={servicios}
          alGuardar={terminar} alCancelar={() => setAccion(null)} />
      )}
      {accion?.tipo === 'usuario' && (
        <FormularioUsuario profesional={accion.profesional} alGuardar={terminar} alCancelar={() => setAccion(null)} />
      )}
      {accion?.tipo === 'horario' && (
        <EditorHorario profesional={accion.profesional} alCerrar={terminar} />
      )}
      {accion?.tipo === 'baja' && (
        <ConfirmarBaja profesional={accion.profesional} turnos={accion.turnos}
          alTerminar={terminar} alCancelar={() => setAccion(null)} />
      )}

      {profesionales === null && !error && <p className="panel__texto" role="status">Cargando profesionales…</p>}
      {profesionales !== null && profesionales.length === 0 && (
        <p className="panel__texto">Todavía no hay profesionales. Cargá el primero.</p>
      )}
      {profesionales !== null && profesionales.length > 0 && (
        <ul className="admin__lista">
          {profesionales.map((profesional) => (
            <li key={profesional.id} className={`admin__item${profesional.activo ? '' : ' admin__item--oculto'}`}>
              {profesional.fotoUrl
                ? <img className="opcion__foto" src={profesional.fotoUrl} alt="" />
                : <span className="opcion__foto opcion__foto--inicial" aria-hidden="true">{profesional.nombre.charAt(0).toUpperCase()}</span>}
              <div className="admin__datos">
                <span className="admin__nombre">{profesional.nombre}</span>
                <span className="admin__detalle">
                  {profesional.servicioIds.length === 0 ? 'Sin servicios' : profesional.servicioIds.map(nombreDe).join(', ')}
                </span>
                <span className="admin__detalle">
                  {profesional.usuarioEmail ? `Entra al panel con ${profesional.usuarioEmail}` : 'Sin usuario del panel'}
                  {profesional.turnosFuturos > 0 && ` · ${profesional.turnosFuturos} turnos por delante`}
                </span>
              </div>
              {!profesional.activo && <span className="admin__etiqueta">Dado de baja</span>}
              <div className="admin__acciones">
                {profesional.activo ? (
                  <>
                    <button type="button" className="btn btn-secondary"
                      onClick={() => setAccion({ tipo: 'editar', profesional })}>Editar</button>
                    <button type="button" className="btn btn-secondary"
                      onClick={() => setAccion({ tipo: 'horario', profesional })}>Horario</button>
                    {profesional.usuarioEmail === null && (
                      <button type="button" className="btn btn-secondary"
                        onClick={() => setAccion({ tipo: 'usuario', profesional })}>Crear usuario</button>
                    )}
                    <button type="button" className="btn btn-secondary" onClick={() => pedirBaja(profesional)}>Dar de baja</button>
                  </>
                ) : (
                  <button type="button" className="btn btn-secondary" onClick={() => volverAlAlta(profesional)}>Dar de alta</button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

// Qué hacer con el resultado de guardar, igual en los tres formularios.
function useManejarResultado() {
  const { sesionPerdida } = useSesion()
  return (resultado: ResultadoGuardar<unknown>, alGuardar: () => void, setErrores: (e: Record<string, string>) => void,
    setAviso: (a: string | null) => void) => {
    if (resultado.estado === 'ok') {
      alGuardar()
    } else if (resultado.estado === 'validacion') {
      setErrores(resultado.errores)
      setAviso(Object.keys(resultado.errores).length === 0 ? resultado.mensaje : null)
    } else if (resultado.estado === 'sin-sesion') {
      sesionPerdida()
    } else {
      setAviso(resultado.estado === 'error' && resultado.mensaje ? resultado.mensaje : 'No pudimos guardar. Probá de nuevo.')
    }
  }
}

type FormularioProfesionalProps = {
  profesional: ProfesionalAdmin | null
  servicios: ServicioAdmin[]
  alGuardar: () => void
  alCancelar: () => void
}

// HU-25 CA1: nombre, foto (opcional) y qué servicios hace. Con esos servicios aparece en el paso 2 de la reserva.
function FormularioProfesional({ profesional, servicios, alGuardar, alCancelar }: FormularioProfesionalProps) {
  const manejar = useManejarResultado()
  const [nombre, setNombre] = useState(profesional?.nombre ?? '')
  const [fotoUrl, setFotoUrl] = useState<string | null>(profesional?.fotoUrl ?? null)
  const [elegidos, setElegidos] = useState<string[]>(profesional?.servicioIds ?? [])
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [aviso, setAviso] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function elegirFoto(evento: ChangeEvent<HTMLInputElement>) {
    const archivo = evento.target.files?.[0]
    if (!archivo) {
      return
    }
    setEnviando(true)
    const resultado = await subirImagen(archivo)
    setEnviando(false)
    if (resultado.estado === 'ok') {
      setFotoUrl(resultado.datos.url)
      setErrores((e) => ({ ...e, fotoUrl: '' }))
    } else {
      setErrores((e) => ({ ...e, fotoUrl: resultado.estado === 'validacion' ? resultado.mensaje : 'No pudimos subir la foto.' }))
    }
  }

  function alternar(id: string) {
    setElegidos((actuales) => (actuales.includes(id) ? actuales.filter((s) => s !== id) : [...actuales, id]))
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    const pedido = { nombre, fotoUrl, servicioIds: elegidos }
    const resultado = profesional ? await editarProfesional(profesional.id, pedido) : await crearProfesional(pedido)
    setEnviando(false)
    manejar(resultado, alGuardar, setErrores, setAviso)
  }

  const titulo = profesional ? `Editar ${profesional.nombre}` : 'Nuevo profesional'
  return (
    <form className="formulario admin__formulario" onSubmit={guardar} noValidate aria-label={titulo}>
      <h2 className="mis-turnos__titulo">{titulo}</h2>
      <div className="campo">
        <label className="campo__etiqueta" htmlFor="profesional-nombre">Nombre</label>
        <input id="profesional-nombre" type="text" value={nombre} onChange={(e) => setNombre(e.target.value)}
          aria-invalid={Boolean(errores.nombre)} />
        {errores.nombre && <p className="campo__error">{errores.nombre}</p>}
      </div>
      <div className="campo">
        <label className="campo__etiqueta" htmlFor="profesional-foto">Foto</label>
        {fotoUrl && <img className="opcion__foto" src={fotoUrl} alt="Foto elegida" />}
        <input id="profesional-foto" type="file" accept="image/png,image/jpeg,image/webp" onChange={elegirFoto} />
        <p className="campo__ayuda">Opcional. PNG, JPG o WebP de hasta 500 KB.</p>
        {fotoUrl && <button type="button" className="link" onClick={() => setFotoUrl(null)}>Quitar foto</button>}
        {errores.fotoUrl && <p className="campo__error" role="alert">{errores.fotoUrl}</p>}
      </div>
      <fieldset className="campo">
        <legend className="campo__etiqueta">Servicios que hace</legend>
        {servicios.map((servicio) => (
          <label key={servicio.id} className="admin__opcion">
            <input type="checkbox" checked={elegidos.includes(servicio.id)} onChange={() => alternar(servicio.id)} />
            {servicio.nombre}{!servicio.activo && ' (oculto)'}
          </label>
        ))}
        {errores.servicioIds && <p className="campo__error">{errores.servicioIds}</p>}
      </fieldset>
      {aviso && <p className="campo__error" role="alert">{aviso}</p>}
      <div className="turno__acciones">
        <button type="submit" className="btn btn-primary" disabled={enviando}>{enviando ? 'Guardando…' : 'Guardar'}</button>
        <button type="button" className="btn btn-secondary" onClick={alCancelar} disabled={enviando}>Cancelar</button>
      </div>
    </form>
  )
}

type FormularioUsuarioProps = { profesional: ProfesionalAdmin; alGuardar: () => void; alCancelar: () => void }

// HU-25 CA2 y CA4: el usuario del panel del profesional, con una contraseña inicial que el admin le pasa.
function FormularioUsuario({ profesional, alGuardar, alCancelar }: FormularioUsuarioProps) {
  const manejar = useManejarResultado()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [aviso, setAviso] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    const resultado = await crearUsuarioProfesional(profesional.id, email, password)
    setEnviando(false)
    manejar(resultado, alGuardar, setErrores, setAviso)
  }

  const titulo = `Usuario de ${profesional.nombre}`
  return (
    <form className="formulario admin__formulario" onSubmit={guardar} noValidate aria-label={titulo}>
      <h2 className="mis-turnos__titulo">{titulo}</h2>
      <p className="panel__texto">Con este mail y contraseña va a entrar al panel y ver solo su agenda.</p>
      <div className="campo">
        <label className="campo__etiqueta" htmlFor="usuario-email">Mail</label>
        <input id="usuario-email" type="email" autoComplete="off" value={email} onChange={(e) => setEmail(e.target.value)}
          aria-invalid={Boolean(errores.email)} />
        {errores.email && <p className="campo__error">{errores.email}</p>}
      </div>
      <div className="campo">
        <label className="campo__etiqueta" htmlFor="usuario-password">Contraseña inicial</label>
        <input id="usuario-password" type="password" autoComplete="new-password" value={password}
          onChange={(e) => setPassword(e.target.value)} aria-invalid={Boolean(errores.password)} />
        {errores.password ? <p className="campo__error">{errores.password}</p>
          : <p className="campo__ayuda">Al menos 8 caracteres.</p>}
      </div>
      {aviso && <p className="campo__error" role="alert">{aviso}</p>}
      <div className="turno__acciones">
        <button type="submit" className="btn btn-primary" disabled={enviando}>{enviando ? 'Creando…' : 'Crear usuario'}</button>
        <button type="button" className="btn btn-secondary" onClick={alCancelar} disabled={enviando}>Cancelar</button>
      </div>
    </form>
  )
}

type ConfirmarBajaProps = {
  profesional: ProfesionalAdmin
  turnos: TurnoFuturo[]
  alTerminar: () => void
  alCancelar: () => void
}

// HU-25 CA3: con turnos por delante, los muestra y pide confirmar. Al confirmar deja de recibir reservas; sus turnos
// siguen y se mueven o cancelan desde la agenda (donde sigue apareciendo mientras tenga turnos).
function ConfirmarBaja({ profesional, turnos, alTerminar, alCancelar }: ConfirmarBajaProps) {
  const { sesionPerdida } = useSesion()
  const [enviando, setEnviando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)

  async function confirmar() {
    setEnviando(true)
    const resultado = await darDeBaja(profesional.id, true)
    setEnviando(false)
    if (resultado.estado === 'ok') {
      alTerminar()
    } else if (resultado.estado === 'sin-sesion') {
      sesionPerdida()
    } else {
      setAviso('No pudimos darlo de baja. Probá de nuevo.')
    }
  }

  return (
    <div className="turno__aviso admin__formulario" role="group" aria-label={`Dar de baja a ${profesional.nombre}`}>
      {turnos.length === 0 ? (
        <p>¿Dar de baja a {profesional.nombre}? Deja de aparecer en la reserva y no puede entrar al panel.</p>
      ) : (
        <>
          <p>
            {profesional.nombre} tiene {turnos.length} {turnos.length === 1 ? 'turno' : 'turnos'} por delante. Si lo das de
            baja, esos turnos siguen: movelos o cancelalos desde la agenda.
          </p>
          <ul>
            {turnos.map((turno) => (
              <li key={turno.id}>
                {fechaLarga(turno.inicio.slice(0, 10))}, {turno.inicio.slice(11, 16)}
                {turno.clienteNombre && ` · ${turno.clienteNombre}`}
              </li>
            ))}
          </ul>
        </>
      )}
      {aviso && <p className="campo__error" role="alert">{aviso}</p>}
      <div className="turno__acciones">
        <button type="button" className="btn btn-primary" onClick={confirmar} disabled={enviando}>
          {enviando ? 'Dando de baja…' : turnos.length === 0 ? 'Sí, dar de baja' : 'Dar de baja igual'}
        </button>
        <button type="button" className="btn btn-secondary" onClick={alCancelar} disabled={enviando}>Cancelar</button>
      </div>
    </div>
  )
}

export default PaginaProfesionales
