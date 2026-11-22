import { expect, type Browser, type Page } from '@playwright/test'

// Ayudas de los end-to-end. Cada test es un visitante nuevo: su propio navegador, su celular y su IP, así ningún
// test depende de lo que dejó otro.

// Un número al azar entre 1 y n.
function alAzar(n: number): number {
  return 1 + Math.floor(Math.random() * n)
}

// Un celular argentino que no usó nadie: 11 4xxx-xxxx.
export function celularNuevo(): string {
  return `11 4${String(alAzar(999)).padStart(3, '0')}-${String(alAzar(9999)).padStart(4, '0')}`
}

// Un visitante con su propia IP: el proxy la manda en X-Forwarded-For, como hace Render, y la app cuenta el límite de
// pedidos por IP (HU-15). Sin esto, todos los tests compartirían el límite de 10 por minuto.
export async function visitante(browser: Browser): Promise<Page> {
  const ip = `11.${alAzar(254)}.${alAzar(254)}.${alAzar(254)}`
  const contexto = await browser.newContext({ extraHTTPHeaders: { 'X-Forwarded-For': ip } })
  return contexto.newPage()
}

// Desde la home hasta la lista de horas: "Barba" la hace solo Bruno, así el paso del profesional se saltea (HU-06
// CA3), y el primer día con lugar ya viene elegido. Devuelve la primera hora libre.
export async function hastaLasHoras(pagina: Page): Promise<string> {
  await pagina.goto('/')
  await pagina.getByRole('link', { name: 'Reservar turno' }).first().click()
  await pagina.getByRole('checkbox', { name: /Barba/ }).first().check()
  await pagina.getByRole('button', { name: 'Siguiente', exact: true }).click()
  await expect(pagina.getByRole('heading', { name: 'Elegí el día' })).toBeVisible()
  await pagina.getByRole('button', { name: 'Siguiente', exact: true }).click()
  await expect(pagina.getByRole('heading', { name: 'Elegí la hora' })).toBeVisible()
  const primera = pagina.getByRole('group', { name: 'Horas libres' }).getByRole('button').first()
  return (await primera.innerText()).trim()
}

// Completa los datos y confirma. Termina en la página del turno recién reservado.
export async function completarYConfirmar(pagina: Page, celular: string): Promise<void> {
  await expect(pagina.getByRole('heading', { name: 'Tus datos' })).toBeVisible()
  await pagina.getByLabel('Nombre').fill('Prueba E2E')
  await pagina.getByLabel('Celular').fill(celular)
  await pagina.getByRole('radio', { name: 'Efectivo, en el local' }).check()
  await pagina.getByRole('checkbox', { name: /política de privacidad/ }).check()
  await pagina.getByRole('button', { name: 'Confirmar', exact: true }).click()
  await expect(pagina.getByRole('heading', { name: '¡Listo! Tu turno quedó reservado' })).toBeVisible()
}

// Una reserva completa con la primera hora libre. Devuelve la hora reservada.
export async function reservar(pagina: Page, celular: string): Promise<string> {
  const hora = await hastaLasHoras(pagina)
  await pagina.getByRole('button', { name: hora, exact: true }).click()
  await pagina.getByRole('button', { name: 'Siguiente', exact: true }).click()
  await completarYConfirmar(pagina, celular)
  return hora
}
