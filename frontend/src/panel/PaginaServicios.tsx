import { useEffect, useState, type FormEvent } from 'react'

import { crearServicio, editarServicio, listarServicios, ordenarServicios } from '../api/admin'
import type { ResultadoGuardar, ResultadoPanel } from '../api/panel'
import type { ServicioAdmin, ServicioPedido } from '../api/tipos'
import { formatearDuracion, formatearPrecio } from '../formato'
import { useSesion } from './sesion'

// Lo que se edita en el formulario: como texto, tal cual lo escribe el admin; se convierte al guardar.
type Formulario = { nombre: string; descripcion: string; duracionMin: string; precio: string; limpiezaMin: string }

const VACIO: Formulario = { nombre: '', descripcion: '', duracionMin: '30', precio: '', limpiezaMin: '' }

function formularioDe(servicio: ServicioAdmin): Formulario {
  return {
    nombre: servicio.nombre,
    descripcion: servicio.descripcion ?? '',
    duracionMin: String(servicio.duracionMin),
    precio: String(servicio.precio),
    limpiezaMin: servicio.limpiezaMin === null ? '' : String(servicio.limpiezaMin),
  }
}

// Un número escrito, o null si quedó vacío o no es un número (la API lo marca como error del campo).
function numero(texto: string): number | null {
  return texto.trim() === '' || Number.isNaN(Number(texto)) ? null : Number(texto)
}

