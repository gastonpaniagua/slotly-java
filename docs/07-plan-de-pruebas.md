# Slotly · Plan de pruebas manuales

Casos de prueba manuales, smoke test, sesiones exploratorias y registro de bugs. La estrategia general está en `06-estrategia-de-testing.md`. Los resultados los completa Gaston al ejecutar cada caso.

## Cómo se registra un caso

- **ID:** `CP-HU07-01` (historia y número) o `CP-E0-01` para los de infraestructura.
- **Estado:** Pasó / Falló / Bloqueado. Si falla, se abre un bug abajo.

## Etapa 0 · Esqueleto

**Precondiciones comunes:** base, API y front levantados según el README.

| ID | Qué se prueba | Precondiciones | Pasos | Resultado esperado | Resultado real | Estado | Fecha |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CP-E0-01 | La página muestra la API y la base sanas | Todo levantado | 1. Abrir `http://demo.localhost:5173` | "API: ok" y "Base de datos: ok" | "API: ok" y "Base de datos: ok"| Pasó | 2026-10-12|
| CP-E0-02 | La página informa la base caída | Todo levantado | 1. `docker compose stop db` con la API corriendo 2. Recargar la página 3. Esperar hasta 40 s | "API: error" y "Base de datos: caida" (puede mostrar "Cargando..." hasta ~30 s) |Primero dice cargando luego de 30 segundos "API: error" y "Base de datos: caida"  |Pasó | 2026-10-12 |
| CP-E0-03 | La página informa la API caída | Base y front levantados | 1. Frenar la API (Ctrl+C) 2. Recargar la página | "No pudimos conectar con la API." | "No pudimos conectar con la API." |Pasó |2026-10-12 |

## Smoke test (versión Etapa 0, ~3 minutos)

1. `docker compose up -d`, API y front levantados sin errores en las terminales.
2. `http://demo.localhost:5173` muestra "API: ok" y "Base de datos: ok".
3. `.\mvnw.cmd test` en `backend/` termina en `BUILD SUCCESS`.
4. El último commit tiene el ✅ del CI en GitHub.

## Bugs

### BUG-001 · La API no arranca en Windows por la zona horaria

- **Encontrado:** 2026-10-10, Etapa 0, al levantar la API por primera vez.
- **Pasos:** con la base levantada, en `backend/`, correr `.\mvnw.cmd spring-boot:run`.
- **Esperado:** la API arranca y se conecta a Postgres.
- **Real:** falla con `FATAL: invalid value for parameter "TimeZone": "America/Buenos_Aires"`.
- **Severidad:** bloqueante.
- **Causa:** Java toma la zona horaria de Windows con su nombre viejo (`America/Buenos_Aires`) y el driver se la pasa a Postgres 17, que no la reconoce.
- **Arreglo:** la JVM corre siempre en UTC (`-Duser.timezone=UTC` en `spring-boot-maven-plugin` y `maven-surefire-plugin`). Commit `2ad337b`.
- **Test de regresión:** `SlotlyApplicationTests.contextLoads` levanta Spring contra Postgres con Testcontainers; sin el arreglo falla en Windows.
- **Lección:** el servidor no tiene que depender de la configuración de la máquina. Se arregla en el repo, no en la compu de cada uno.
