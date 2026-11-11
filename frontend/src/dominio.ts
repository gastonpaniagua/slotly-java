// Dominio sobre el que cuelgan los locales: en desarrollo "localhost" (demo.localhost),
// en producción se define al compilar con VITE_DOMINIO_BASE=slotly.com.ar. Es el mismo valor que slotly.dominio-base del back.
export const DOMINIO_BASE = import.meta.env.VITE_DOMINIO_BASE ?? 'localhost'

// true si se entró sin subdominio de local (localhost, slotly.com.ar o www.slotly.com.ar): ahí va la página de Slotly (HU-03 CA5).
export function esDominioRaiz(host: string, dominioBase: string = DOMINIO_BASE): boolean {
  const h = host.toLowerCase()
  const base = dominioBase.toLowerCase()
  return h === base || h === `www.${base}`
}
