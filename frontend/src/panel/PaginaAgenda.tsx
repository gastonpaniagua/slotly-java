import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'

import { obtenerAgenda, type ResultadoPanel } from '../api/panel'
import type { Agenda, ProfesionalDeAgenda } from '../api/tipos'
import { diasDeLaSemana, fechaLarga, lunesDe, sumarDias, textoDeLaSemana } from '../fechas'
import { franjasDelDia, itemsDelDia, textoDelEstado, textoDelHorario, TODO_EL_DIA, type ItemDeAgenda } from './agenda'
import { useSesion } from './sesion'

type Vista = 'dia' | 'semana'

// La agenda del panel (HU-17 y HU-18). Lo que se está mirando vive en la dirección
// (/agenda?vista=semana&dia=2026-11-17&profesional=…): recargar, o volver después del login (HU-16 CA6),
// muestra lo mismo.
function PaginaAgenda() {
  const { usuario, sesionPerdida } = useSesion()
  const [parametros, setParametros] = useSearchParams()
  const vista: Vista = parametros.get('vista') === 'semana' ? 'semana' : 'dia'
  const diaPedido = parametros.get('dia')
  const filtro = parametros.get('profesional')
  const [intento, setIntento] = useState(0)

  // Sin día en la dirección, la API devuelve la de hoy (el "hoy" lo decide el back con la zona del local).
  const desde = diaPedido === null ? null : vista === 'semana' ? lunesDe(diaPedido) : diaPedido
  const clave = `${vista}|${desde}|${intento}`
  const [respuesta, setRespuesta] = useState<{ clave: string; resultado: ResultadoPanel<Agenda> } | null>(null)

  useEffect(() => {
    let vigente = true
    obtenerAgenda(desde, vista === 'semana' ? 7 : 1).then((resultado) => {
      if (vigente) {
        setRespuesta({ clave, resultado })
      }
    })
    return () => {
      vigente = false
    }
  }, [desde, vista, clave])

  const resultado = respuesta?.clave === clave ? respuesta.resultado : null

  // HU-16 CA7: la sesión se cerró en otra pestaña (o venció) mientras se miraba la agenda.
  useEffect(() => {
    if (resultado?.estado === 'sin-sesion') {
      sesionPerdida()
    }
  }, [resultado, sesionPerdida])

  function cambiar(cambios: Record<string, string | null>, reemplazar = false) {
    const nuevos = new URLSearchParams(parametros)
    for (const [nombre, valor] of Object.entries(cambios)) {
      if (valor === null) {
        nuevos.delete(nombre)
      } else {
        nuevos.set(nombre, valor)
      }
    }
    setParametros(nuevos, { replace: reemplazar })
  }

  const agenda = resultado?.estado === 'ok' ? resultado.datos : null
  const dia = diaPedido ?? agenda?.hoy ?? null
  const paso = vista === 'semana' ? 7 : 1

  // La semana va de lunes a domingo. Si se pidió sin día (la API mandó 7 días desde hoy), ahora que se sabe qué
  // día es hoy se pide la semana desde su lunes.
  const hoyDeLaSemanaSinDia = vista === 'semana' && diaPedido === null && agenda !== null
    && agenda.desde !== lunesDe(agenda.hoy) ? agenda.hoy : null
  useEffect(() => {
    if (hoyDeLaSemanaSinDia !== null) {
      setParametros((actuales) => {
        const nuevos = new URLSearchParams(actuales)
        nuevos.set('dia', hoyDeLaSemanaSinDia)
        return nuevos
      }, { replace: true })
    }
  }, [hoyDeLaSemanaSinDia, setParametros])

  return (
    <section className="agenda" aria-busy={resultado === null}>
      <div className="agenda__barra">
        <h1 className="panel__titulo">Agenda</h1>
        <div className="agenda__vistas" role="group" aria-label="Vista">
          <button type="button" className="agenda__vista" aria-pressed={vista === 'dia'}
            onClick={() => cambiar({ vista: null })}>Día</button>
          <button type="button" className="agenda__vista" aria-pressed={vista === 'semana'}
            onClick={() => cambiar({ vista: 'semana', dia })}>Semana</button>
        </div>
      </div>

      <div className="agenda__navegacion">
        <button type="button" className="btn btn-secondary agenda__flecha" disabled={dia === null}
          aria-label={vista === 'semana' ? 'Semana anterior' : 'Día anterior'}
          onClick={() => dia && cambiar({ dia: sumarDias(dia, -paso) })}>‹</button>
        <h2 className="agenda__periodo">
          {dia === null ? '' : vista === 'semana' ? `Semana del ${textoDeLaSemana(lunesDe(dia))}` : fechaLarga(dia)}
        </h2>
        <button type="button" className="btn btn-secondary agenda__flecha" disabled={dia === null}
          aria-label={vista === 'semana' ? 'Semana siguiente' : 'Día siguiente'}
          onClick={() => dia && cambiar({ dia: sumarDias(dia, paso) })}>›</button>
        <button type="button" className="btn btn-secondary" onClick={() => cambiar({ dia: null })}>Hoy</button>
        {usuario.rol === 'admin' && agenda && (
          // HU-18 CA1: el admin ve a todos juntos o filtra por uno.
          <label className="agenda__filtro">
            <span>Profesional</span>
            <select value={filtro ?? ''} onChange={(e) => cambiar({ profesional: e.target.value || null })}>
              <option value="">Todos</option>
              {agenda.profesionales.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
            </select>
          </label>
        )}
      </div>

      {(resultado === null || hoyDeLaSemanaSinDia !== null) && (
        <p className="panel__texto" role="status">Cargando agenda…</p>
      )}
      {resultado?.estado === 'error' && (
        <div className="reserva__error" role="alert">
          <p>No pudimos cargar la agenda.</p>
          <button type="button" className="btn btn-secondary" onClick={() => setIntento((n) => n + 1)}>
            Probar de nuevo
          </button>
        </div>
      )}
      {agenda && hoyDeLaSemanaSinDia === null && (
        <ContenidoDeLaAgenda
          agenda={agenda}
          dias={vista === 'semana' ? diasDeLaSemana(agenda.desde) : [agenda.desde]}
          profesionales={agenda.profesionales.filter((p) => filtro === null || p.id === filtro)}
          conTituloPorDia={vista === 'semana'}
        />
      )}
    </section>
  )
}

type ContenidoProps = {
  agenda: Agenda
  dias: string[]
  profesionales: ProfesionalDeAgenda[]
  conTituloPorDia: boolean
}

function ContenidoDeLaAgenda({ agenda, dias, profesionales, conTituloPorDia }: ContenidoProps) {
  const conNombre = profesionales.length > 1
  return (
    <div className="agenda__dias">
      {dias.map((fecha) => (
        <section key={fecha} className="agenda__dia" aria-label={fechaLarga(fecha)}>
          {conTituloPorDia && <h3 className="agenda__fecha">{fechaLarga(fecha)}</h3>}
          {profesionales.map((profesional) => {
            const franjas = franjasDelDia(agenda.franjas, fecha, profesional.id)
            const items = itemsDelDia(fecha, profesional.id, agenda.franjas, agenda.turnos)
            const hayTurnos = items.some((i) => i.tipo !== 'pausa')
            return (
              <div key={profesional.id} className="agenda__profesional">
                {conNombre && <h4 className="agenda__nombre">{profesional.nombre}</h4>}
                <p className="agenda__horario">
                  {franjas.length === 0 ? 'No trabaja este día.' : `Trabaja de ${textoDelHorario(franjas)}.`}
                </p>
                {/* HU-17 CA4: un día de trabajo sin turnos lo dice; no es un error. */}
                {hayTurnos && (
                  <ul className="agenda__lista">{items.map((item) => <Item key={claveDe(item)} item={item} />)}</ul>
                )}
                {!hayTurnos && franjas.length > 0 && <p className="agenda__vacio">Sin turnos</p>}
              </div>
            )
          })}
        </section>
      ))}
    </div>
  )
}

function claveDe(item: ItemDeAgenda): string {
  return item.tipo === 'pausa' ? `pausa-${item.desde}` : item.turno.id
}

// HU-17 CA1 y CA5: un turno con su cliente, servicios y estado; los bloqueos y las pausas, marcados distinto.
function Item({ item }: { item: ItemDeAgenda }) {
  const horas = item.desde === TODO_EL_DIA.desde && item.hasta === TODO_EL_DIA.hasta
    ? 'Todo el día'
    : `${item.desde} a ${item.hasta}`
  if (item.tipo === 'pausa') {
    return (
      <li className="agenda__item agenda__item--pausa">
        <span className="agenda__horas">{horas}</span>
        <span className="agenda__titulo-item">Pausa</span>
      </li>
    )
  }
  const { turno } = item
  if (item.tipo === 'bloqueo') {
    return (
      <li className="agenda__item agenda__item--bloqueo">
        <span className="agenda__horas">{horas}</span>
        <span className="agenda__titulo-item">Bloqueado{turno.motivo ? `: ${turno.motivo}` : ''}</span>
      </li>
    )
  }
  return (
    <li className="agenda__item agenda__item--turno">
      <span className="agenda__horas">{horas}</span>
      <div className="agenda__detalle">
        <span className="agenda__titulo-item">{turno.clienteNombre ?? 'Sin nombre'}</span>
        <span className="agenda__servicios">{turno.servicios.join(' + ')}</span>
        {turno.clienteTelefono && <a className="agenda__telefono" href={`tel:${turno.clienteTelefono}`}>{turno.clienteTelefono}</a>}
      </div>
      <span className={`agenda__estado agenda__estado--${turno.estado}`}>{textoDelEstado(turno.estado)}</span>
    </li>
  )
}

export default PaginaAgenda
