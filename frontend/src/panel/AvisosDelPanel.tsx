import { fechaLarga } from '../fechas'
import { textoDelAviso, type Avisos } from './avisos'

// El botón con el contador va en el encabezado del panel; la lista se abre debajo (HU-37).

export function BotonAvisos({ avisos }: { avisos: Avisos }) {
  const sinLeer = avisos.bandeja?.sinLeer ?? 0
  return (
    <button type="button" className="btn btn-secondary avisos__boton" onClick={avisos.alternar}
      aria-expanded={avisos.abierto} aria-controls="lista-avisos"
      aria-label={sinLeer > 0 ? `Avisos, ${sinLeer} sin leer` : 'Avisos'}>
      Avisos
      {sinLeer > 0 && <span className="avisos__contador" aria-hidden="true">{sinLeer}</span>}
    </button>
  )
}

export function ListaAvisos({ avisos }: { avisos: Avisos }) {
  if (!avisos.abierto) {
    return null
  }
  const lista = avisos.bandeja?.notificaciones ?? []
  return (
    <section id="lista-avisos" className="avisos" aria-label="Avisos">
      {lista.length === 0 ? (
        <p className="panel__texto">Todavía no tenés avisos.</p>
      ) : (
        <ul className="avisos__lista">
          {lista.map((aviso) => (
            <li key={aviso.id} className={aviso.leida ? 'avisos__item' : 'avisos__item avisos__item--nuevo'}>
              {!aviso.leida && <span className="avisos__nuevo">Nuevo</span>}
              <p>{textoDelAviso(aviso)}</p>
              <p className="avisos__cuando">{fechaLarga(aviso.creadaEn.slice(0, 10))}, {aviso.creadaEn.slice(11, 16)}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
