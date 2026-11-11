import type { LocalPublico } from './tipos'

// Lo que puede pasar al pedir la home. Las pantallas eligen qué mostrar según el estado.
export type ResultadoLocal =
  | { estado: 'ok'; local: LocalPublico }
  | { estado: 'no-existe' }
  | { estado: 'error' }

// Pide los datos de la home. No se le pasa qué local: el navegador manda el Host
// (demo.localhost:5173) y el back resuelve el local con él.
export async function obtenerLocal(): Promise<ResultadoLocal> {
  try {
    const respuesta = await fetch('/api/publico/local')
    if (respuesta.ok) {
      const local: LocalPublico = await respuesta.json()
      return { estado: 'ok', local }
    }
    // El front decide por el código del error, nunca por el texto (doc 00).
    const problema: { codigo?: string } | null = await respuesta.json().catch(() => null)
    if (problema?.codigo === 'LOCAL_NO_ENCONTRADO') {
      return { estado: 'no-existe' }
    }
    return { estado: 'error' }
  } catch {
    // Sin conexión, o la API caída y el proxy de Vite sin respuesta.
    return { estado: 'error' }
  }
}
