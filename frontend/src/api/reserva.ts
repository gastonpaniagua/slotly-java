import type { Resultado } from './publico'
import type { ApartadoCreado, ConfirmarPedido, CrearApartadoPedido, DiasConLugar, HorasLibres, TurnoPublico } from './tipos'

// Lo que devuelve la API cuando algo sale mal: el front decide por el código (doc 00); el detalle es para mostrar.
type Problema = { codigo?: string; detail?: string; errores?: { campo: string; mensaje: string }[] }

async function enviar(url: string, cuerpo: unknown): Promise<{ status: number; datos: unknown } | null> {
  try {
    const respuesta = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cuerpo),
    })
    const datos: unknown = await respuesta.json().catch(() => null)
    return { status: respuesta.status, datos }
  } catch {
    return null
  }
}

export type ResultadoApartar =
  | { estado: 'ok'; apartado: ApartadoCreado }
  | { estado: 'ocupado' }
  | { estado: 'demasiados' }
  | { estado: 'error' }

// Aparta la hora elegida al pasar al paso 5 (HU-11). Con anterior, libera el apartado que tenía antes (CA3).
export async function apartar(pedido: CrearApartadoPedido): Promise<ResultadoApartar> {
  const respuesta = await enviar('/api/publico/apartados', pedido)
  if (respuesta?.status === 201) {
    return { estado: 'ok', apartado: respuesta.datos as ApartadoCreado }
  }
  const codigo = (respuesta?.datos as Problema | null)?.codigo
  if (codigo === 'HORARIO_OCUPADO') {
    return { estado: 'ocupado' }
  }
  if (codigo === 'DEMASIADOS_INTENTOS') {
    return { estado: 'demasiados' }
  }
  return { estado: 'error' }
}

export type ResultadoConfirmar =
  | { estado: 'ok'; turno: TurnoPublico }
  | { estado: 'validacion'; errores: Record<string, string> }
  | { estado: 'vencido' }
  | { estado: 'maximo'; mensaje: string }
  | { estado: 'demasiados' }
  | { estado: 'error' }

// Confirma el apartado con los datos del cliente (HU-10).
export async function confirmar(id: string, pedido: ConfirmarPedido): Promise<ResultadoConfirmar> {
  const respuesta = await enviar(`/api/publico/apartados/${id}/confirmar`, pedido)
  if (respuesta?.status === 200) {
    return { estado: 'ok', turno: respuesta.datos as TurnoPublico }
  }
  const problema = (respuesta?.datos ?? null) as Problema | null
  switch (problema?.codigo) {
    case 'VALIDACION':
      // HU-10 CA4: cada error va junto a su campo.
      return {
        estado: 'validacion',
        errores: Object.fromEntries((problema.errores ?? []).map((e) => [e.campo, e.mensaje])),
      }
    case 'APARTADO_VENCIDO':
    case 'TURNO_NO_ENCONTRADO':
      return { estado: 'vencido' }
    case 'MAXIMO_TURNOS':
      return { estado: 'maximo', mensaje: problema.detail ?? 'Ya tenés el máximo de turnos reservados.' }
    case 'DEMASIADOS_INTENTOS':
      return { estado: 'demasiados' }
    default:
      return { estado: 'error' }
  }
}

// Con qué prueba el cliente que el turno es suyo: el token del link (un texto) o el acceso de entrar con el teléfono.
export type CredencialTurno = string | { acceso: string }

function cabeceras(credencial: CredencialTurno): Record<string, string> {
  return typeof credencial === 'string' ? { 'X-Token-Turno': credencial } : { 'X-Acceso-Cliente': credencial.acceso }
}

export type ResultadoTurno =
  | { estado: 'ok'; turno: TurnoPublico }
  | { estado: 'invalido' }
  | { estado: 'vencido' }
  | { estado: 'error' }

