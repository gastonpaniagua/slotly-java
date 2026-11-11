import { useEffect, useState } from 'react'

import { obtenerLocal, type ResultadoLocal } from './api/publico'
import Home from './home/Home'
import PantallaMensaje from './PantallaMensaje'

// Pide la home del local del subdominio y elige qué pantalla mostrar según la respuesta.
function App() {
  const [resultado, setResultado] = useState<ResultadoLocal | null>(null)

  useEffect(() => {
    obtenerLocal().then(setResultado)
  }, [])

  if (resultado === null) {
    return <PantallaMensaje titulo="Cargando…" />
  }

  switch (resultado.estado) {
    case 'ok':
      return <Home local={resultado.local} />
    case 'no-existe':
      // HU-03 CA2: sin detalles técnicos.
      return (
        <PantallaMensaje
          titulo="Este local no existe en Slotly"
          texto="Revisá que la dirección esté bien escrita."
        />
      )
    case 'error':
      // HU-01 CA4: la API no respondió o falló.
      return (
        <PantallaMensaje
          titulo="No pudimos cargar la página"
          texto="Probá de nuevo en un rato."
        />
      )
  }
}

export default App
