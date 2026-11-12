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

## Etapa 2 · Profesionales y primeros pasos de la reserva

**Precondiciones comunes:** base, API y front levantados según el README; la API reiniciada después del paso 22 (datos demo de qué servicios hace cada profesional).

| ID | Qué se prueba | Precondiciones | Pasos | Resultado esperado | Resultado real | Estado | Fecha |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CP-HU05-01 | Resumen de tiempo y precio (CA1) | Todo levantado | 1. Abrir `http://demo.localhost:5173` 2. Tocar "Reservar turno" 3. Marcar "Corte de pelo" y "Barba" | Abajo dice "1 h · $ 22.000" (40 + 20 min, $ 14.000 + $ 8.000) | Deja apretar el botón "Reservar turno". Al elegir "Corte de pelo" y "Barba", abajo dice "1 h · $ 22.000" | Pasó | 2026-11-11 |
| CP-HU05-02 | Sin servicios no se puede avanzar (CA2) | Todo levantado | 1. Entrar al paso 1 sin marcar nada 2. Marcar un servicio y desmarcarlo | "Siguiente" deshabilitado y sin resumen; al marcar se habilita y al desmarcar vuelve a deshabilitarse | Efectivamente, al desmarcar los servicios el botón "Siguiente" se bloquea y al elegir un servicio se habilita | Pasó | 2026-11-11 |
| CP-HU05-03 | La selección se mantiene al volver (CA3) | Todo levantado | 1. Marcar "Corte de pelo" 2. "Siguiente" 3. "Atrás" 4. Repetir con el botón Atrás del navegador | En los dos casos "Corte de pelo" sigue marcado | La selección se mantiene al volver con el botón "Atrás" y con el del navegador | Pasó | 2026-11-11 |
| CP-HU05-04 | Barra de progreso (CA4) | Todo levantado | 1. Mirar arriba en el paso 1 | "Paso 1 de 6 · Servicios" y la barra con un sexto pintado del color del local | Sí, la barra de progreso tiene el color del local y avanza en cada paso hasta el paso 3 (después dice "Próximamente") | Pasó | 2026-11-11 |
| CP-HU06-01 | Profesionales que hacen todo (CA1) | Todo levantado | 1. Marcar solo "Corte de pelo" 2. "Siguiente" | Paso 2 con "Cualquiera disponible", Ana y Bruno | Sí, funciona: aparecen "Cualquiera disponible", Ana y Bruno, y se puede elegir "Cualquiera disponible" | Pasó | 2026-11-11 |
| CP-HU06-02 | Nadie hace la combinación (CA2) | Todo levantado | 1. Marcar "Barba" y "Corte infantil" 2. "Siguiente" 3. "Atrás" | "Ningún profesional hace esta combinación, probá sacando un servicio"; "Atrás" vuelve al paso 1 | Sale el aviso de que ningún profesional hace esa combinación; al sacar un servicio sí aparecen los profesionales | Pasó | 2026-11-11 |
| CP-HU06-03 | Un solo profesional salta el paso 2 (CA3) | Todo levantado | 1. Marcar solo "Barba" 2. "Siguiente" 3. "Atrás" | Va directo a "Elegí el día" (paso 3 de 6) con "Con Bruno."; "Atrás" vuelve al paso 1 | Sí: si elegís "Barba" y tocás "Siguiente" va directo a "Elegí el día" con "Con Bruno.", y "Atrás" vuelve al paso 1 | Pasó | 2026-11-11 |
| CP-HU06-04 | Un servicio de otro local da 404 (CA4, HU-04 CA3) | API levantada | 1. En una terminal: `curl.exe -s -i -H "Host: demo.localhost" "http://localhost:8080/api/publico/profesionales?servicios=20000000-0000-0000-0000-000000000006"` | `HTTP/1.1 404` y en el cuerpo `"codigo":"SERVICIO_NO_ENCONTRADO"` (ese id es el "Corte clásico" de otra) | Sí, responde HTTP 404 y en el código aparece "SERVICIO_NO_ENCONTRADO" | Pasó | 2026-11-11 |

## Etapa 3 · Disponibilidad

