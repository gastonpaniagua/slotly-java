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

## Convenciones

- **Nombres:** el dominio va en castellano (`Turno`, `Profesional`, `reservar()`, `estado`) y los términos técnicos en inglés (`Controller`, `Repository`, `Response`, `health`).
- **Paquetes por funcionalidad, no por capa:** cada parte del negocio tiene su paquete con todo lo suyo (`ar.com.slotly.local` tiene la entidad, el repositorio, el servicio y el controlador del local). Lo que cambia junto está junto, y las clases que no hace falta exponer quedan sin `public`.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/) con la historia o la etapa como alcance, por ejemplo `feat(HU-07): ofrece solo horarios que entran antes del cierre` o `chore(etapa-0): ...`.