import { defineConfig, devices } from '@playwright/test'

// End-to-end (doc 06): los flujos críticos con todo levantado, en un navegador de verdad. Corren contra la imagen de
// producción (docker-compose.e2e.yml en la raíz), con los datos de demo, en el subdominio del local demo como en
// desarrollo: http://demo.localhost:8090.
export default defineConfig({
  testDir: './e2e',
  // De a uno: todos reservan sobre la misma agenda de demo. Cada test igual arma sus propios datos (su celular, su IP).
  workers: 1,
  fullyParallel: false,
  // Sin reintentos: un test que a veces falla es un test para arreglar, no para repetir.
  retries: 0,
  forbidOnly: !!process.env.CI,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_URL ?? 'http://demo.localhost:8090',
    // Si falla, queda la traza para ver qué pasó paso a paso (npx playwright show-trace).
    trace: 'retain-on-failure',
    locale: 'es-AR',
    timezoneId: 'America/Argentina/Buenos_Aires',
  },
  // Mobile first: casi todos reservan desde el celular.
  projects: [{ name: 'celular', use: { ...devices['Pixel 7'] } }],
})
