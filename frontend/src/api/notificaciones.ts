import { guardarEnElPanel, pedirDelPanel, type ResultadoGuardar, type ResultadoPanel } from './panel'
import type { BandejaDeAvisos } from './tipos'

// HU-37: los avisos del profesional de la sesión (las reservas, cancelaciones y cambios que hizo otro en su agenda)
// y cuántos no leyó.
export function verAvisos(): Promise<ResultadoPanel<BandejaDeAvisos>> {
  return pedirDelPanel<BandejaDeAvisos>('/api/panel/notificaciones')
}

// CA2: al abrir la lista, todos pasan a leídos.
export function marcarAvisosLeidos(): Promise<ResultadoGuardar<void>> {
  return guardarEnElPanel<void>('/api/panel/notificaciones/leidas', 'POST')
}
