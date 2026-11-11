import type { LocalPublico, ProfesionalPublico } from './tipos'

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

export type ResultadoProfesionales =
  | { estado: 'ok'; profesionales: ProfesionalPublico[] }
  | { estado: 'error' }

// Los profesionales que hacen todos los servicios elegidos (paso 2 de la reserva, HU-06).
// Un 404 acá significa que algún servicio dejó de estar disponible mientras el cliente elegía: para la pantalla es un error.
export async function obtenerProfesionales(servicioIds: string[]): Promise<ResultadoProfesionales> {
  try {
    const parametros = new URLSearchParams({ servicios: servicioIds.join(',') })
    const respuesta = await fetch(`/api/publico/profesionales?${parametros}`)
    if (!respuesta.ok) {
      return { estado: 'error' }
    }
    const profesionales: ProfesionalPublico[] = await respuesta.json()
    return { estado: 'ok', profesionales }
  } catch {
    return { estado: 'error' }
  }
}
