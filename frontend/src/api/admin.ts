import { guardarEnElPanel, pedirDelPanel, subirArchivo, type ResultadoGuardar, type ResultadoPanel } from './panel'
import type {
  ClienteAdmin, ClienteConHistorial, ConfiguracionDelLocal, FranjaHorario, HorarioGuardado, ImagenSubida, ProfesionalAdmin, ProfesionalPedido, ResumenDeLaSemana, ServicioAdmin, ServicioPedido,
  TurnoFuturo,
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

// HU-26: el horario semanal de un profesional (franjas por día, 0 domingo a 6 sábado, en hora del local).
export function verHorario(id: string): Promise<ResultadoPanel<FranjaHorario[]>> {
  return pedirDelPanel<FranjaHorario[]>(`/api/admin/profesionales/${id}/horario`)
}

// Reemplaza el horario entero. La respuesta trae los turnos por delante que quedaron fuera del horario nuevo (CA3).
export function guardarHorario(id: string, franjas: FranjaHorario[]): Promise<ResultadoGuardar<HorarioGuardado>> {
  return guardarEnElPanel(`/api/admin/profesionales/${id}/horario`, 'PUT', { franjas })
}

// HU-31: clientes con visitas, faltas y última visita; se busca por nombre o teléfono.
export function listarClientes(buscar: string): Promise<ResultadoPanel<ClienteAdmin[]>> {
  const parametros = buscar.trim() === '' ? '' : `?${new URLSearchParams({ buscar: buscar.trim() })}`
  return pedirDelPanel<ClienteAdmin[]>(`/api/admin/clientes${parametros}`)
}

export function verCliente(id: string): Promise<ResultadoPanel<ClienteConHistorial>> {
  return pedirDelPanel<ClienteConHistorial>(`/api/admin/clientes/${id}`)
}

// HU-34: anonimiza sus datos personales. Con turnos por delante, la API pide cancelarlos antes (TURNOS_FUTUROS).
export function borrarDatosCliente(id: string): Promise<ResultadoGuardar<ClienteAdmin>> {
  return guardarEnElPanel(`/api/admin/clientes/${id}/borrar-datos`, 'POST')
}

// HU-32: datos, imagen, pago y reglas de turnos del local.
export function verLocal(): Promise<ResultadoPanel<ConfiguracionDelLocal>> {
  return pedirDelPanel<ConfiguracionDelLocal>('/api/admin/local')
}

export function guardarLocal(configuracion: ConfiguracionDelLocal): Promise<ResultadoGuardar<ConfiguracionDelLocal>> {
  return guardarEnElPanel('/api/admin/local', 'PUT', configuracion)
}

// HU-33: turnos de hoy y de la semana, ingresos estimados y faltas.
export function verResumen(): Promise<ResultadoPanel<ResumenDeLaSemana>> {
  return pedirDelPanel<ResumenDeLaSemana>('/api/admin/resumen')
}