**Precondiciones comunes:** base, API y front levantados según el README. "El martes que viene" es el martes de la semana siguiente a la actual: los comandos de preparación lo calculan solos, así funcionan cualquier día que se ejecuten. Antes de cada caso con preparación, correr **L1** para empezar sin turnos de prueba.

CA8 (limpieza) y CA10 (horario de verano) se cubren solo con tests automáticos (`MotorDisponibilidadTest` y `DisponibilidadControllerTest`): la demo tiene limpieza 0 y Argentina no tiene horario de verano.

| ID | Qué se prueba | Precondiciones | Pasos | Resultado esperado | Resultado real | Estado | Fecha |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CP-HU07-01 | Cierre y pausa (CA1, CA2) | Todo levantado | 1. "Reservar turno" → "Corte y barba" (75 min) → Ana 2. Elegir el martes que viene | Mañana: la última es 11:45. Tarde: arranca 16:00 y la última es 18:45. Nada entre 11:45 y 16:00 | Con "Corte y barba" y el martes que viene, los horarios de Ana son los correctos: a la mañana la última es 11:45; a la tarde arranca 16:00 y la última es 18:45; nada entre 11:45 y 16:00 | Pasó | 2026-11-12 |
| CP-HU07-02 | Un turno existente (CA3) | L1 y después S1 | 1. "Barba" (pasa directo a Bruno) 2. Elegir el martes que viene | No aparecen 10:00, 10:15 ni 10:30; sí 10:45 | Al elegir "Barba" pasa directo a Bruno; el martes que viene no aparecen 10:00, 10:15 ni 10:30, y sí 10:45 | Pasó | 2026-11-12 |
| CP-HU07-03 | Un bloqueo (CA4) | L1 y después S2 | 1. "Barba" 2. Elegir el martes que viene | No aparece nada entre 10:45 y 11:45; sí 12:00 | Con "Barba" y el martes que viene no aparece nada entre 10:45 y 11:45; sí 12:00 | Pasó | 2026-11-12 |
| CP-HU07-04 | Apartado vigente y vencido (CA5) | L1 y después S3 | 1. "Barba" 2. Elegir el martes que viene 3. Correr S4 y volver a entrar a las horas | Con S3 no aparece 16:00; después de S4 vuelve a aparecer | Con "Barba" y el martes que viene, con S3 no aparece 16:00; después de S4 vuelve a aparecer | Pasó | 2026-11-12 |
| CP-HU07-05 | Anticipación (CA6) | Hoy el local atiende (martes a sábado) y estamos dentro del horario | 1. "Barba" 2. Elegir hoy | La primera hora es la primera "en punto" de 15 min que queda a más de 1 h de ahora | Con "Barba" y el día de hoy: probado a las 10:49, la primera hora es 12:00 (10:49 + 1 h = 11:49 → la siguiente en punto es 12:00) | Pasó | 2026-11-12 |
| CP-HU07-06 | Cualquiera disponible (CA9) | L1 y después S5 | 1. "Corte de pelo" → "Cualquiera disponible" 2. Elegir el martes que viene | Aparece 10:00 aunque Ana esté bloqueada toda la mañana (Bruno está libre) | Con "Corte de pelo", "Cualquiera disponible" y el martes que viene aparece 10:00 aunque Ana esté bloqueada toda la mañana | Pasó | 2026-11-12 |
| CP-HU07-07 | Profesional que no hace el servicio (CA11) | API levantada | 1. Correr C1 | `HTTP/1.1 404` con `"codigo":"PROFESIONAL_NO_ENCONTRADO"` | `HTTP/1.1 404`, `Content-Type: application/problem+json` y `"codigo":"PROFESIONAL_NO_ENCONTRADO"` con el detalle "Ese profesional no está disponible para los servicios elegidos." | Pasó | 2026-11-12 |
| CP-HU08-01 | Días en gris y primer día elegido (CA1, CA2, CA4) | L1 | 1. "Barba" 2. Mirar el calendario | Domingo, lunes y los días que ya pasaron en gris y sin poder tocarlos; queda elegido el primer día con lugar | Con "Barba", en el calendario domingo, lunes y los días que ya pasaron están en gris y no se pueden tocar; queda elegido el primer día con lugar | Pasó | 2026-11-12 |
| CP-HU08-02 | Día completo en gris (CA1) | L1 y después S6 | 1. "Corte de pelo" → "Cualquiera disponible" 2. Ir a la semana que viene | El martes aparece en gris | Con "Corte de pelo" y "Cualquiera disponible", en la semana que viene el martes aparece en gris | Pasó | 2026-11-12 |
| CP-HU08-03 | Límites de semanas (CA3) | Todo levantado | 1. En el calendario, tocar ‹ y › hasta el final | ‹ deshabilitada en la semana actual; › se deshabilita en la semana que tiene el día "hoy + 30" | En el calendario, tocando ‹ y › hasta el final: ‹ está deshabilitada en la semana actual y › se deshabilita en la semana que tiene el día "hoy + 30" | Pasó | 2026-11-12 |
| CP-HU09-01 | Refresco cada 15 s (CA1) | L1 | 1. "Barba" → el martes que viene 2. Mirar las horas 3. En otra terminal correr S1 4. Esperar sin tocar nada | En 15 s como mucho desaparecen 10:00, 10:15 y 10:30 | Con "Barba" aparecen 10:00, 10:15 y 10:30; al correr S1 en otra terminal desaparecen las tres automáticamente, sin tocar nada | Pasó | 2026-11-12 |
| CP-HU09-02 | Refresco al volver a la pestaña (CA2) | L1 | 1. Con las horas del martes que viene abiertas, cambiar a otra pestaña 2. Correr S2 3. Volver a la pestaña | Al volver se actualiza en ese momento (desaparecen 10:45 a 11:45) | Después de ejecutar S2 desaparecen 10:45 a 11:45 (que se actualice justo al volver a la pestaña lo cubre el test automático de HU-09 CA2) | Pasó | 2026-11-12 |
| CP-HU09-03 | Falla un refresco (CA3) | Todo levantado | 1. Con las horas abiertas, frenar la API (Ctrl+C) 2. Esperar 15 s 3. Levantar la API 4. Esperar 15 s | Con la API caída se sigue viendo la lista con el aviso gris "No pudimos actualizar las horas…"; al volver la API, el aviso desaparece | Con las horas a la vista, al frenar la API aparece el aviso "No pudimos actualizar las horas…" y la lista se sigue viendo; al levantar de nuevo la API, esperando sin tocar nada, el aviso se va solo | Pasó | 2026-11-12 |
| CP-HU09-04 | La hora elegida se ocupa (CA4) | L1 | 1. "Barba" → el martes que viene → elegir 10:00 2. En otra terminal correr S1 3. Esperar sin tocar nada | "Esa hora se acaba de ocupar", 10:00 desaparece y "Siguiente" se deshabilita | Con "Barba", el martes que viene y 10:00 elegida, al ejecutar S1 aparece "Esa hora se acaba de ocupar. Elegí otra.", 10:00 desaparece y "Siguiente" se deshabilita | Pasó | 2026-11-12 |

