import { createContext, useContext } from 'react'

import type { UsuarioPanel } from '../api/tipos'

// Lo que las páginas del panel saben de la sesión: quién entró y qué hacer si la API dice que ya no hay sesión
// (se cerró en otra pestaña o venció: HU-16 CA6 y CA7).
export type SesionDelPanel = {
  usuario: UsuarioPanel
  sesionPerdida: () => void
}

export const ContextoSesion = createContext<SesionDelPanel | null>(null)

export function useSesion(): SesionDelPanel {
  const sesion = useContext(ContextoSesion)
  if (sesion === null) {
    throw new Error('useSesion se usa solo dentro de PanelConSesion')
  }
  return sesion
}

// La página por defecto del panel (doc 00: una sola agenda para el profesional y el admin).
export const INICIO_DEL_PANEL = '/agenda'

// El link al login que, después de entrar, vuelve a esta página (CA6).
export function linkIngresar(volver: string): string {
  return `/ingresar?${new URLSearchParams({ volver })}`
}

// A dónde ir después de entrar. Solo direcciones de este mismo sitio: un link armado por otro con
// ?volver=https://otro-sitio no puede usar el login para mandar a alguien afuera.
export function destinoDespuesDeIngresar(volver: string | null): string {
  if (volver === null || !volver.startsWith('/') || volver.startsWith('//') || volver.startsWith('/ingresar')) {
    return INICIO_DEL_PANEL
  }
  return volver
}
