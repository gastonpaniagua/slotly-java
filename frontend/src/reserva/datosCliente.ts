// Lo que el cliente escribe en el paso 5. Vive en Reserva: si tiene que volver a elegir hora, no lo pierde (HU-12 CA2).
export type DatosCliente = {
  nombre: string
  telefono: string
  email: string
  formaPago: 'efectivo' | 'transferencia' | ''
  consentimiento: boolean
}

export const DATOS_VACIOS: DatosCliente = {
  nombre: '',
  telefono: '',
  email: '',
  formaPago: '',
  consentimiento: false,
}

export type ErroresDeDatos = Partial<Record<keyof DatosCliente, string>>

// La misma regla que la base para el mail (doc 03).
const MAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

// Validación en el navegador, para avisar antes de mandar (HU-10 CA4). La API vuelve a validar todo:
// el celular, de verdad, lo valida el back con libphonenumber; acá solo se frena lo que seguro está mal.
export function validarDatos(datos: DatosCliente): ErroresDeDatos {
  const errores: ErroresDeDatos = {}
  const nombre = datos.nombre.trim()
  if (nombre === '') {
    errores.nombre = 'Ingresá tu nombre.'
  } else if (nombre.length > 80) {
    errores.nombre = 'El nombre puede tener hasta 80 caracteres.'
  }
  const digitos = datos.telefono.replace(/\D/g, '')
  if (datos.telefono.trim() === '') {
    errores.telefono = 'Ingresá tu celular.'
  } else if (digitos.length < 8 || /[^\d\s()+-]/.test(datos.telefono)) {
    errores.telefono = 'Ingresá un celular válido, por ejemplo 11 2345-6789.'
  }
  if (datos.email.trim() !== '' && !MAIL.test(datos.email.trim())) {
    errores.email = 'Revisá el mail.'
  }
  if (datos.formaPago === '') {
    errores.formaPago = 'Elegí cómo vas a pagar.'
  }
  if (!datos.consentimiento) {
    // HU-10 CA2
    errores.consentimiento = 'Tenés que aceptar la política de privacidad.'
  }
  return errores
}
