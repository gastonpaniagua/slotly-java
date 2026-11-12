// Formatos para mostrar: el back manda números y el front decide cómo se leen (HU-01 CA1).

const miles = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 })

// 8000 → "$ 8.000"
export function formatearPrecio(pesos: number): string {
  return `$ ${miles.format(pesos)}`
}

// 45 → "45 min", 60 → "1 h", 75 → "1 h 15 min"
export function formatearDuracion(minutos: number): string {
  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  if (horas === 0) {
    return `${resto} min`
  }
  if (resto === 0) {
    return `${horas} h`
  }
  return `${horas} h ${resto} min`
}

// Link para escribirle al local por WhatsApp. El número viene en formato internacional (+5493534000000);
// wa.me lo quiere solo con dígitos.
export function linkDeWhatsApp(numero: string): string {
  return `https://wa.me/${numero.replace(/\D/g, '')}`
}
