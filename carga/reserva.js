// Prueba de carga (Etapa 9) con k6, contra la imagen de producción con los datos de demo (docker-compose.e2e.yml).
// Se corre a mano, no en el CI: ver el README ("Prueba de carga").
//
// Dos escenarios:
// - misma_hora: 50 personas apartan exactamente la misma hora al mismo tiempo. Tiene que salir 1 sola bien y las
//   otras 49 con HORARIO_OCUPADO: ni dos turnos en el mismo horario ni errores 500 (el constraint de la base, D6).
// - uso_normal: durante 1 minuto, hasta 20 personas a la vez miran días y horas, apartan y confirman. Mide cuánto
//   tarda la API. Los umbrales son para esta máquina con Docker, no para el plan gratis de Render.
import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter } from 'k6/metrics'

const BASE = __ENV.BASE ?? 'http://app:8080'
// Los ids fijos de los datos de demo: "Barba" la hace solo Bruno.
const BARBA = '20000000-0000-0000-0000-000000000002'
const BRUNO = '30000000-0000-0000-0000-000000000002'

const apartadosMismaHora = new Counter('apartados_misma_hora')
const ocupadosMismaHora = new Counter('ocupados_misma_hora')
const errores5xx = new Counter('errores_5xx')

// Un 409 (la hora se ocupó) es una respuesta esperada, no una falla.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409))

export const options = {
  scenarios: {
    misma_hora: {
      executor: 'per-vu-iterations',
      exec: 'mismaHora',
      vus: 50,
      iterations: 1,
    },
    uso_normal: {
      executor: 'ramping-vus',
      exec: 'usoNormal',
      startTime: '5s',
      stages: [
        { duration: '15s', target: 20 },
        { duration: '30s', target: 20 },
        { duration: '15s', target: 0 },
      ],
    },
  },
  thresholds: {
    apartados_misma_hora: ['count==1'],
    ocupados_misma_hora: ['count==49'],
    errores_5xx: ['count==0'],
    http_req_failed: ['rate==0'],
    'http_req_duration{tipo:lectura}': ['p(95)<500'],
    'http_req_duration{tipo:reserva}': ['p(95)<1000'],
  },
}

// Cada pedido como un visitante distinto: el límite de 10 reservas por minuto es por IP (HU-15), y el proxy la manda
// en X-Forwarded-For, como hace Render.
function cabeceras(tipo) {
  const ip = `11.${rand(254)}.${rand(254)}.${rand(254)}`
  return {
    headers: { Host: 'demo.localhost', 'Content-Type': 'application/json', 'X-Forwarded-For': ip },
    tags: { tipo },
  }
}

function rand(n) {
  return 1 + Math.floor(Math.random() * n)
}

function contar5xx(respuesta) {
  if (respuesta.status >= 500) {
    errores5xx.add(1)
  }
}

// Antes de empezar: las horas libres de Bruno en los próximos días con lugar. La primera queda para misma_hora; el
// resto, para uso_normal.
export function setup() {
  const dias = http.get(`${BASE}/api/publico/disponibilidad/dias?servicios=${BARBA}&profesional=${BRUNO}`, cabeceras('lectura'))
  const conLugar = dias.json('dias').filter((d) => d.hayLugar).slice(0, 5)
  const horarios = []
  for (const dia of conLugar) {
    const horas = http.get(
      `${BASE}/api/publico/disponibilidad/horas?servicios=${BARBA}&profesional=${BRUNO}&fecha=${dia.fecha}`,
      cabeceras('lectura'))
    for (const hora of horas.json('horas')) {
      horarios.push({ fecha: dia.fecha, hora })
    }
  }
  if (horarios.length < 10) {
    throw new Error(`Pocas horas libres para probar (${horarios.length}): levantar la base de nuevo con down -v.`)
  }
  return { disputada: horarios[0], libres: horarios.slice(1) }
}

function apartar(horario) {
  const cuerpo = JSON.stringify({ servicios: [BARBA], profesional: BRUNO, fecha: horario.fecha, hora: horario.hora })
  return http.post(`${BASE}/api/publico/apartados`, cuerpo, cabeceras('reserva'))
}

export function mismaHora(datos) {
  const respuesta = apartar(datos.disputada)
  contar5xx(respuesta)
  if (respuesta.status === 201) {
    apartadosMismaHora.add(1)
  } else if (respuesta.status === 409 && respuesta.json('codigo') === 'HORARIO_OCUPADO') {
    ocupadosMismaHora.add(1)
  }
}

export function usoNormal(datos) {
  const dias = http.get(`${BASE}/api/publico/disponibilidad/dias?servicios=${BARBA}&profesional=${BRUNO}`, cabeceras('lectura'))
  contar5xx(dias)
  const horario = datos.libres[Math.floor(Math.random() * datos.libres.length)]
  const horas = http.get(
    `${BASE}/api/publico/disponibilidad/horas?servicios=${BARBA}&profesional=${BRUNO}&fecha=${horario.fecha}`,
    cabeceras('lectura'))
  contar5xx(horas)
  check(horas, { 'horas: 200': (r) => r.status === 200 })
  sleep(1)

  const apartado = apartar(horario)
  contar5xx(apartado)
  // 201 o, si otro la agarró antes, 409: las dos están bien.
  check(apartado, { 'apartar: 201 o 409': (r) => r.status === 201 || r.status === 409 })
  if (apartado.status !== 201) {
    return
  }
  sleep(1)
  const confirmado = http.post(`${BASE}/api/publico/apartados/${apartado.json('id')}/confirmar`, JSON.stringify({
    token: apartado.json('token'),
    nombre: 'Prueba de carga',
    telefono: `11 4${String(rand(999)).padStart(3, '0')}-${String(rand(9999)).padStart(4, '0')}`,
    email: null,
    formaPago: 'efectivo',
    consentimiento: true,
  }), cabeceras('reserva'))
  contar5xx(confirmado)
  check(confirmado, { 'confirmar: 200': (r) => r.status === 200 })
}