// Un turno con su token: la confirmación al recargar y "Mi turno" (HU-10 CA7 y CA9, HU-19).
// "invalido": el token no corresponde, el turno no existe o es de otro local (CA2 y CA5), o el acceso del cliente
// venció. "vencido": pasaron 30 días desde el turno (CA3).
export async function obtenerTurno(id: string, credencial: CredencialTurno): Promise<ResultadoTurno> {
  try {
    const respuesta = await fetch(`/api/publico/turnos/${id}`, { headers: cabeceras(credencial) })
    if (respuesta.ok) {
      return { estado: 'ok', turno: (await respuesta.json()) as TurnoPublico }
    }
    const problema = (await respuesta.json().catch(() => null)) as Problema | null
    if (problema?.codigo === 'TURNO_NO_ENCONTRADO' || problema?.codigo === 'NO_AUTENTICADO') {
      return { estado: 'invalido' }
    }
    return problema?.codigo === 'LINK_VENCIDO' ? { estado: 'vencido' } : { estado: 'error' }
  } catch {
    return { estado: 'error' }
  }
}

export type ResultadoCancelar =
  | { estado: 'ok'; turno: TurnoPublico }
  | { estado: 'fuera-de-plazo' }
  | { estado: 'invalido' }
  | { estado: 'vencido' }
  | { estado: 'error' }

// HU-20: cancela el turno con el token de su link. Fuera de plazo (menos de 3 horas) decide el local (CA2).
export async function cancelarTurno(id: string, credencial: CredencialTurno): Promise<ResultadoCancelar> {
  try {
    const respuesta = await fetch(`/api/publico/turnos/${id}/cancelar`, {
      method: 'POST',
      headers: cabeceras(credencial),
    })
    if (respuesta.ok) {
      return { estado: 'ok', turno: (await respuesta.json()) as TurnoPublico }
    }
    const problema = (await respuesta.json().catch(() => null)) as Problema | null
    switch (problema?.codigo) {
      case 'FUERA_DE_PLAZO':
        return { estado: 'fuera-de-plazo' }
      case 'TURNO_NO_ENCONTRADO':
        return { estado: 'invalido' }
      case 'LINK_VENCIDO':
        return { estado: 'vencido' }
      default:
        return { estado: 'error' }
    }
  } catch {
    return { estado: 'error' }
  }
}

// HU-21: días y horas para mover el turno. No se mandan servicios ni profesional: la API usa los del turno.
async function pedirConCredencial<T>(url: string, credencial: CredencialTurno): Promise<Resultado<T>> {
  try {
    const respuesta = await fetch(url, { headers: cabeceras(credencial) })
    return respuesta.ok ? { estado: 'ok', datos: (await respuesta.json()) as T } : { estado: 'error' }
  } catch {
    return { estado: 'error' }
  }
}

export function obtenerDiasParaReprogramar(id: string, credencial: CredencialTurno, desde?: string): Promise<Resultado<DiasConLugar>> {
  const parametros = new URLSearchParams(desde ? { desde } : {})
  return pedirConCredencial(`/api/publico/turnos/${id}/reprogramar/dias?${parametros}`, credencial)
}

export function obtenerHorasParaReprogramar(id: string, credencial: CredencialTurno, fecha: string): Promise<Resultado<HorasLibres>> {
  return pedirConCredencial(`/api/publico/turnos/${id}/reprogramar/horas?${new URLSearchParams({ fecha })}`, credencial)
}

export type ResultadoReprogramar =
  | { estado: 'ok'; id: string; token: string; turno: TurnoPublico }
  | { estado: 'ocupado' }
  | { estado: 'fuera-de-plazo' }
  | { estado: 'error' }

// HU-21 CA2: mueve el turno. El turno nuevo tiene su propio link (id y token nuevos); el viejo queda cancelado.
export async function reprogramarTurno(id: string, credencial: CredencialTurno, fecha: string, hora: string): Promise<ResultadoReprogramar> {
  try {
    const respuesta = await fetch(`/api/publico/turnos/${id}/reprogramar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...cabeceras(credencial) },
      body: JSON.stringify({ fecha, hora }),
    })
    if (respuesta.ok) {
      const datos = (await respuesta.json()) as { id: string; token: string; turno: TurnoPublico }
      return { estado: 'ok', ...datos }
    }
    const problema = (await respuesta.json().catch(() => null)) as Problema | null
    switch (problema?.codigo) {
      case 'HORARIO_OCUPADO':
        return { estado: 'ocupado' }
      case 'FUERA_DE_PLAZO':
        return { estado: 'fuera-de-plazo' }
      default:
        return { estado: 'error' }
    }
  } catch {
    return { estado: 'error' }
  }
}
