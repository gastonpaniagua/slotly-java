import { useSesion } from './sesion'

// La agenda del panel. Por ahora solo el título: el día y la semana llegan en el paso 40 (HU-17 y HU-18).
function PaginaAgenda() {
  const { usuario } = useSesion()
  return (
    <section>
      <h1 className="panel__titulo">Agenda</h1>
      <p className="panel__texto">
        {usuario.rol === 'admin' ? 'Vas a ver la agenda de todos los profesionales.' : 'Vas a ver tu agenda.'}
      </p>
    </section>
  )
}

export default PaginaAgenda
