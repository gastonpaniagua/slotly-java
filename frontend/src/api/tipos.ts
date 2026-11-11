// Nombres cortos para los tipos que genera openapi-typescript desde el back (npm run tipos).
// Si el back cambia una respuesta, se regenera esquema.ts y el compilador marca qué adaptar.
import type { components } from './esquema'

type Esquemas = components['schemas']

export type LocalPublico = Esquemas['LocalPublicoResponse']
export type ServicioPublico = Esquemas['ServicioPublicoResponse']
export type DiaPublico = Esquemas['DiaPublicoResponse']
export type FranjaPublica = Esquemas['FranjaPublicaResponse']
export type ProfesionalPublico = Esquemas['ProfesionalPublicoResponse']
