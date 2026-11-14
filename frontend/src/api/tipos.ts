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

// Mis turnos (Etapa 6). Cada turno es un TurnoPublico, con la misma corrección de transferencia que arriba.
export type TurnoDelCliente = Omit<Esquemas['TurnoDelCliente'], 'turno'> & { turno: TurnoPublico }
export type MisTurnos = { proximos: TurnoDelCliente[]; historial: TurnoDelCliente[] }

// Gestión del local (Etapa 7)
export type ServicioAdmin = Esquemas['ServicioAdmin']
export type ServicioPedido = Esquemas['ServicioRequest']
export type ProfesionalAdmin = Esquemas['ProfesionalAdmin']
export type ProfesionalPedido = Esquemas['ProfesionalRequest']
export type TurnoFuturo = Esquemas['TurnoFuturo']
export type ImagenSubida = Esquemas['ImagenSubida']
export type FranjaHorario = Esquemas['Franja']
export type HorarioGuardado = Esquemas['HorarioGuardado']
export type PedidoDeBloqueo = Esquemas['BloqueoRequest']
export type BloqueosCreados = Esquemas['BloqueosCreados']
