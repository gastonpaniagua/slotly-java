import { Link } from 'react-router'

import type { TurnoPublico } from '../api/tipos'
import { fechaLarga } from '../fechas'
import { formatearDuracion, formatearPrecio } from '../formato'
import BarraProgreso from './BarraProgreso'
import DatosTransferencia from './DatosTransferencia'

type Props = {
  turno: TurnoPublico
}

// Paso 6: el turno quedó reservado (HU-10 CA1). En el paso 35 suma "Mi turno" y que sobreviva a recargar.
function PasoConfirmacion({ turno }: Props) {
  return (
    <main className="reserva">
      <BarraProgreso paso={6} />
      <h1 className="reserva__titulo">¡Listo! Tu turno quedó reservado</h1>

      <section className="resumen" aria-label="Tu turno">
        <p className="resumen__cuando">{fechaLarga(turno.fecha)}, {turno.hora}</p>
        <p>Con {turno.profesionalNombre}</p>
        <ul className="resumen__servicios">
          {turno.servicios.map((s) => (
            <li key={s.nombre}>{s.nombre} · {formatearDuracion(s.duracionMin)} · {formatearPrecio(s.precio)}</li>
          ))}
        </ul>
        <p className="resumen__total price">
          Total: {formatearDuracion(turno.duracionMin)} · {formatearPrecio(turno.precioTotal)}
        </p>
        <p>Pago: {turno.formaPago === 'transferencia' ? 'Transferencia' : 'Efectivo, en el local'}</p>
      </section>

      {turno.transferencia && <DatosTransferencia datos={turno.transferencia} />}

      <Link className="btn btn-secondary" to="/">Volver al inicio</Link>
    </main>
  )
}

export default PasoConfirmacion