### Comandos de preparación (desde `C:\dev\slotly-java`)

Todos usan el local demo (`10000000-…-001`), Ana (`30000000-…-001`) y Bruno (`30000000-…-002`). Las horas son de Buenos Aires.

**L1** · Borra todos los turnos de la demo (son solo de prueba):

```bash
docker compose exec db psql -U slotly -d slotly -c "DELETE FROM turno;"
```

**S1** · Bruno con un turno el martes que viene de 10:00 a 10:45:

```bash
docker compose exec db psql -U slotly -d slotly -c "WITH m AS (SELECT date_trunc('week', now() AT TIME ZONE 'America/Argentina/Buenos_Aires') + interval '8 days' AS d) INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta) SELECT '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002', 'bloqueo', 'confirmado', (d + time '10:00') AT TIME ZONE 'America/Argentina/Buenos_Aires', (d + time '10:45') AT TIME ZONE 'America/Argentina/Buenos_Aires', (d + time '10:45') AT TIME ZONE 'America/Argentina/Buenos_Aires' FROM m;"
```

**S2** · Bruno bloqueado el martes que viene de 11:00 a 12:00:

```bash
docker compose exec db psql -U slotly -d slotly -c "WITH m AS (SELECT date_trunc('week', now() AT TIME ZONE 'America/Argentina/Buenos_Aires') + interval '8 days' AS d) INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta) SELECT '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002', 'bloqueo', 'confirmado', (d + time '11:00') AT TIME ZONE 'America/Argentina/Buenos_Aires', (d + time '12:00') AT TIME ZONE 'America/Argentina/Buenos_Aires', (d + time '12:00') AT TIME ZONE 'America/Argentina/Buenos_Aires' FROM m;"
```

