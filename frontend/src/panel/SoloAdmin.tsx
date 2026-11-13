import type { ReactNode } from 'react'

import { useSesion } from './sesion'

// Las secciones de gestión del local son solo para el admin (doc 00). La API igual responde 403 a un profesional
// (HU-24 CA5); esto es para que no vea una pantalla que no le sirve si entra a la dirección a mano.
function SoloAdmin({ children }: { children: ReactNode }) {
  const { usuario } = useSesion()
  if (usuario.rol !== 'admin') {
    return (
      <section>
        <h1 className="panel__titulo">No tenés permiso para ver esto</h1>
        <p className="panel__texto">Esta sección es solo para el dueño del local.</p>
      </section>
    )
  }
  return children
}

export default SoloAdmin
