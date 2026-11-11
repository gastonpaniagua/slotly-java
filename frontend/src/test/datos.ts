import type { LocalPublico, ServicioPublico } from '../api/tipos'

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
