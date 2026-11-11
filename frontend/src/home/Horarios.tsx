import type { DiaPublico } from '../api/tipos'

type Props = {
  horarios: DiaPublico[]
}

// diaSemana viene como en la base: 0 domingo … 6 sábado.
const NOMBRES = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']

// En Argentina la semana se lee de lunes a domingo.
const ORDEN = [1, 2, 3, 4, 5, 6, 0]

// Horario de atención de la semana (HU-02 CA1). Las horas se muestran tal como las manda el back,
// que ya están en hora del local: no se convierten con la zona del navegador (HU-02 CA2).
function Horarios({ horarios }: Props) {
  const porDia = new Map(horarios.map((dia) => [dia.diaSemana, dia.franjas]))

  return (
    <section className="seccion" aria-labelledby="titulo-horarios">
      <h2 id="titulo-horarios" className="seccion__titulo">Horarios de atención</h2>
      <dl className="horarios">
        {ORDEN.map((dia) => {
          const franjas = porDia.get(dia) ?? []
          return (
            <div key={dia} className="horarios__dia">
              <dt className="horarios__nombre">{NOMBRES[dia]}</dt>
              <dd className={franjas.length === 0 ? 'horarios__franjas horarios__franjas--cerrado' : 'horarios__franjas'}>
                {franjas.length === 0
                  ? 'Cerrado'
                  : franjas.map((franja) => `${franja.desde} a ${franja.hasta}`).join(' y ')}
              </dd>
            </div>
          )
        })}
      </dl>
    </section>
  )
}

export default Horarios
