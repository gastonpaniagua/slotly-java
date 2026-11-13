// Lo que este navegador recuerda del cliente (HU-10 CA9): nombre y celular para no escribirlos de nuevo, y los
// links de sus turnos ("Mis turnos"). Va en localStorage, que es por subdominio: demo y otra no se mezclan.
// Todo protegido con try/catch: en modo incógnito o con el almacenamiento bloqueado, la página sigue andando.

const CLAVE_CLIENTE = 'slotly.cliente'
const CLAVE_TURNOS = 'slotly.turnos'
const CLAVE_ACCESO = 'slotly.acceso'
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

// Al reprogramar (HU-21), el turno nuevo (con su link) reemplaza al viejo en "Mis turnos".
export function reemplazarTurno(idViejo: string, nuevo: TurnoRecordado) {
  const otros = leerTurnos().filter((t) => t.id !== idViejo && t.id !== nuevo.id)
  guardar(CLAVE_TURNOS, [...otros, nuevo].slice(-MAXIMO_TURNOS))
}

// El acceso de entrar con el teléfono (HU-22): 30 días en este navegador. Vencido, es como no tenerlo.
export type AccesoRecordado = { token: string; venceEn: string }

export function leerAcceso(): AccesoRecordado | null {
  const acceso = leer<AccesoRecordado>(CLAVE_ACCESO)
  if (!acceso || typeof acceso.token !== 'string' || typeof acceso.venceEn !== 'string') {
    return null
  }
  return Date.parse(acceso.venceEn) > Date.now() ? acceso : null
}

export function recordarAcceso(acceso: AccesoRecordado) {
  guardar(CLAVE_ACCESO, acceso)
}

export function olvidarAcceso() {
  try {
    localStorage.removeItem(CLAVE_ACCESO)
  } catch {
    // Sin almacenamiento: no había nada guardado.
  }
}

// Con qué abrir un turno: el token del link si vino en la dirección; si no, el acceso de este navegador (si hay).
export function credencialDelTurno(tokenDelLink: string): string | { acceso: string } {
  if (tokenDelLink !== '') {
    return tokenDelLink
  }
  const acceso = leerAcceso()
  return acceso ? { acceso: acceso.token } : ''
}

// El link de "Mi turno": el token va después del #, la parte de la URL que el navegador nunca manda al servidor.
export function linkDelTurno(turno: { id: string; token: string }): string {
  return `/turno/${turno.id}#t=${encodeURIComponent(turno.token)}`
}
