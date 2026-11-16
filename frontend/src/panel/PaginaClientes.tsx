import { useEffect, useState, type FormEvent } from 'react'

import { borrarDatosCliente, listarClientes, verCliente } from '../api/admin'
import type { ClienteAdmin, ClienteConHistorial } from '../api/tipos'
import { fechaLarga } from '../fechas'
import { formatearPrecio } from '../formato'
import { textoDelEstado } from './agenda'
import { useSesion } from './sesion'

// Los clientes del local (HU-31): quién viene seguido y quién falta, con su historial. Y borrar los datos de uno que
// lo pide (HU-34).
function PaginaClientes() {
  const { sesionPerdida } = useSesion()
  const [buscar, setBuscar] = useState('')
  // La búsqueda que se pidió (al tocar "Buscar"); cambiarla vuelve a pedir la lista.
  const [busqueda, setBusqueda] = useState('')
  const [intento, setIntento] = useState(0)
  const [clientes, setClientes] = useState<ClienteAdmin[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [abierto, setAbierto] = useState<string | null>(null)

  useEffect(() => {
    let vigente = true
    listarClientes(busqueda).then((resultado) => {
      if (!vigente) {
        return
      }
      if (resultado.estado === 'ok') {
        setClientes(resultado.datos)
        setError(null)
      } else if (resultado.estado === 'sin-sesion') {
        sesionPerdida()
      } else {
        setError('No pudimos cargar los clientes.')
      }
    })
    return () => {
      vigente = false
    }
  }, [busqueda, intento, sesionPerdida])

  function buscarClientes(evento: FormEvent) {
    evento.preventDefault()
    setBusqueda(buscar)
  }

  return (
    <section>
      <h1 className="panel__titulo">Clientes</h1>

      <form className="clientes__buscar" onSubmit={buscarClientes} role="search">
        <label className="solo-lectores" htmlFor="clientes-buscar">Buscar por nombre o teléfono</label>
        <input id="clientes-buscar" type="search" placeholder="Nombre o teléfono" value={buscar}
          onChange={(e) => setBuscar(e.target.value)} />
        <button type="submit" className="btn btn-secondary">Buscar</button>
      </form>

      {error && <p className="reserva__aviso" role="alert">{error}</p>}
      {clientes === null && !error && <p className="panel__texto" role="status">Cargando clientes…</p>}
      {clientes !== null && clientes.length === 0 && (
        <p className="panel__texto">{busqueda ? 'No hay clientes con esa búsqueda.' : 'Todavía no hay clientes.'}</p>
      )}
      {clientes !== null && clientes.length > 0 && (
        <ul className="admin__lista">
          {clientes.map((cliente) => (
            <li key={cliente.id} className="admin__item">
              <div className="admin__datos">
                <span className="admin__nombre">{cliente.nombre}</span>
                <span className="admin__detalle">
                  {cliente.telefono ?? 'Sin teléfono'} · {cliente.visitas} {cliente.visitas === 1 ? 'visita' : 'visitas'}
                  {' '}· {cliente.faltas} {cliente.faltas === 1 ? 'falta' : 'faltas'}
                  {cliente.ultimaVisita && ` · Última visita: ${fechaLarga(cliente.ultimaVisita)}`}
                </span>
              </div>
              <button type="button" className="btn btn-secondary"
                onClick={() => setAbierto(abierto === cliente.id ? null : cliente.id)}
                aria-expanded={abierto === cliente.id}>
                {abierto === cliente.id ? 'Cerrar' : 'Ver historial'}
              </button>
              {abierto === cliente.id && (
                <DetalleCliente id={cliente.id} alBorrar={() => setIntento((n) => n + 1)} />
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

// HU-31 CA2: el historial de turnos del cliente en el local; y HU-34: borrar sus datos.
function DetalleCliente({ id, alBorrar }: { id: string; alBorrar: () => void }) {
  const { sesionPerdida } = useSesion()
  const [detalle, setDetalle] = useState<ClienteConHistorial | null>(null)
  const [intento, setIntento] = useState(0)
  const [confirmando, setConfirmando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)

  useEffect(() => {
    let vigente = true
    verCliente(id).then((resultado) => {
      if (!vigente) {
        return
      }
      if (resultado.estado === 'ok') {
        setDetalle(resultado.datos)
      } else if (resultado.estado === 'sin-sesion') {
        sesionPerdida()
      } else {
        setAviso('No pudimos cargar el historial.')
      }
    })
    return () => {
      vigente = false
    }
  }, [id, intento, sesionPerdida])

  async function borrar() {
    const resultado = await borrarDatosCliente(id)
    setConfirmando(false)
    if (resultado.estado === 'ok') {
      setAviso('Datos borrados. Sus turnos quedan en el historial, sin datos personales.')
      setIntento((n) => n + 1)
      alBorrar()
    } else if (resultado.estado === 'sin-sesion') {
      sesionPerdida()
    } else {
      // CA2: con turnos por delante, primero hay que cancelarlos.
      setAviso(resultado.estado === 'error' && resultado.mensaje ? resultado.mensaje : 'No pudimos borrar los datos.')
    }
  }

  return (
    <div className="clientes__detalle" role="region" aria-label="Historial del cliente">
      {aviso && <p className="reserva__aviso" role="status">{aviso}</p>}
      {detalle === null && !aviso && <p className="panel__texto" role="status">Cargando historial…</p>}
      {detalle !== null && (
        <>
          {detalle.cliente.email && <p className="admin__detalle">Mail: {detalle.cliente.email}</p>}
          {detalle.turnos.length === 0 ? (
            <p className="panel__texto">Todavía no tiene turnos.</p>
          ) : (
            <ul className="clientes__historial">
              {detalle.turnos.map((turno) => (
                <li key={turno.id}>
                  {fechaLarga(turno.inicio.slice(0, 10))}, {turno.inicio.slice(11, 16)} · {turno.servicios.join(' + ')}
                  {' '}· con {turno.profesionalNombre} · {formatearPrecio(turno.precioTotal)} · {textoDelEstado(turno.estado)}
                </li>
              ))}
            </ul>
          )}
          {/* HU-34: no se puede deshacer, así que pide confirmar. A un cliente ya borrado no hay nada más que borrarle. */}
          {detalle.cliente.datosBorrados ? null : confirmando ? (
            <div className="turno__aviso">
              <p>¿Borrar los datos de {detalle.cliente.nombre}? Nombre, teléfono y mail se borran y no se pueden recuperar.</p>
              <div className="turno__acciones">
                <button type="button" className="btn btn-primary" onClick={borrar}>Sí, borrar sus datos</button>
                <button type="button" className="btn btn-secondary" onClick={() => setConfirmando(false)}>No</button>
              </div>
            </div>
          ) : (
            <button type="button" className="link" onClick={() => setConfirmando(true)}>Borrar los datos de este cliente</button>
          )}
        </>
      )}
    </div>
  )
}

export default PaginaClientes
