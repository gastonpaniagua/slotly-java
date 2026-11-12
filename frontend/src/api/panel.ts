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

async function enviar(url: string, cuerpo?: unknown): Promise<Response | null> {
  try {
    const token = await asegurarTokenCsrf()
    return await fetch(url, {
      method: 'POST',
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
