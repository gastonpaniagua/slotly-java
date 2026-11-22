import { expect, test } from '@playwright/test'

import { visitante } from './ayudas.ts'

// Los usuarios del panel son los de los datos de demo (README).
test('login del panel: con la contraseña mal no entra; bien, ve la agenda y la sesión sigue al recargar', async ({ browser }) => {
  const pagina = await visitante(browser)

  await pagina.goto('/ingresar')
  await pagina.getByLabel('Mail').fill('bruno@demo.com')
  await pagina.getByLabel('Contraseña').fill('no-es-esta')
  await pagina.getByRole('button', { name: 'Ingresar', exact: true }).click()
  await expect(pagina.getByRole('alert')).toBeVisible()
  await expect(pagina).toHaveURL(/\/ingresar$/)

  await pagina.getByLabel('Contraseña').fill('demo1234')
  await pagina.getByRole('button', { name: 'Ingresar', exact: true }).click()

  await expect(pagina.getByRole('heading', { name: 'Agenda' })).toBeVisible()
  await expect(pagina.getByText('bruno@demo.com')).toBeVisible()
  // La sesión vive en la cookie (D1): recargar no la pierde.
  await pagina.reload()
  await expect(pagina.getByRole('heading', { name: 'Agenda' })).toBeVisible()
})