**S3** · Un apartado de Bruno el martes que viene a las 16:00 (20 min) que vence en 10 minutos:

```bash
docker compose exec db psql -U slotly -d slotly -c "WITH m AS (SELECT date_trunc('week', now() AT TIME ZONE 'America/Argentina/Buenos_Aires') + interval '8 days' AS d) INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta, vence_en) SELECT '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002', 'reserva', 'apartado', (d + time '16:00') AT TIME ZONE 'America/Argentina/Buenos_Aires', (d + time '16:20') AT TIME ZONE 'America/Argentina/Buenos_Aires', (d + time '16:20') AT TIME ZONE 'America/Argentina/Buenos_Aires', now() + interval '10 minutes' FROM m;"
```

**S4** · Hace vencer el apartado (como si hubieran pasado los minutos):

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE turno SET vence_en = now() - interval '1 minute' WHERE estado = 'apartado';"
```

**S5** · Ana bloqueada el martes que viene de 10:00 a 13:00:

```bash
docker compose exec db psql -U slotly -d slotly -c "WITH m AS (SELECT date_trunc('week', now() AT TIME ZONE 'America/Argentina/Buenos_Aires') + interval '8 days' AS d) INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta) SELECT '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 'bloqueo', 'confirmado', (d + time '10:00') AT TIME ZONE 'America/Argentina/Buenos_Aires', (d + time '13:00') AT TIME ZONE 'America/Argentina/Buenos_Aires', (d + time '13:00') AT TIME ZONE 'America/Argentina/Buenos_Aires' FROM m;"
```

**S6** · Ana y Bruno bloqueados todo el martes que viene (de 10 a 13 y de 16 a 20):

```bash
docker compose exec db psql -U slotly -d slotly -c "WITH m AS (SELECT date_trunc('week', now() AT TIME ZONE 'America/Argentina/Buenos_Aires') + interval '8 days' AS d) INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta) SELECT '10000000-0000-0000-0000-000000000001', p.id, 'bloqueo', 'confirmado', (d + f.desde) AT TIME ZONE 'America/Argentina/Buenos_Aires', (d + f.hasta) AT TIME ZONE 'America/Argentina/Buenos_Aires', (d + f.hasta) AT TIME ZONE 'America/Argentina/Buenos_Aires' FROM m, (VALUES ('30000000-0000-0000-0000-000000000001'::uuid), ('30000000-0000-0000-0000-000000000002'::uuid)) AS p(id), (VALUES (time '10:00', time '13:00'), (time '16:00', time '20:00')) AS f(desde, hasta);"
```

**C1** · Pide horas de Bruno para "Corte infantil", que no hace:

```bash
curl.exe -s -i -H "Host: demo.localhost" "http://localhost:8080/api/publico/disponibilidad/horas?servicios=20000000-0000-0000-0000-000000000004&profesional=30000000-0000-0000-0000-000000000002&fecha=2026-12-01"
```

## Smoke test (versión Etapa 3, ~7 minutos)

1. `docker compose up -d`, API y front levantados sin errores en las terminales.
2. `http://demo.localhost:5173` muestra la home de Barbería Demo con servicios, horarios y mapa.
3. `http://noexiste.localhost:5173` muestra "Este local no existe en Slotly".
4. "Reservar turno" → marcar "Corte de pelo" → "Siguiente" muestra "Cualquiera disponible", Ana y Bruno.
5. Volver, marcar solo "Barba" → "Siguiente" muestra el calendario con un día elegido → "Siguiente" muestra horas.
6. `.\mvnw.cmd test` en `backend/` y `npm test` en `frontend/` terminan en verde.
7. El último commit tiene el ✅ del CI en GitHub.

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
