// Deriva las variables CSS del acento a partir del color que eligió el local (diseño de Claude Design, accent.js).
// Cualquier color tiene que leerse bien: si es muy claro (amarillo), el texto encima pasa a oscuro
// y el color para texto se oscurece hasta llegar a contraste WCAG AA (4,5:1).

type Rgb = [number, number, number]

const ACENTO_SLOTLY = '#1f2937'
const BLANCO: Rgb = [255, 255, 255]
const NEGRO: Rgb = [0, 0, 0]
const TINTA: Rgb = [17, 24, 39] // #111827, el texto principal

function aRgb(hex: string): Rgb {
  let h = hex.trim().replace('#', '')
  if (h.length === 3) {
    h = h.split('').map((c) => c + c).join('')
  }
  if (!/^[0-9a-fA-F]{6}$/.test(h)) {
    h = ACENTO_SLOTLY.slice(1)
  }
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)) as Rgb
}

function aHex(rgb: Rgb): string {
  return '#' + rgb.map((v) => v.toString(16).padStart(2, '0')).join('')
}

// Mezcla a con b en proporción t (0 = todo a, 1 = todo b).
function mezclar(a: Rgb, b: Rgb, t: number): Rgb {
  return a.map((v, i) => Math.round(v * (1 - t) + b[i] * t)) as Rgb
}

// Luminancia relativa según WCAG 2.x.
function luminancia(rgb: Rgb): number {
  const pesos = [0.2126, 0.7152, 0.0722]
  return rgb.reduce((suma, v, i) => {
    const c = v / 255
    const lineal = c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
    return suma + pesos[i] * lineal
  }, 0)
}

function contraste(a: Rgb, b: Rgb): number {
  const la = luminancia(a)
  const lb = luminancia(b)
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}

// Sin color elegido → el gris de Slotly.
export function variablesDeAcento(hex: string | null): Record<string, string> {
  const acento = aRgb(hex ?? ACENTO_SLOTLY)

  // Texto encima del acento: blanco si llega a 4,5:1; si no, el que contraste más.
  const textoEncima = contraste(acento, BLANCO) >= 4.5
    ? BLANCO
    : contraste(acento, TINTA) >= contraste(acento, BLANCO) ? TINTA : BLANCO

  // Fondo suave: 90 % blanco.
  const tinte = mezclar(acento, BLANCO, 0.9)

  // Acento para texto: se oscurece de a 2 % hasta llegar a 4,5:1 sobre blanco y sobre el tinte.
  let tinta = acento
  let t = 0
  while ((contraste(tinta, BLANCO) < 4.5 || contraste(tinta, tinte) < 4.5) && t < 1) {
    t += 0.02
    tinta = mezclar(acento, NEGRO, t)
  }

  return {
    '--accent': aHex(acento),
    '--accent-contrast': aHex(textoEncima),
    '--accent-hover': aHex(mezclar(acento, NEGRO, 0.12)),
    '--accent-ink': aHex(tinta),
    '--accent-tint': aHex(tinte),
    // Borde extra cuando el acento se pierde sobre blanco (menos de 3:1).
    '--accent-edge': contraste(acento, BLANCO) < 3 ? 'rgba(17, 24, 39, .18)' : aHex(acento),
  }
}
