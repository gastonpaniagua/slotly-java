# Slotly · Contexto y decisiones

Punto de entrada del proyecto. Resume qué es Slotly, qué se decidió y por qué, y qué pasó en el primer intento. Los detalles están en los docs 01 a 05.

## 1. Qué es Slotly

SaaS de reservas online para peluquerías y barberías de Argentina. Se vende a varios locales (multi-tenant): cada local tiene su web en un subdominio (`barberiajuan.slotly.com.ar`) con su marca, servicios, profesionales y reglas. El cliente final reserva desde el celular sin crear cuenta; el profesional ve su agenda y recibe el aviso en el panel; el dueño administra todo.

Es el proyecto de portfolio de Gaston, desarrollador junior. El objetivo principal es **aprender**: programar, la estructura de un sistema, seguridad y testing. Cada pieza tiene que poder explicarla en una entrevista.

| Doc                            | Qué tiene                                                               |
| ------------------------------ | ----------------------------------------------------------------------- |
| `01-definicion-producto.md`    | Funciones, pantallas, reglas de turnos, casos borde, qué queda afuera   |
| `02-integraciones-y-costos.md` | WhatsApp, reseñas, mapa, hosting y costo mensual por local              |
| `03-modelo-de-datos.md`        | Tablas, relaciones y reglas de la base                                  |
| `04-flujo-de-reserva.md`       | Diagrama del flujo de reserva                                           |
| `05-historias-de-usuario.md`   | Backlog aprobado: 39 historias con criterios Dado/Cuando/Entonces       |

## 2. Historia del proyecto

- **2026-10-02:** se definieron el producto (doc 01) y los costos (doc 02), y se armó un monorepo TypeScript (NestJS + Prisma + Next.js).
- **2026-10-04:** Gaston escribió el modelo (doc 03) y el flujo de reserva (doc 04).
- **2026-10-05:** Gaston escribió el prompt de método. Etapa 0: auditoría, decisiones D1 a D11 (aprobadas) y backlog (aprobado). Claude hizo la Etapa 1 completa por su cuenta y Gaston frenó: no estaba aprendiendo.
- **2026-10-06:** reinicio en **modo aprendizaje**: Gaston escribe el código, Claude guía con pasos chicos y comprueba que entendió.
- **2026-10-09/10:** Gaston escribió su propio schema y migraciones. Al revisarlo apareció que había perdido el aislamiento entre locales (sin claves compuestas) y que se había apartado del modelo acordado (sin bloqueos, apartado ni reglas del local). Lección: el modelo acordado está en el doc 03 y hay que comparar contra él.
- **2026-10-10:** Gaston reinicia desde cero con **Java + Spring Boot + React**, con el mismo método. Se conservan todas las decisiones de producto y de modelo; se revisan solo las que dependen del stack.
- **2026-10-10 (Etapa 0):** se cierran D1 (sesión con Spring Session JDBC), D4 (tests con Testcontainers) y el formato de error (`ProblemDetail` con `codigo`). D2 queda para la Etapa 8.

## 3. Decisiones de producto (no se reabren sin una razón fuerte)

1. **Turnos que nunca se pisan:** lo garantiza PostgreSQL con un constraint de exclusión (`btree_gist`), no el código. Turnos manuales, bloqueos, vacaciones y apartados pasan por la misma regla.
2. **Fechas en UTC**; horarios de trabajo en hora del local, con una zona horaria fija por local. Todo se muestra en hora del local.
3. **Cliente sin contraseña:** reserva con nombre y teléfono (mail opcional) y consentimiento. Gestiona su turno desde un link con token. Si vuelve desde otro dispositivo, entra con teléfono + código. Máximo 2 turnos futuros por teléfono (configurable por local).
4. **Panel con usuarios propios:** admin (dueño, ve todo) y profesional (solo su agenda).
5. **Precio y duración se copian al turno** al reservar. **Los horarios libres se calculan en el momento** desde la agenda real, nunca de una lista guardada.
6. **Apartado:** al llegar al paso de datos, el horario queda reservado unos minutos (5 por defecto) y se libera solo si no confirma.
7. **Aviso al profesional por notificación del panel** (no WhatsApp) en la v1. (2026-10-05)
8. **Sin código en la primera reserva.** (2026-10-05)
9. **WhatsApp (número único de Slotly) y Mercado Pago** están previstos pero fuera del núcleo de la v1. Los mensajes salen por un adaptador simulado; el real queda preparado y apagado.
10. **Fuera de la v1:** todo lo de "Para después" del doc 01 y la tabla PD-01 a PD-18 del doc 05.
11. **Multi-tenant por subdominio** desde el primer día; en desarrollo con `*.localhost` (por ejemplo `demo.localhost:5173`).
12. **Mobile first:** casi todos reservan desde el celular entrando desde Instagram.

## 4. Decisiones técnicas del primer intento y cómo pasan a Java

Las D1 a D11 las aprobó Gaston el 2026-10-05 para el stack TypeScript. La idea se mantiene; la herramienta cambia. Las marcadas **(revisar)** se confirman en la Etapa 0 del nuevo proyecto.

