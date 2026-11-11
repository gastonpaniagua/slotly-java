import { useCallback, useEffect, useState } from 'react'

import { obtenerLocal, type ResultadoLocal } from './api/publico'
import { esDominioRaiz } from './dominio'
import Home from './home/Home'
import HomeCargando from './home/HomeCargando'
import PaginaSlotly from './PaginaSlotly'
import PantallaMensaje from './PantallaMensaje'

type Props = {
  // El host se recibe para poder probar App con cualquier dirección; en el navegador es el de la barra.
  host?: string
}

// Elige qué mostrar: la página de Slotly en el dominio raíz, o la home del local del subdominio.
function App({ host = window.location.hostname }: Props) {
  if (esDominioRaiz(host)) {
    return <PaginaSlotly />
  }
  return <HomeDelLocal />
}

// Pide la home del local y muestra la pantalla que corresponde a la respuesta.
function HomeDelLocal() {
  const [resultado, setResultado] = useState<ResultadoLocal | null>(null)
  // Cada vez que cambia, el efecto vuelve a pedir el local (sirve para "Probar de nuevo").
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    // Si el componente se desmonta o hay un intento nuevo, se ignora la respuesta vieja.
    let vigente = true
    obtenerLocal().then((r) => {
      if (vigente) {
        setResultado(r)
      }
    })
    return () => {
      vigente = false
    }
  }, [intento])

  const reintentar = useCallback(() => {
    setResultado(null)
    setIntento((n) => n + 1)
  }, [])

  if (resultado === null) {
    return <HomeCargando />
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
      // HU-01 CA4: la API no respondió o falló. Se puede reintentar sin recargar la página.
      return (
        <PantallaMensaje
          titulo="No pudimos cargar la página"
          texto="Probá de nuevo en un rato."
          accion={{ texto: 'Probar de nuevo', alHacerClic: reintentar }}
        />
      )
  }
}

export default App
