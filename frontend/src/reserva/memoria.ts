// Lo que este navegador recuerda del cliente (HU-10 CA9): nombre y celular para no escribirlos de nuevo, y los
// links de sus turnos ("Mis turnos"). Va en localStorage, que es por subdominio: demo y otra no se mezclan.
// Todo protegido con try/catch: en modo incógnito o con el almacenamiento bloqueado, la página sigue andando.

const CLAVE_CLIENTE = 'slotly.cliente'
const CLAVE_TURNOS = 'slotly.turnos'
// Se guardan los últimos turnos; los más viejos se descartan.
const MAXIMO_TURNOS = 10

export type ClienteRecordado = { nombre: string; telefono: string }
export type TurnoRecordado = { id: string; token: string; fecha: string; hora: string }

function leer<T>(clave: string): T | null {
  try {
    const texto = localStorage.getItem(clave)
    return texto === null ? null : (JSON.parse(texto) as T)
  } catch {
    return null
  }
}

function guardar(clave: string, valor: unknown) {
  try {
    localStorage.setItem(clave, JSON.stringify(valor))
  } catch {
    // Sin almacenamiento disponible: no se recuerda, y listo.
  }
}

export function leerCliente(): ClienteRecordado | null {
  const cliente = leer<ClienteRecordado>(CLAVE_CLIENTE)
  return cliente && typeof cliente.nombre === 'string' && typeof cliente.telefono === 'string' ? cliente : null
}

export function recordarCliente(cliente: ClienteRecordado) {
  guardar(CLAVE_CLIENTE, cliente)
}

// Los turnos recordados, del más próximo al más lejano.
export function leerTurnos(): TurnoRecordado[] {
  const turnos = leer<TurnoRecordado[]>(CLAVE_TURNOS)
  if (!Array.isArray(turnos)) {
    return []
  }
  return turnos
    .filter((t) => t && typeof t.id === 'string' && typeof t.token === 'string')
    .sort((a, b) => `${a.fecha} ${a.hora}`.localeCompare(`${b.fecha} ${b.hora}`))
}

export function recordarTurno(turno: TurnoRecordado) {
  const otros = leerTurnos().filter((t) => t.id !== turno.id)
  guardar(CLAVE_TURNOS, [...otros, turno].slice(-MAXIMO_TURNOS))
}

// El link de "Mi turno": el token va después del #, la parte de la URL que el navegador nunca manda al servidor.
export function linkDelTurno(turno: { id: string; token: string }): string {
  return `/turno/${turno.id}#t=${encodeURIComponent(turno.token)}`
}
