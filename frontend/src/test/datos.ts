import type { LocalPublico, ServicioPublico, TurnoDeAgenda } from '../api/tipos'

// Builders para los tests: cada test arma su local y pisa solo lo que le importa (doc 06: sin datos compartidos).

export function unServicio(datos: Partial<ServicioPublico> = {}): ServicioPublico {
  return {
    id: crypto.randomUUID(),
    nombre: 'Corte de pelo',
    descripcion: null,
    duracionMin: 40,
    precio: 14000,
    ...datos,
  }
}

export function unLocal(datos: Partial<LocalPublico> = {}): LocalPublico {
  return {
    nombre: 'Barbería Demo',
    descripcion: null,
    logoUrl: null,
    colorAcento: null,
    whatsapp: null,
    instagram: null,
    tiktok: null,
    facebook: null,
    direccion: null,
    ciudad: null,
    latitud: null,
    longitud: null,
    servicios: [],
    horarios: [],
    ...datos,
  }
}

// Un turno de la agenda del panel: el martes 17/11 de 10:00 a 10:30, de Ana, confirmado.
export function unTurno(datos: Partial<TurnoDeAgenda> = {}): TurnoDeAgenda {
  return {
    id: crypto.randomUUID(),
    profesionalId: 'ana',
    tipo: 'reserva',
    estado: 'confirmado',
    inicio: '2026-11-17T10:00',
    fin: '2026-11-17T10:30',
    clienteNombre: 'Lucía',
    clienteTelefono: '+5491123456789',
    servicios: ['Corte'],
    formaPago: 'efectivo',
    nota: null,
    motivo: null,
    ...datos,
  }
}
