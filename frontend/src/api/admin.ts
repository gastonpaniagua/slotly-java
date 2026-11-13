import { guardarEnElPanel, pedirDelPanel, subirArchivo, type ResultadoGuardar, type ResultadoPanel } from './panel'
import type {
  ImagenSubida, ProfesionalAdmin, ProfesionalPedido, ServicioAdmin, ServicioPedido, TurnoFuturo,
} from './tipos'

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

// D10: una imagen del local (PNG, JPG o WebP, hasta 500 KB). Devuelve la URL para guardar en la foto o el logo.
export function subirImagen(archivo: File): Promise<ResultadoGuardar<ImagenSubida>> {
  return subirArchivo('/api/admin/imagenes', archivo)
}

// HU-25: profesionales, también los dados de baja.
export function listarProfesionales(): Promise<ResultadoPanel<ProfesionalAdmin[]>> {
  return pedirDelPanel<ProfesionalAdmin[]>('/api/admin/profesionales')
}

export function crearProfesional(profesional: ProfesionalPedido): Promise<ResultadoGuardar<ProfesionalAdmin>> {
  return guardarEnElPanel('/api/admin/profesionales', 'POST', profesional)
}

export function editarProfesional(id: string, profesional: ProfesionalPedido): Promise<ResultadoGuardar<ProfesionalAdmin>> {
  return guardarEnElPanel(`/api/admin/profesionales/${id}`, 'PUT', profesional)
}

// CA2: el usuario con el que el profesional entra al panel.
export function crearUsuarioProfesional(id: string, email: string, password: string): Promise<ResultadoGuardar<ProfesionalAdmin>> {
  return guardarEnElPanel(`/api/admin/profesionales/${id}/usuario`, 'POST', { email, password })
}

// CA3: antes de la baja, sus turnos por delante.
export function turnosFuturosDe(id: string): Promise<ResultadoPanel<TurnoFuturo[]>> {
  return pedirDelPanel<TurnoFuturo[]>(`/api/admin/profesionales/${id}/turnos-futuros`)
}

export function darDeBaja(id: string, confirmar: boolean): Promise<ResultadoGuardar<ProfesionalAdmin>> {
  return guardarEnElPanel(`/api/admin/profesionales/${id}/baja`, 'POST', { confirmar })
}

export function darDeAlta(id: string): Promise<ResultadoGuardar<ProfesionalAdmin>> {
  return guardarEnElPanel(`/api/admin/profesionales/${id}/alta`, 'POST')
}
