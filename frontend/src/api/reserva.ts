import type { ApartadoCreado, ConfirmarPedido, CrearApartadoPedido, TurnoPublico } from './tipos'

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

export type ResultadoTurno =
  | { estado: 'ok'; turno: TurnoPublico }
  | { estado: 'invalido' }
  | { estado: 'vencido' }
  | { estado: 'error' }

// Un turno con su token: la confirmación al recargar y "Mi turno" (HU-10 CA7 y CA9, HU-19).
// "invalido": el token no corresponde, el turno no existe o es de otro local (CA2 y CA5). "vencido": pasaron
// 30 días desde el turno (CA3).
export async function obtenerTurno(id: string, token: string): Promise<ResultadoTurno> {
  try {
    const respuesta = await fetch(`/api/publico/turnos/${id}`, { headers: { 'X-Token-Turno': token } })
    if (respuesta.ok) {
      return { estado: 'ok', turno: (await respuesta.json()) as TurnoPublico }
    }
    const problema = (await respuesta.json().catch(() => null)) as Problema | null
    if (problema?.codigo === 'TURNO_NO_ENCONTRADO') {
      return { estado: 'invalido' }
    }
    return problema?.codigo === 'LINK_VENCIDO' ? { estado: 'vencido' } : { estado: 'error' }
  } catch {
    return { estado: 'error' }
  }
}
