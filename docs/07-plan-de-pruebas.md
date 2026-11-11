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

## Etapa 1 · Cimientos y home del local

**Precondiciones comunes:** base y API levantadas según el README.

| ID | Qué se prueba | Precondiciones | Pasos | Resultado esperado | Resultado real | Estado | Fecha |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CP-E1-01 | Con la base caída, el health responde rápido | Base y API levantadas | 1. `docker compose stop` con la API corriendo 2. `curl.exe -s -w "\n%{http_code} en %{time_total} s\n" http://localhost:8080/api/health` | `503` con `"baseDeDatos":"caida"` en ~5 s (antes ~30 s, ver CP-E0-02) | `503` con `"baseDeDatos":"caida"` en 5,28 s | Pasó | 2026-10-28 |
| CP-HU03-01 | La home tiene la marca del local (CA1, CA4) | Todo levantado, base recreada con `docker compose down -v` | 1. Abrir `http://demo.localhost:5173` 2. Mirar el título de la pestaña 3. F12 → Elements → mirar `<html lang>` y `<title>` | "Barbería Demo" con la inicial "B" en un círculo verde y franja verde arriba; pestaña "Barbería Demo"; `lang="es-AR"` | Se ve "Barbería Demo" con la inicial "B" en un círculo verde y franja verde arriba; la pestaña dice "Barbería Demo"; y también se ve el `lang="es-AR"` | Pasó | 2026-11-11 |
| CP-HU03-02 | Local inexistente (CA2) | Todo levantado | 1. Abrir `http://noexiste.localhost:5173` | "Este local no existe en Slotly", sin códigos ni errores técnicos | Se ve claro el mensaje "Este local no existe en Slotly", sin códigos ni errores | Pasó | 2026-11-11 |
| CP-HU03-03 | Dominio raíz (CA5) | Todo levantado | 1. Abrir `http://localhost:5173` | Página de Slotly con la "S", no la home de un local | Te lleva perfecto a la página de Slotly con una "S" | Pasó | 2026-11-11 |
| CP-HU01-01 | Servicios con formato y sin ocultos (CA1, CA2) | Todo levantado | 1. Abrir `http://demo.localhost:5173` | 4 servicios en este orden: Corte de pelo, Barba, Corte y barba, Corte infantil; "Corte y barba" con "1 h 15 min" y "$ 20.000"; "Color" no aparece | Aparece todo en ese orden con ese precio y "Color" no aparece | Pasó | 2026-11-11 |
| CP-HU01-02 | API caída y reintento (CA4) | Base y front levantados, API frenada | 1. Abrir `http://demo.localhost:5173` 2. Levantar la API 3. Tocar "Probar de nuevo" | Paso 1: "No pudimos cargar la página. Probá de nuevo en un rato." con el botón. Paso 3: carga la home sin recargar la página | Paso 1: aparece "No pudimos cargar la página. Probá de nuevo en un rato." con el botón. Paso 3: con la API levantada, "Probar de nuevo" carga la home sin recargar la página | Pasó | 2026-11-11 |
| CP-HU02-01 | Horarios de la semana (CA1) | Todo levantado | 1. Abrir `http://demo.localhost:5173` 2. Bajar hasta "Horarios de atención" | De lunes a domingo; lunes y domingo "Cerrado"; martes a sábado "10:00 a 13:00 y 16:00 a 20:00" | Aparece de lunes a domingo; lunes y domingo "Cerrado"; y con ese horario de martes a sábado | Pasó | 2026-11-11 |
| CP-HU02-02 | Horarios en hora del local con el navegador en otra zona (CA2) | Todo levantado, Chrome | 1. F12 → ⋮ → More tools → Sensors → Location: "Tokyo" 2. Recargar la página con F12 abierto | Los horarios no cambian: siguen "10:00 a 13:00 y 16:00 a 20:00" | Con la ubicación en Tokyo los horarios siguen "10:00 a 13:00 y 16:00 a 20:00" | Pasó | 2026-11-11 |
| CP-HU02-03 | Ubicación y "Cómo llegar" (CA3) | Todo levantado | 1. Bajar hasta "Ubicación" 2. Tocar "Cómo llegar" | Mapa de Villa María y "San Martín 1200, Villa María"; el botón abre Google Maps con la ruta en otra pestaña | Sí, funciona perfecto: el botón abre Google Maps con la ruta en otra pestaña | Pasó | 2026-11-11 |
| CP-HU04-01 | Otro local ve solo lo suyo (CA1) | Todo levantado | 1. Abrir `http://otra.localhost:5173` | "Otra Barbería" en rojo, un solo servicio ("Corte clásico") y ninguno de demo; todos los días "Cerrado" (otra no tiene profesionales) | Se ve una "O" en un círculo rojo, solo se ve "Corte clásico" y ninguno de demo; todos los días "Cerrado" | Pasó | 2026-11-11 |
| CP-E1-02 | La home se ve bien en celular | Todo levantado, Chrome | 1. F12 → ícono de celular (Ctrl+Shift+M) → "iPhone SE" 2. Recorrer la home de demo | Nada se corta ni desborda a lo ancho; los botones se tocan fácil. (En un celular real se prueba en el deploy: el celular no puede abrir `demo.localhost`) | Anda perfecto en la versión para celular | Pasó | 2026-11-11 |

## Smoke test (versión Etapa 1, ~5 minutos)

1. `docker compose up -d`, API y front levantados sin errores en las terminales.
2. `http://demo.localhost:5173` muestra la home de Barbería Demo con servicios, horarios y mapa.
3. `http://noexiste.localhost:5173` muestra "Este local no existe en Slotly".
4. `.\mvnw.cmd test` en `backend/` y `npm test` en `frontend/` terminan en verde.
5. El último commit tiene el ✅ del CI en GitHub.

Los casos CP-E0-01 a CP-E0-03 quedan como historial: la página de "API: ok" se reemplazó por la home del local en la Etapa 1.

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
