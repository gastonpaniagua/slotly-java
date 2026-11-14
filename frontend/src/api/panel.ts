import type { Agenda, UsuarioPanel } from './tipos'

// Pedidos del panel. La sesión viaja sola en la cookie httpOnly SLOTLY_SESION (el front no la ve ni la toca).
// Lo que sí hace el front es el CSRF (D1): la API deja el token en la cookie XSRF-TOKEN y cada POST lo devuelve
// en el header X-XSRF-TOKEN. Otro sitio no puede leer esa cookie, así que no puede armar un POST válido.

function tokenCsrf(): string | null {
  const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : null
}

// La cookie del token la deja la API en la respuesta a cualquier pedido del panel. Si todavía no llegó
// (primera visita), se pide "quién soy" para recibirla antes del POST.
async function asegurarTokenCsrf(): Promise<string | null> {
  if (tokenCsrf() === null) {
    await fetch('/api/panel/yo').catch(() => null)
  }
  return tokenCsrf()
}

async function enviar(url: string, cuerpo?: unknown, metodo: 'POST' | 'PUT' | 'DELETE' = 'POST'): Promise<Response | null> {
  try {
    const token = await asegurarTokenCsrf()
    return await fetch(url, {
      method: metodo,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'X-XSRF-TOKEN': token } : {}),
      },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
  } catch {
    return null
  }
}

export type ResultadoIngresar =
  | { estado: 'ok'; usuario: UsuarioPanel }
  | { estado: 'invalido' }
  | { estado: 'demasiados' }
  | { estado: 'error' }

// HU-16: entrar con mail y contraseña. "invalido" es el mismo para mail inexistente y contraseña mal (CA2).
export async function ingresar(email: string, password: string): Promise<ResultadoIngresar> {
  const respuesta = await enviar('/api/panel/ingresar', { email, password })
  if (respuesta?.ok) {
    return { estado: 'ok', usuario: (await respuesta.json()) as UsuarioPanel }
  }
  const problema: { codigo?: string } | null = await respuesta?.json().catch(() => null) ?? null
  switch (problema?.codigo) {
    case 'CREDENCIALES_INVALIDAS':
    case 'VALIDACION':
      return { estado: 'invalido' }
    case 'DEMASIADOS_INTENTOS':
      return { estado: 'demasiados' }
    default:
      return { estado: 'error' }
  }
}

// HU-16 CA7: cierra la sesión en el servidor. Si falla la red no importa: el front igual vuelve al login.
export async function salir(): Promise<void> {
  await enviar('/api/panel/salir')
}

// Lo que pasa al pedir algo del panel: los datos, que no hay sesión (o se cerró en otra pestaña: CA7), o un error.
export type ResultadoPanel<T> = { estado: 'ok'; datos: T } | { estado: 'sin-sesion' } | { estado: 'error' }

export async function pedirDelPanel<T>(url: string): Promise<ResultadoPanel<T>> {
  try {
    const respuesta = await fetch(url)
    if (respuesta.ok) {
      return { estado: 'ok', datos: (await respuesta.json()) as T }
    }
    return respuesta.status === 401 ? { estado: 'sin-sesion' } : { estado: 'error' }
  } catch {
    return { estado: 'error' }
  }
}

// ¿Quién soy? Al entrar a una página del panel: sin sesión, al login (CA6).
export function obtenerUsuario(): Promise<ResultadoPanel<UsuarioPanel>> {
  return pedirDelPanel<UsuarioPanel>('/api/panel/yo')
}

// HU-17 y HU-18: la agenda de 1 o 7 días desde esa fecha (sin fecha, desde hoy en la zona del local).
// Un profesional recibe la suya; el admin, la de todos (el filtro por profesional lo hace la pantalla).
export function obtenerAgenda(desde: string | null, dias: 1 | 7): Promise<ResultadoPanel<Agenda>> {
  const parametros = new URLSearchParams({ dias: String(dias) })
  if (desde !== null) {
    parametros.set('desde', desde)
  }
  return pedirDelPanel<Agenda>(`/api/panel/agenda?${parametros}`)
}

// Lo que puede pasar al guardar algo desde el panel: lo guardado, errores por campo (400 VALIDACION, para mostrar
// junto a cada campo), sin sesión (al login, HU-16 CA7), sin permiso (403) o un error.
export type ResultadoGuardar<T> =
  | { estado: 'ok'; datos: T }
  | { estado: 'validacion'; errores: Record<string, string>; mensaje: string }
  | { estado: 'sin-sesion' }
  | { estado: 'sin-permiso' }
  // codigo y problema: el error completo, para los casos que traen datos (por ejemplo, la lista de turnos que
  // impiden un bloqueo, HU-27 CA2).
  | { estado: 'error'; mensaje?: string; codigo?: string; problema?: Record<string, unknown> }

export async function guardarEnElPanel<T>(url: string, metodo: 'POST' | 'PUT' | 'DELETE', cuerpo?: unknown): Promise<ResultadoGuardar<T>> {
  const respuesta = await enviar(url, cuerpo, metodo)
  if (respuesta === null) {
    return { estado: 'error' }
  }
  if (respuesta.ok) {
    return { estado: 'ok', datos: (await respuesta.json().catch(() => null)) as T }
  }
  const problema = (await respuesta.json().catch(() => null)) as
    { codigo?: string; detail?: string; errores?: { campo: string; mensaje: string }[] } | null
  if (respuesta.status === 401) {
    return { estado: 'sin-sesion' }
  }
  if (respuesta.status === 403) {
    return { estado: 'sin-permiso' }
  }
  if (problema?.codigo === 'VALIDACION') {
    return {
      estado: 'validacion',
      errores: Object.fromEntries((problema.errores ?? []).map((e) => [e.campo, e.mensaje])),
      mensaje: problema.detail ?? 'Revisá los datos marcados.',
    }
  }
  return { estado: 'error', mensaje: problema?.detail, codigo: problema?.codigo, problema: problema ?? undefined }
}

// Sube un archivo (multipart) con el token CSRF, para las imágenes del local (D10). Un 413 es un archivo enorme
// que el servidor cortó antes de mirarlo.
export async function subirArchivo<T>(url: string, archivo: File): Promise<ResultadoGuardar<T>> {
  try {
    const token = await asegurarTokenCsrf()
    const formulario = new FormData()
    formulario.append('archivo', archivo)
    const respuesta = await fetch(url, {
      method: 'POST',
      headers: token ? { 'X-XSRF-TOKEN': token } : {},
      body: formulario,
    })
    if (respuesta.ok) {
      return { estado: 'ok', datos: (await respuesta.json()) as T }
    }
    if (respuesta.status === 401) {
      return { estado: 'sin-sesion' }
    }
    if (respuesta.status === 403) {
      return { estado: 'sin-permiso' }
    }
    if (respuesta.status === 413) {
      return { estado: 'validacion', errores: {}, mensaje: 'La imagen puede pesar hasta 500 KB.' }
    }
    const problema = (await respuesta.json().catch(() => null)) as { codigo?: string; detail?: string } | null
    return problema?.codigo === 'VALIDACION'
      ? { estado: 'validacion', errores: {}, mensaje: problema.detail ?? 'La imagen no es válida.' }
      : { estado: 'error' }
  } catch {
    return { estado: 'error' }
  }
}