// Servicios del local (HU-24): crear, editar, ordenar y ocultar. Lo que se cambia acá se ve en la home y en la
// reserva; los turnos ya reservados mantienen su duración y precio (CA2).
function PaginaServicios() {
  const { sesionPerdida } = useSesion()
  const [servicios, setServicios] = useState<ServicioAdmin[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [intento, setIntento] = useState(0)
  // null: sin formulario abierto; 'nuevo': alta; un servicio: edición.
  const [editando, setEditando] = useState<ServicioAdmin | 'nuevo' | null>(null)

  useEffect(() => {
    let vigente = true
    listarServicios().then((resultado: ResultadoPanel<ServicioAdmin[]>) => {
      if (!vigente) {
        return
      }
      if (resultado.estado === 'ok') {
        setServicios(resultado.datos)
      } else if (resultado.estado === 'sin-sesion') {
        sesionPerdida()
      } else {
        setError('No pudimos cargar los servicios.')
      }
    })
    return () => {
      vigente = false
    }
  }, [intento, sesionPerdida])

  // Cualquier cambio vuelve a pedir la lista: así se ve el orden y los datos como quedaron en la base.
  function recargar() {
    setIntento((n) => n + 1)
  }

  function manejarError(resultado: ResultadoGuardar<unknown>) {
    if (resultado.estado === 'sin-sesion') {
      sesionPerdida()
    } else if (resultado.estado !== 'ok') {
      setError('No pudimos guardar el cambio. Probá de nuevo.')
    }
  }

  async function mover(indice: number, paso: -1 | 1) {
    if (servicios === null) {
      return
    }
    // El mismo orden, con este servicio cambiado de lugar con el de arriba (o el de abajo).
    const ids = servicios.map((s) => s.id)
    const otro = indice + paso
    const ordenNuevo = ids.map((id, i) => (i === indice ? ids[otro] : i === otro ? ids[indice] : id))
    const resultado = await ordenarServicios(ordenNuevo)
    if (resultado.estado === 'ok') {
      setServicios(resultado.datos)
    } else {
      manejarError(resultado)
    }
  }

  // HU-24 CA3: ocultar o mostrar es editar el servicio con activo cambiado.
  async function alternarVisible(servicio: ServicioAdmin) {
    const resultado = await editarServicio(servicio.id, {
      nombre: servicio.nombre,
      descripcion: servicio.descripcion,
      duracionMin: servicio.duracionMin,
      precio: servicio.precio,
      limpiezaMin: servicio.limpiezaMin,
      activo: !servicio.activo,
    })
    if (resultado.estado === 'ok') {
      recargar()
    } else {
      manejarError(resultado)
    }
  }

  return (
    <section>
      <div className="admin__barra">
        <h1 className="panel__titulo">Servicios</h1>
        {editando === null && (
          <button type="button" className="btn btn-primary" onClick={() => setEditando('nuevo')}>Nuevo servicio</button>
        )}
      </div>

      {error && <p className="reserva__aviso" role="alert">{error}</p>}

      {editando !== null && (
        <FormularioServicio
          servicio={editando === 'nuevo' ? null : editando}
          alGuardar={() => { setEditando(null); recargar() }}
          alCancelar={() => setEditando(null)}
        />
      )}

      {servicios === null && !error && <p className="panel__texto" role="status">Cargando servicios…</p>}
      {servicios !== null && servicios.length === 0 && (
        <p className="panel__texto">Todavía no hay servicios. Creá el primero.</p>
      )}
      {servicios !== null && servicios.length > 0 && (
        <ul className="admin__lista">
          {servicios.map((servicio, indice) => (
            <li key={servicio.id} className={`admin__item${servicio.activo ? '' : ' admin__item--oculto'}`}>
              <div className="admin__datos">
                <span className="admin__nombre">{servicio.nombre}</span>
                <span className="admin__detalle">
                  {formatearDuracion(servicio.duracionMin)} · {formatearPrecio(servicio.precio)}
                  {servicio.limpiezaMin !== null && ` · ${servicio.limpiezaMin} min de limpieza`}
                </span>
              </div>
              {!servicio.activo && <span className="admin__etiqueta">Oculto</span>}
              <div className="admin__acciones">
                <button type="button" className="btn btn-secondary admin__flecha" aria-label={`Subir ${servicio.nombre}`}
                  disabled={indice === 0} onClick={() => mover(indice, -1)}>↑</button>
                <button type="button" className="btn btn-secondary admin__flecha" aria-label={`Bajar ${servicio.nombre}`}
                  disabled={indice === servicios.length - 1} onClick={() => mover(indice, 1)}>↓</button>
                <button type="button" className="btn btn-secondary" onClick={() => setEditando(servicio)}>Editar</button>
                <button type="button" className="btn btn-secondary" onClick={() => alternarVisible(servicio)}>
                  {servicio.activo ? 'Ocultar' : 'Mostrar'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

type FormularioProps = {
  servicio: ServicioAdmin | null
  alGuardar: () => void
  alCancelar: () => void
}

// HU-24 CA1 y CA4: alta y edición, con el error de cada campo junto a él (las reglas las valida la API).
function FormularioServicio({ servicio, alGuardar, alCancelar }: FormularioProps) {
  const { sesionPerdida } = useSesion()
  const [datos, setDatos] = useState<Formulario>(() => (servicio ? formularioDe(servicio) : VACIO))
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [aviso, setAviso] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  function cambiar(campo: keyof Formulario, valor: string) {
    setDatos((anteriores) => ({ ...anteriores, [campo]: valor }))
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault()
    const pedido = {
      nombre: datos.nombre,
      descripcion: datos.descripcion.trim() === '' ? null : datos.descripcion,
      duracionMin: numero(datos.duracionMin),
      precio: numero(datos.precio),
      limpiezaMin: numero(datos.limpiezaMin),
      activo: servicio?.activo ?? true,
    } as ServicioPedido
    setEnviando(true)
    setAviso(null)
    const resultado = servicio ? await editarServicio(servicio.id, pedido) : await crearServicio(pedido)
    setEnviando(false)
    if (resultado.estado === 'ok') {
      alGuardar()
    } else if (resultado.estado === 'validacion') {
      setErrores(resultado.errores)
    } else if (resultado.estado === 'sin-sesion') {
      sesionPerdida()
    } else {
      setAviso('No pudimos guardar el servicio. Probá de nuevo.')
    }
  }

  function campo(id: keyof Formulario, etiqueta: string, entrada: 'text' | 'number' | 'textarea', ayuda?: string) {
    const props = {
      id: `servicio-${id}`,
      value: datos[id],
      'aria-invalid': errores[id] !== undefined,
      'aria-describedby': errores[id] ? `error-servicio-${id}` : undefined,
    }
    return (
      <div className="campo">
        <label className="campo__etiqueta" htmlFor={props.id}>{etiqueta}</label>
        {entrada === 'textarea'
          ? <textarea {...props} onChange={(e) => cambiar(id, e.target.value)} />
          : <input {...props} type={entrada} onChange={(e) => cambiar(id, e.target.value)} />}
        {ayuda && !errores[id] && <p className="campo__ayuda">{ayuda}</p>}
        {errores[id] && <p id={`error-servicio-${id}`} className="campo__error">{errores[id]}</p>}
      </div>
    )
  }

  return (
    <form className="formulario admin__formulario" onSubmit={guardar} noValidate
      aria-label={servicio ? `Editar ${servicio.nombre}` : 'Nuevo servicio'}>
      <h2 className="mis-turnos__titulo">{servicio ? `Editar ${servicio.nombre}` : 'Nuevo servicio'}</h2>
      {campo('nombre', 'Nombre', 'text')}
      {campo('descripcion', 'Descripción', 'textarea', 'Opcional. Se ve en la home del local.')}
      <div className="admin__fila">
        {campo('duracionMin', 'Duración (min)', 'number', 'Múltiplo de 5, de 5 a 480.')}
        {campo('precio', 'Precio ($)', 'number')}
        {campo('limpiezaMin', 'Limpieza (min)', 'number', 'Opcional. Si no, la del local.')}
      </div>
      {aviso && <p className="campo__error" role="alert">{aviso}</p>}
      <div className="turno__acciones">
        <button type="submit" className="btn btn-primary" disabled={enviando}>{enviando ? 'Guardando…' : 'Guardar'}</button>
        <button type="button" className="btn btn-secondary" onClick={alCancelar} disabled={enviando}>Cancelar</button>
      </div>
    </form>
  )
}

export default PaginaServicios
