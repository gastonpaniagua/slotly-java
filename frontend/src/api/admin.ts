import { guardarEnElPanel, pedirDelPanel, type ResultadoGuardar, type ResultadoPanel } from './panel'
import type { ServicioAdmin, ServicioPedido } from './tipos'

// Gestión del local (Etapa 7): todo bajo /api/admin, solo para el admin (un profesional recibe 403).

// HU-24: servicios, también los ocultos, en el orden de la home.
export function listarServicios(): Promise<ResultadoPanel<ServicioAdmin[]>> {
  return pedirDelPanel<ServicioAdmin[]>('/api/admin/servicios')
}

export function crearServicio(servicio: ServicioPedido): Promise<ResultadoGuardar<ServicioAdmin>> {
  return guardarEnElPanel('/api/admin/servicios', 'POST', servicio)
}

export function editarServicio(id: string, servicio: ServicioPedido): Promise<ResultadoGuardar<ServicioAdmin>> {
  return guardarEnElPanel(`/api/admin/servicios/${id}`, 'PUT', servicio)
}

export function ordenarServicios(ids: string[]): Promise<ResultadoGuardar<ServicioAdmin[]>> {
  return guardarEnElPanel('/api/admin/servicios/orden', 'POST', { ids })
}
