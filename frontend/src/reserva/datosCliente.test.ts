import { describe, expect, it } from 'vitest'

import { DATOS_VACIOS, validarDatos, type DatosCliente } from './datosCliente'

const VALIDOS: DatosCliente = {
  nombre: 'Lucía',
  telefono: '11 2345-6789',
  email: '',
  formaPago: 'efectivo',
  consentimiento: true,
}

describe('validarDatos', () => {
  it('datos completos y válidos no tienen errores (el mail es opcional)', () => {
    expect(validarDatos(VALIDOS)).toEqual({})
  })

  it('HU-10 CA4: sin completar marca nombre, celular, forma de pago y consentimiento', () => {
    expect(Object.keys(validarDatos(DATOS_VACIOS)).sort())
      .toEqual(['consentimiento', 'formaPago', 'nombre', 'telefono'])
  })

  it('HU-10 CA4: un nombre de más de 80 caracteres', () => {
    expect(validarDatos({ ...VALIDOS, nombre: 'a'.repeat(81) }).nombre).toBe('El nombre puede tener hasta 80 caracteres.')
    expect(validarDatos({ ...VALIDOS, nombre: '   ' }).nombre).toBe('Ingresá tu nombre.')
  })

  it.each(['123', '11 2345', 'once 2345 6789'])('HU-10 CA4: "%s" no es un celular válido', (telefono) => {
    expect(validarDatos({ ...VALIDOS, telefono }).telefono).toBe('Ingresá un celular válido, por ejemplo 11 2345-6789.')
  })

  it.each(['11 2345-6789', '+54 9 11 2345 6789', '(0353) 15-400-0000'])('"%s" pasa (lo termina de validar el back)', (telefono) => {
    expect(validarDatos({ ...VALIDOS, telefono }).telefono).toBeUndefined()
  })

  it('HU-10 CA4: un mail mal escrito', () => {
    expect(validarDatos({ ...VALIDOS, email: 'ana@' }).email).toBe('Revisá el mail.')
    expect(validarDatos({ ...VALIDOS, email: 'ana@mail.com' }).email).toBeUndefined()
  })

  it('HU-10 CA2: sin aceptar la política de privacidad no se puede confirmar', () => {
    expect(validarDatos({ ...VALIDOS, consentimiento: false }).consentimiento)
      .toBe('Tenés que aceptar la política de privacidad.')
  })
})
