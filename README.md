# Slotly

SaaS de reservas online para peluquerías y barberías. Multi-tenant: cada local tiene su web en un subdominio con su marca, servicios, profesionales y reglas. El cliente final reserva desde el celular sin crear cuenta; el profesional ve su agenda y recibe el aviso en el panel; el dueño administra todo.

Proyecto de portfolio. El detalle del producto y las decisiones están en [`docs/`](docs/).

## Stack

- **Back:** Java 21, Spring Boot 4.1, Maven, Spring Web MVC, Spring Data JPA, Spring Security, Bean Validation, Flyway, springdoc-openapi.
- **Base:** PostgreSQL 17 con `btree_gist`, en Docker.
- **Front:** React + Vite + TypeScript, React Router, Tailwind.
- **Tests:** JUnit 5, AssertJ, MockMvc, Postgres real con Testcontainers, JaCoCo; Vitest + Testing Library; Playwright.
- **Repo:** uno solo, con `backend/` y `frontend/`.

Algunas piezas se incorporan por etapas (ver [`docs/05-historias-de-usuario.md`](docs/05-historias-de-usuario.md)).

## Requisitos

- **JDK 21** (por ejemplo Eclipse Temurin), con `JAVA_HOME` apuntando a él
- **Node 24**
- **Docker Desktop** con Docker Compose

## Cómo levantarlo

1. Copiar la plantilla de variables y poner una contraseña propia en `POSTGRES_PASSWORD` (en la raíz del repo):
   ```powershell
   Copy-Item .env.example .env
   ```
2. Levantar la base (en la raíz del repo):
   ```powershell
   docker compose up -d
   ```
3. Levantar la API (en `backend/`, en su propia terminal; esperar a ver `Started SlotlyApplication`):
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
4. Levantar el front (en `frontend/`, en otra terminal; la primera vez, antes, `npm install`):
   ```powershell
   npm run dev
   ```
5. Abrir http://demo.localhost:5173

> El orden importa: si la base no está arriba, la API no arranca.

**Usuarios de la demo** (panel en http://demo.localhost:5173/ingresar; contraseña de juguete, solo existe en los datos de demo):

| Mail | Contraseña | Rol |
| --- | --- | --- |
| `admin@demo.com` | `demo1234` | admin (y profesional: Ana) |
| `bruno@demo.com` | `demo1234` | profesional (Bruno) |

Para apagar: `Ctrl+C` en las terminales del front y de la API, y `docker compose stop` en la raíz (no borra los datos).

## Tests

Back (en `backend/`; necesita Docker corriendo, porque Testcontainers levanta un Postgres 17 temporal):
```powershell
.\mvnw.cmd test
```

Front (en `frontend/`):
```powershell
npm run lint
npm run build
```

## Desplegar la demo (gratis: Render + Neon)

La demo publicada es una sola app: la imagen del [`Dockerfile`](Dockerfile) trae la API y el front compilado, así el navegador ve un solo dominio (sin CORS y con la cookie de sesión del mismo sitio). La base va en Neon, que no vence ni se pausa por días sin uso. No hace falta tarjeta en ninguna de las dos.

**1. Base en Neon** ([neon.com](https://neon.com))

1. Crear la cuenta y un proyecto: nombre `slotly`, Postgres 17, región **AWS US East 1 (N. Virginia)**, cerca de Render.
2. En "Connect", **apagar "Connection pooling"** (Flyway necesita la conexión directa: el host no tiene que terminar en `-pooler`) y anotar el host, la base, el usuario y la contraseña.

**2. App en Render** ([render.com](https://render.com))

1. Entrar con GitHub → "New" → "Web Service" → elegir este repo. Render detecta el `Dockerfile`.
2. Región **Virginia (US East)** e instancia **Free**.
3. Variables de entorno (las de la base, de Neon):

   | Variable | Valor |
   | --- | --- |
   | `SPRING_PROFILES_ACTIVE` | `prod,demo` |
   | `POSTGRES_HOST` | el host de Neon, por ejemplo `ep-algo-123.us-east-1.aws.neon.tech` |
   | `POSTGRES_DB` | la base de Neon (por ejemplo `neondb`) |
   | `POSTGRES_USER` | el usuario de Neon |
   | `POSTGRES_PASSWORD` | la contraseña de Neon |
   | `SLOTLY_DOMINIO_BASE` | `onrender.com` |
   | `SLOTLY_URL_SITIO` | la dirección que da Render, por ejemplo `https://slotly-demo.onrender.com` |
   | `SLOTLY_LOCAL_POR_DEFECTO` | `demo` |

4. En "Advanced": **Health Check Path** `/api/health`. Si ofrece "Auto-Deploy: After CI Checks Pass", elegirlo (así no se publica un commit con el CI en rojo).
5. "Create Web Service". El primer deploy tarda unos minutos (arma la imagen) y termina con "Your service is live". Las migraciones y los datos de demo se cargan solos al arrancar.

**Para probarla:** la dirección de Render abre la web de "Barbería Demo"; el panel está en `/ingresar` con los usuarios de la demo (tabla de arriba) y la documentación de la API en `/swagger-ui.html`.

**Límites del plan gratis:**

- Render la duerme después de 15 minutos sin visitas. La primera visita después la despierta y tarda **unos 2 minutos** (Spring Boot con poco CPU); las siguientes van normal. Conviene avisarlo donde se comparta el link.
- 512 MB de memoria: la imagen le dice a la JVM que se acomode a ese límite (ver el `Dockerfile`).
- Cualquiera que entre puede cambiar los datos de la demo.

Para armar y probar la imagen en la máquina (con los mismos 512 MB de Render): `docker build -t slotly .` en la raíz.

## Convenciones

- **Nombres:** el dominio va en castellano (`Turno`, `Profesional`, `reservar()`, `estado`) y los términos técnicos en inglés (`Controller`, `Repository`, `Response`, `health`).
- **Paquetes por funcionalidad, no por capa:** cada parte del negocio tiene su paquete con todo lo suyo (`ar.com.slotly.local` tiene la entidad, el repositorio, el servicio y el controlador del local). Lo que cambia junto está junto, y las clases que no hace falta exponer quedan sin `public`.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/) con la historia o la etapa como alcance, por ejemplo `feat(HU-07): ofrece solo horarios que entran antes del cierre` o `chore(etapa-0): ...`.