import { expect, test } from '@playwright/test'

import { celularNuevo, reservar, visitante } from './ayudas.ts'

test('Mi turno: el cliente cancela su turno y queda cancelado', async ({ browser }) => {
  const pagina = await visitante(browser)
  await reservar(pagina, celularNuevo())

  await pagina.getByRole('button', { name: 'Cancelar turno', exact: true }).click()
  await pagina.getByRole('button', { name: 'Sí, cancelar', exact: true }).click()

  await expect(pagina.getByText('Cancelaste tu turno. El horario quedó libre para otra persona.')).toBeVisible()
  await pagina.reload()
  await expect(pagina.getByText('Este turno está cancelado.')).toBeVisible()
})

test('Mis turnos desde otro navegador: entra con el código que muestra la demo', async ({ browser }) => {
  const celular = celularNuevo()
  const hora = await reservar(await visitante(browser), celular)
  // Otro navegador, sin nada guardado: como entrar desde la computadora.
  const otro = await visitante(browser)

  await otro.goto('/entrar')
  await otro.getByLabel('Celular').fill(celular)
  await otro.getByRole('button', { name: 'Pedir código', exact: true }).click()
  const aviso = otro.getByRole('note')
  await expect(aviso).toContainText('Es una demo: tu código es')
  const codigo = (await aviso.locator('strong').innerText()).trim()
  await otro.getByLabel('Código').fill(codigo)
  await otro.getByRole('button', { name: 'Entrar', exact: true }).click()

  await expect(otro.getByRole('heading', { name: 'Mis turnos' })).toBeVisible()
  await expect(otro.getByRole('link', { name: new RegExp(`, ${hora}`) })).toBeVisible()
})
