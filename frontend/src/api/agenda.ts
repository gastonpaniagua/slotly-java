import { guardarEnElPanel, type ResultadoGuardar } from './panel'
import type { BloqueosCreados, PedidoDeBloqueo, PedidoDeTurnoManual, TurnoGuardado } from './tipos'

// Lo que se hace desde la agenda del panel, para el admin y el profesional (cada uno sobre la agenda que ve).

// Un turno que impide el bloqueo (HU-27 CA2). Viene dentro del error (no en el esquema de la API), por eso se
// declara acá.
export type TurnoEnElRango = { id: string; profesionalNombre: string; inicio: string; clienteNombre: string | null }

// HU-27: profesional es un id, "todos" (todo el local, solo el admin) o null (el propio). Horas del local.
export function crearBloqueo(pedido: PedidoDeBloqueo): Promise<ResultadoGuardar<BloqueosCreados>> {
  return guardarEnElPanel('/api/panel/bloqueos', 'POST', pedido)
}

// CA4: al borrarlo, esas horas vuelven a ofrecerse.
export function borrarBloqueo(id: string): Promise<ResultadoGuardar<null>> {
  return guardarEnElPanel(`/api/panel/bloqueos/${id}`, 'DELETE')
}

// HU-28: un turno manual, para quien llama o entra sin reserva. El teléfono es opcional.
export function crearTurnoManual(pedido: PedidoDeTurnoManual): Promise<ResultadoGuardar<TurnoGuardado>> {
  return guardarEnElPanel('/api/panel/turnos', 'POST', pedido)
}

// HU-29: moverlo a otro día u hora (mismo profesional) y cancelarlo, sin plazo.
export function moverTurno(id: string, fecha: string, hora: string): Promise<ResultadoGuardar<TurnoGuardado>> {
  return guardarEnElPanel(`/api/panel/turnos/${id}/mover`, 'POST', { fecha, hora })
}

export function cancelarTurnoDelPanel(id: string): Promise<ResultadoGuardar<TurnoGuardado>> {
  return guardarEnElPanel(`/api/panel/turnos/${id}/cancelar`, 'POST')
}

// HU-30: si el cliente vino (true) o no (false), en un turno que ya empezó. Se corrige el mismo día.
export function marcarAsistencia(id: string, vino: boolean): Promise<ResultadoGuardar<TurnoGuardado>> {
  return guardarEnElPanel(`/api/panel/turnos/${id}/asistencia`, 'POST', { vino })
}
