// Nombres cortos para los tipos que genera openapi-typescript desde el back (npm run tipos).
// Si el back cambia una respuesta, se regenera esquema.ts y el compilador marca qué adaptar.
import type { components } from './esquema'

type Esquemas = components['schemas']

export type LocalPublico = Esquemas['LocalPublicoResponse']
export type ServicioPublico = Esquemas['ServicioPublicoResponse']
export type DiaPublico = Esquemas['DiaPublicoResponse']
export type FranjaPublica = Esquemas['FranjaPublicaResponse']
export type ProfesionalPublico = Esquemas['ProfesionalPublicoResponse']
export type DiasConLugar = Esquemas['DiasConLugar']
export type DiaConLugar = Esquemas['Dia']
export type HorasLibres = Esquemas['HorasResponse']
export type CrearApartadoPedido = Esquemas['CrearApartadoRequest']
export type ApartadoCreado = Esquemas['Apartado']
export type ConfirmarPedido = Esquemas['ConfirmarRequest']
export type DatosTransferencia = Esquemas['DatosTransferencia']
// La API manda transferencia: null cuando se paga en efectivo, pero swagger no puede marcar como "puede ser null"
// un campo que es otro objeto (ignora la anotación): se corrige acá para que el compilador obligue a contemplarlo.
export type TurnoPublico = Omit<Esquemas['TurnoPublico'], 'transferencia'> & {
  transferencia: DatosTransferencia | null
}

// Panel (Etapa 5)
export type UsuarioPanel = Esquemas['UsuarioActual']
export type Agenda = Esquemas['Agenda']
export type TurnoDeAgenda = Esquemas['TurnoDeAgenda']
export type FranjaDeAgenda = Esquemas['FranjaDeAgenda']
export type ProfesionalDeAgenda = Esquemas['ProfesionalDeAgenda']
