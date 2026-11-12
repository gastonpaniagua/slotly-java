import { useState } from 'react'

import type { DatosTransferencia as Datos } from '../api/tipos'

type Props = {
  datos: Datos
}

// Alias, CBU y titular del local con botón de copiar (HU-10 CA5). Se usa en el paso 5 y en la confirmación.
function DatosTransferencia({ datos }: Props) {
  const [copiado, setCopiado] = useState<string | null>(null)

  const filas = [
    { clave: 'alias', nombre: 'Alias', valor: datos.alias },
    { clave: 'cbu', nombre: 'CBU', valor: datos.cbu },
    { clave: 'titular', nombre: 'Titular', valor: datos.titular },
  ].filter((fila): fila is { clave: string; nombre: string; valor: string } => Boolean(fila.valor))

  if (filas.length === 0) {
    return <p className="reserva__ayuda">El local te va a pasar los datos para transferir.</p>
  }

  function copiar(clave: string, valor: string) {
    navigator.clipboard?.writeText(valor).then(() => setCopiado(clave)).catch(() => setCopiado(null))
  }

  return (
    <dl className="transferencia">
      {filas.map((fila) => (
        <div key={fila.clave} className="transferencia__fila">
          <dt className="transferencia__nombre">{fila.nombre}</dt>
          <dd className="transferencia__valor">
            <span className="transferencia__texto">{fila.valor}</span>
            <button
              type="button"
              className="btn btn-secondary transferencia__copiar"
              aria-label={`Copiar ${fila.nombre}`}
              onClick={() => copiar(fila.clave, fila.valor)}
            >
              {copiado === fila.clave ? 'Copiado' : 'Copiar'}
            </button>
          </dd>
        </div>
      ))}
    </dl>
  )
}

export default DatosTransferencia
