import type { MisTurnos } from './tipos'

// Entrar con el teléfono y un código (HU-22). El front decide por el código de error (doc 00), nunca por el texto.
type Problema = { codigo?: string; detail?: string; errores?: { campo: string; mensaje: string }[] }

async function enviar(url: string, cuerpo: unknown): Promise<{ status: number; datos: unknown } | null> {
  try {
    const respuesta = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cuerpo),
    })
    return { status: respuesta.status, datos: await respuesta.json().catch(() => null) }
  } catch {
    return null
  }
}

// El primer mensaje de un 400 VALIDACION (por ejemplo, "Revisá el número de celular."), si lo hay.
function mensajeDeValidacion(datos: unknown): string | null {
  const problema = datos as Problema | null
  return problema?.codigo === 'VALIDACION' ? problema.errores?.[0]?.mensaje ?? problema.detail ?? null : null
}

export type ResultadoPedirCodigo =
  | { estado: 'ok'; vigenciaMinutos: number }
  | { estado: 'validacion'; mensaje: string }
  | { estado: 'demasiados' }
  | { estado: 'error' }

// CA1 y CA2: la respuesta es la misma tenga o no turnos el teléfono; la pantalla dice lo mismo en los dos casos.
export async function pedirCodigo(telefono: string): Promise<ResultadoPedirCodigo> {
  const respuesta = await enviar('/api/publico/codigos', { telefono })
  if (respuesta?.status === 202) {
    return { estado: 'ok', vigenciaMinutos: (respuesta.datos as { vigenciaMinutos: number }).vigenciaMinutos }
  }
  const validacion = mensajeDeValidacion(respuesta?.datos)
  if (validacion !== null) {
    return { estado: 'validacion', mensaje: validacion }
  }
  return (respuesta?.datos as Problema | null)?.codigo === 'DEMASIADOS_INTENTOS' ? { estado: 'demasiados' } : { estado: 'error' }
}

export type AccesoCliente = { token: string; venceEn: string }

export type ResultadoVerificar =
  | { estado: 'ok'; acceso: AccesoCliente }
  | { estado: 'invalido' }
  | { estado: 'validacion'; mensaje: string }
  | { estado: 'demasiados' }
  | { estado: 'error' }

// CA3 a CA5: con el código correcto, la API entrega el acceso del cliente (30 días).
export async function verificarCodigo(telefono: string, codigo: string): Promise<ResultadoVerificar> {
  const respuesta = await enviar('/api/publico/codigos/verificar', { telefono, codigo })
  if (respuesta?.status === 200) {
    return { estado: 'ok', acceso: respuesta.datos as AccesoCliente }
  }
  const validacion = mensajeDeValidacion(respuesta?.datos)
  if (validacion !== null) {
    return { estado: 'validacion', mensaje: validacion }
  }
  switch ((respuesta?.datos as Problema | null)?.codigo) {
    case 'CODIGO_INVALIDO':
      return { estado: 'invalido' }
    case 'DEMASIADOS_INTENTOS':
      return { estado: 'demasiados' }
    default:
      return { estado: 'error' }
  }
}

export type ResultadoMisTurnos = { estado: 'ok'; datos: MisTurnos } | { estado: 'sin-acceso' } | { estado: 'error' }

// HU-22 CA3 y HU-23 CA1: próximos e historial. "sin-acceso": venció o no es de este local; hay que volver a entrar.
export async function obtenerMisTurnos(acceso: string): Promise<ResultadoMisTurnos> {
  try {
    const respuesta = await fetch('/api/publico/mis-turnos', { headers: { 'X-Acceso-Cliente': acceso } })
    if (respuesta.ok) {
      return { estado: 'ok', datos: (await respuesta.json()) as MisTurnos }
    }
    return respuesta.status === 401 ? { estado: 'sin-acceso' } : { estado: 'error' }
  } catch {
    return { estado: 'error' }
  }
}