| ID  | Decisión original                                                               | En Java + Spring Boot + React                                                                                                                                                                                                                                                  |
| --- | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| D1  | Login del panel propio, argon2id, sesiones opacas en Postgres con cookie httpOnly | Igual, con Spring Security: `Argon2PasswordEncoder`, sesión del lado del servidor guardada en Postgres con Spring Session JDBC, cookie `HttpOnly`, `Secure`, `SameSite=Lax`, y protección CSRF. Por qué no JWT: no se puede invalidar antes de que venza (HU-16 CA7). Las tablas de Spring Session se crean con una migración de Flyway en la Etapa 5. **(confirmada 2026-10-10)** |
| D2  | Cola de trabajos con pg-boss sobre Postgres                                     | Sin Redis. Opciones: `@Scheduled` + tabla con `SELECT … FOR UPDATE SKIP LOCKED`, o una librería sobre Postgres (db-scheduler, JobRunr). Se decide en la Etapa 8. **(pendiente, Etapa 8)**                                                                                                 |
| D3  | Aislamiento: filtro por `local_id` en la aplicación + claves foráneas compuestas; RLS después | Igual. Las claves compuestas se escriben en las migraciones SQL. El local sale del subdominio en cada pedido y toda consulta filtra por él. Row Level Security queda como evolución.                                                                                       |
| D4  | Tests contra Postgres real: base `slotly_test` en el Docker, migraciones al arrancar, `TRUNCATE` por test | Postgres real con **Testcontainers** (Postgres 17, misma imagen que el docker-compose, conectado con `@ServiceConnection` y un solo contenedor reutilizado para todos los tests); Flyway migra al arrancar; limpieza con `TRUNCATE` por test, no con rollback (el rollback rompe el test de concurrencia). Por qué: cada corrida arranca con la base limpia, sin datos de desarrollo, e igual en local que en CI. **(confirmada 2026-10-10)**                     |
| D5  | El navegador llama a la API por el mismo origen (proxy de Next) para que la cookie quede atada al subdominio | Igual, con el proxy del servidor de Vite en desarrollo (`/api` → Spring Boot, conservando el host original) y un reverse proxy en producción. La API resuelve el local por el host.                                                                                       |
| D6  | Bloqueos y apartados como filas de `turno` (`tipo` y `estado`)                  | Igual (ver doc 03). Es SQL, no depende del stack.                                                                                                                                                                                                                              |
| D7  | Aviso al profesional por notificación del panel                                 | Igual.                                                                                                                                                                                                                                                                         |
| D8  | Link del turno sin WhatsApp real: se muestra en la confirmación y el navegador lo recuerda | Igual.                                                                                                                                                                                                                                                                  |
| D9  | Cómo recibe Gaston los cambios                                                  | Reemplazada por el modo aprendizaje: Gaston escribe y commitea; Claude guía y revisa.                                                                                                                                                                                         |
| D10 | Imágenes: se decide en la Etapa 7 (recomendado: en Postgres, hasta 500 KB)       | Igual.                                                                                                                                                                                                                                                                         |
| D11 | Mapa: OpenStreetMap embebido + link "Cómo llegar" a Google Maps                 | Igual.                                                                                                                                                                                                                                                                         |

Decisiones chicas que se mantienen:

- Teléfonos normalizados a formato internacional antes de guardarlos (en Java, `libphonenumber` de Google).
- El horario de atención de la home se calcula juntando los horarios de los profesionales activos (una sola fuente de verdad).
- Errores de la API con `ProblemDetail` (RFC 9457, `application/problem+json`) más un campo `codigo` propio (`HORARIO_OCUPADO`, `LOCAL_NO_ENCONTRADO`, `VALIDACION`); los errores de validación suman `errores` con el detalle por campo. El front decide qué mostrar por `codigo`, nunca por el texto. **(confirmada 2026-10-10)**
- Login del panel en `/ingresar`; panel del admin en `/admin` y del profesional en `/agenda`.
- El pedido de reseña (HU-38) es del rol admin: el beneficio es del local.

## 5. Stack elegido (2026-10-10)

- **Back:** Java 21, Spring Boot 4.x, Maven, Spring Web MVC, Spring Data JPA, Spring Security, Bean Validation, Flyway, springdoc-openapi.
- **Base:** PostgreSQL 17 con `btree_gist`, en Docker.
- **Front:** React + Vite + TypeScript, React Router, Tailwind.
- **Tests:** JUnit 5, AssertJ, MockMvc, Postgres real con Testcontainers, JaCoCo; Vitest + Testing Library; Playwright.
- **Repo:** uno solo, con `backend/` y `frontend/`.

## 6. Experiencia de Gaston (para calibrar)

- Hizo un proyecto anterior "vibecodeando", casi sin leer el código. Por eso este se hace distinto.
- Testing: algo de pruebas de API a mano con Insomnia. Quiere usar Playwright para los end-to-end.
- Trabaja en Windows con PowerShell.
- El nivel en Java y Spring se pregunta en la Etapa 0.
