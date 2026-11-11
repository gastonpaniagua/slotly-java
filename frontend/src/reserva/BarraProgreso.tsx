// Los 6 pasos de la reserva (doc 04). Los primeros dos llegan en la Etapa 2; el resto, en las Etapas 3 y 4.
const PASOS_RESERVA =['Servicios', 'Profesional', 'Día', 'Hora', 'Tus datos', 'Confirmar']

type Props = {
  paso: number
}

// Barra que indica en qué paso de la reserva está el cliente (HU-05 CA4).
function BarraProgreso({ paso }: Props) {
  const total = PASOS_RESERVA.length
  return (
    <div className="progreso">
      <p className="progreso__texto">
        Paso {paso} de {total} · <span className="progreso__nombre">{PASOS_RESERVA[paso - 1]}</span>
      </p>
      <div
        className="progreso__barra"
        role="progressbar"
        aria-label="Progreso de la reserva"
        aria-valuemin={1}
        aria-valuemax={total}
        aria-valuenow={paso}
        aria-valuetext={`Paso ${paso} de ${total}`}
      >
        <div className="progreso__relleno" style={{ width: `${(paso / total) * 100}%` }} />
      </div>
    </div>
  )
}

export default BarraProgreso
