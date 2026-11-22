import { expect, test } from '@playwright/test'

import { celularNuevo, completarYConfirmar, hastaLasHoras, reservar, visitante } from './ayudas.ts'

test('reservar un turno de punta a punta, y seguir viéndolo al recargar', async ({ browser }) => {
  const pagina = await visitante(browser)

  const hora = await reservar(pagina, celularNuevo())

  await expect(pagina.getByText(new RegExp(`, ${hora}$`))).toBeVisible()
  await pagina.reload()
  await expect(pagina.getByRole('heading', { name: 'Tu turno' })).toBeVisible()
  await expect(pagina.getByText(new RegExp(`, ${hora}$`))).toBeVisible()
})

test('dos personas eligen la misma hora: la segunda se entera de que se ocupó', async ({ browser }) => {
  const primera = await visitante(browser)
  const segunda = await visitante(browser)
  const hora = await hastaLasHoras(primera)
  await hastaLasHoras(segunda)
  await segunda.getByRole('button', { name: hora, exact: true }).click()

  // La primera la aparta (pasa a sus datos) y la segunda llega tarde.
  await primera.getByRole('button', { name: hora, exact: true }).click()
  await primera.getByRole('button', { name: 'Siguiente', exact: true }).click()
  await expect(primera.getByRole('heading', { name: 'Tus datos' })).toBeVisible()
  await segunda.getByRole('button', { name: 'Siguiente', exact: true }).click()

  await expect(segunda.getByRole('alert')).toHaveText('Ese horario se acaba de ocupar, elegí otro.')
  // La primera puede confirmar igual.
  await completarYConfirmar(primera, celularNuevo())
})
