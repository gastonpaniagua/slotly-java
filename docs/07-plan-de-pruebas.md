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

**L1** · Borra todos los turnos de la demo (son solo de prueba). Primero sus mensajes, sus avisos y sus servicios, porque la base no deja borrar un turno que todavía los tiene:

```bash
docker compose exec db psql -U slotly -d slotly -c "DELETE FROM mensaje; DELETE FROM notificacion; DELETE FROM turno_servicio; DELETE FROM turno;"
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

## Etapa 4 · Reservar un turno

**Precondiciones comunes:** base, API y front levantados según el README (la API reiniciada con el código de la Etapa 4). "El martes que viene" es el de la semana siguiente. Antes de cada caso que lo dice, correr **L1** (sección de la Etapa 3) para empezar sin turnos de prueba. Para dos clientes a la vez se usan dos pestañas: cada una tiene su propia reserva.

Quedan cubiertos solo con tests automáticos: HU-10 CA8 (precio y duración copiados, `ApartadoControllerTest`), HU-13 entera (el constraint en `ReglasDeTurnoEnLaBaseTest` y 10 reservas simultáneas en `ApartadoControllerTest`) y HU-14 CA2, CA4 y CA5 (turno ya pasado, otro local y dos confirmaciones a la vez, en `ConfirmacionTest`): necesitan mover el reloj, otro local con profesionales o simultaneidad real.

| ID | Qué se prueba | Precondiciones | Pasos | Resultado esperado | Resultado real | Estado | Fecha |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CP-HU10-01 | Reserva completa con transferencia (CA1, CA5) | L1 y después S7 | 1. "Barba" → martes que viene → 10:00 → Siguiente 2. Completar los datos y elegir "Transferencia" 3. Confirmar | En el paso 5 aparecen alias, CBU y titular con botones "Copiar"; la confirmación dice "martes … 10:00", Bruno, Barba, total, "Pago: Transferencia" y los datos para transferir | Correcto: aparece toda la información | Paso | 2026/11/12 |
| CP-HU10-02 | Sin consentimiento no se reserva (CA2) | L1 | 1. Llegar al paso 5 2. Completar todo menos la casilla 3. Confirmar 4. Tocar el link de la política | "Tenés que aceptar la política de privacidad." junto a la casilla y no avanza; el link abre la política de privacidad | Si no se marca la casilla de la política de privacidad, no deja avanzar | Paso | 2026/11/12 |
| CP-HU10-03 | Datos inválidos (CA4) | L1 | 1. Llegar al paso 5 2. Nombre vacío, celular "123", mail "ana@" 3. Confirmar | Cada error aparece debajo de su campo y no avanza | Aparece el error en cada campo y no avanza | Paso | 2026/11/12 |
| CP-HU10-04 | Mismo celular escrito de dos formas (CA3) | L1 | 1. Reservar con "11 2345-6789" 2. Reservar otra hora con "+54 9 11 2345 6789" 3. Correr C2 | Un solo cliente con `+5491123456789` y 2 turnos confirmados | Se guardó un solo teléfono con dos turnos confirmados | Paso | 2026/11/12 |
| CP-HU10-05 | Doble toque y recarga (CA6, CA7) | L1 | 1. Llegar al paso 5 con todo completo 2. Tocar "Confirmar" dos veces rápido 3. En la confirmación, recargar (F5) 4. Correr C2 | Un solo turno confirmado; al recargar se ve el mismo turno | Un solo turno confirmado; al recargar se ve el mismo turno | Paso | 2026/11/12 |
| CP-HU10-06 | Datos recordados y "Mis turnos" (CA9) | Después de CP-HU10-05 | 1. "Volver al inicio" → "Mis turnos" → abrir el turno 2. Empezar otra reserva hasta el paso 5 | El turno se abre desde la lista; en el paso 5 nombre y celular ya están completos | El turno se abre desde la lista y en el paso 5 nombre y celular ya están completos | Paso | 2026/11/12 |
| CP-HU11-01 | El apartado saca la hora a los demás (CA1) | L1 | 1. Pestaña A: "Barba" → martes que viene → 11:00 → Siguiente 2. Pestaña B: lo mismo hasta ver las horas | En la pestaña B no aparece 11:00 | En la pestaña B no aparece 11:00 | Paso | 2026/11/12 |
| CP-HU11-02 | Cuenta regresiva y vencimiento (CA2, CA4) | L1 y después S8 (apartado de 1 minuto) | 1. Llegar al paso 5 2. Esperar 1 minuto sin tocar nada 3. Al terminar, correr **S9** | Se ve el tiempo que queda bajando; al llegar a 0 aparece "Se venció el tiempo para confirmar" con "Elegir otra hora" y "Confirmar" deshabilitado | Se ve el tiempo bajando; al llegar a 0 aparece "Se venció el tiempo para confirmar" y "Confirmar" queda deshabilitado | Paso | 2026/11/12 |
| CP-HU11-03 | Volver y elegir otra hora libera la anterior (CA3) | L1 | 1. Pestaña A: apartar 11:00 2. "Atrás" → 11:30 → Siguiente 3. Pestaña B: ver las horas del martes que viene | En la pestaña B aparece 11:00 de nuevo y no aparece 11:30 | Aparece 11:00 de nuevo y no aparece 11:30 | Paso | 2026/11/12 |
| CP-HU12-01 | La hora se ocupa al avanzar (HU-12 CA1, HU-11 CA5) | L1 | 1. Pestaña B: dejar abiertas las horas del martes que viene 2. Pestaña A: apartar 11:00 3. Pestaña B, antes de 15 s: elegir 11:00 → Siguiente | 11:00 desaparece de la pestaña B al volver a ella (HU-09 CA2), así que no se puede elegir. El aviso "Ese horario se acaba de ocupar, elegí otro." lo cubre `PasoHora.test` "HU-12 CA1" y el 409 de la API, CP-HU12-02 | Al volver a la pestaña B, 11:00 ya no estaba y no se pudo elegir | Paso | 2026/11/12 |
| CP-HU12-02 | La API responde 409 con su código (CA3) | L1 | 1. Correr C4 2. Correr C4 otra vez | La primera `HTTP/1.1 201`; la segunda `HTTP/1.1 409` con `"codigo":"HORARIO_OCUPADO"` | La primera `HTTP/1.1 201`; la segunda `HTTP/1.1 409` con `"codigo":"HORARIO_OCUPADO"` | Paso | 2026/11/12 |
| CP-HU14-01 | Máximo 2 turnos futuros (CA1) | L1 | 1. Reservar 2 turnos con el mismo celular 2. Intentar un tercero 3. Correr C2 | "Ya tenés 2 turnos reservados…" con "Ver mis turnos"; C2 sigue mostrando 2 confirmados | No deja reservar un tercer turno con el mismo celular: sale "Ya tenés 2 turnos reservados…" con "Ver mis turnos" y C2 sigue mostrando 2 confirmados | Paso | 2026/11/12 |
| CP-HU14-02 | Un turno cancelado no cuenta (CA3) | Después de CP-HU14-01, S10 | 1. Volver a intentar el tercero | Se confirma | Después de S10 deja confirmar el turno | Paso | 2026/11/12 |
| CP-HU15-01 | Límite de pedidos por IP (CA1, CA2) | L1 | 1. Correr C5 2. Esperar 1 minuto 3. Correr C4 | C5 muestra diez `409` y al final un `429`; después del minuto, C4 vuelve a responder (`201`) | C5 responde diez `409` y al final un `429`; después del minuto, C4 vuelve a dar `201` | Paso | 2026/11/12 |
| CP-HU15-02 | Campos que no existen en el contrato (CA3) | API levantada | 1. Correr C6 | `400` con `"codigo":"VALIDACION"` | `HTTP 400` con `"codigo":"VALIDACION"` | Paso | 2026/11/12 |

### Comandos de la Etapa 4 (desde `C:\dev\slotly-java`)

Los SQL se corren con `docker compose exec`; los pedidos a la API (C4 a C6) escriben el cuerpo en un archivo temporal, porque en PowerShell `curl.exe` pierde las comillas de un JSON escrito en la línea.

**S7** · Carga alias, CBU y titular de prueba en el local demo:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE local SET alias = 'barberia.demo', cbu = '0000003100000000000001', titular_cuenta = 'Juan Perez' WHERE subdominio = 'demo';"
```

**S8** · Apartado de 1 minuto en el local demo (para no esperar 5):

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE local SET apartado_min = 1 WHERE subdominio = 'demo';"
```

**S9** · Vuelve el apartado a 5 minutos:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE local SET apartado_min = 5 WHERE subdominio = 'demo';"
```

**S10** · Cancela el turno confirmado más próximo:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE turno SET estado = 'cancelado' WHERE id = (SELECT id FROM turno WHERE estado = 'confirmado' ORDER BY inicio LIMIT 1);"
```

**C2** · Clientes guardados y cuántos turnos confirmados tiene cada uno:

```bash
docker compose exec db psql -U slotly -d slotly -c "SELECT c.telefono, count(t.id) FILTER (WHERE t.estado = 'confirmado') AS turnos_confirmados FROM cliente c LEFT JOIN turno t ON t.cliente_id = c.id GROUP BY c.telefono ORDER BY c.telefono;"
```

**C4** · Aparta Barba con Bruno el martes que viene a las 11:00, directo a la API:

```bash
$hoy = (Get-Date).Date; $martes = $hoy.AddDays(8 - (([int]$hoy.DayOfWeek + 6) % 7)).ToString('yyyy-MM-dd'); Set-Content -Path $env:TEMP\pedido.json -Value ('{"servicios":["20000000-0000-0000-0000-000000000002"],"profesional":"30000000-0000-0000-0000-000000000002","fecha":"' + $martes + '","hora":"11:00"}'); curl.exe -s -i -H "Host: demo.localhost" -H "Content-Type: application/json" --data-binary "@$env:TEMP\pedido.json" http://localhost:8080/api/publico/apartados
```

**C5** · 11 pedidos seguidos de apartado (a las 14:00, que no se atiende: no crea nada) y muestra el código de cada uno:

```bash
$hoy = (Get-Date).Date; $martes = $hoy.AddDays(8 - (([int]$hoy.DayOfWeek + 6) % 7)).ToString('yyyy-MM-dd'); Set-Content -Path $env:TEMP\pedido.json -Value ('{"servicios":["20000000-0000-0000-0000-000000000002"],"profesional":"30000000-0000-0000-0000-000000000002","fecha":"' + $martes + '","hora":"14:00"}'); 1..11 | ForEach-Object { curl.exe -s -o NUL -w "%{http_code} " -H "Host: demo.localhost" -H "Content-Type: application/json" --data-binary "@$env:TEMP\pedido.json" http://localhost:8080/api/publico/apartados }
```

**C6** · Un apartado con un campo que no existe (`"estado"`):

```bash
$hoy = (Get-Date).Date; $martes = $hoy.AddDays(8 - (([int]$hoy.DayOfWeek + 6) % 7)).ToString('yyyy-MM-dd'); Set-Content -Path $env:TEMP\pedido.json -Value ('{"servicios":["20000000-0000-0000-0000-000000000002"],"profesional":"30000000-0000-0000-0000-000000000002","fecha":"' + $martes + '","hora":"11:00","estado":"confirmado"}'); curl.exe -s -i -H "Host: demo.localhost" -H "Content-Type: application/json" --data-binary "@$env:TEMP\pedido.json" http://localhost:8080/api/publico/apartados
```

## Etapa 5 · Acceso al panel y agenda

**Precondiciones comunes:** base, API y front levantados, con la API reiniciada después del paso 40. Usuarios de la demo (contraseña `demo1234`): `admin@demo.com` (admin, y además Ana) y `bruno@demo.com` (profesional). "El martes que viene" es el de la semana siguiente. "Consola" es la consola del navegador (F12 → Console), con la sesión de ese usuario abierta en el panel.

Quedan cubiertos solo con tests automáticos: HU-16 CA5 (una sesión del local demo usada contra otro local, en `IngresoTest`), porque el navegador no manda la cookie de un subdominio a otro y a mano no se puede reproducir; y la parte "tarda lo mismo" de HU-16 CA2 (`IngresoServiceTest`), porque medir milisegundos a mano no sirve.

| ID | Qué se prueba | Precondiciones | Pasos | Resultado esperado | Resultado real | Estado | Fecha |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CP-HU16-01 | Ingresar y cookie httpOnly (CA1) | Sin sesión | 1. Abrir `demo.localhost:5173/ingresar` 2. Entrar como `admin@demo.com` 3. En la consola: `document.cookie` | Entra a la agenda; `document.cookie` no muestra `SLOTLY_SESION` (JavaScript no la puede leer) | Entra a la agenda; aparece solo XSRF-TOKEN y no SLOTLY_SESION, así que JavaScript no puede leer la cookie de la sesión | Paso | 2026/11/12 |
| CP-HU16-02 | Mismo mensaje para los dos errores (CA2) | Sin sesión | 1. Entrar con `nadie@demo.com` / `demo1234` 2. Entrar con `bruno@demo.com` / `otra` | Las dos veces: "Mail o contraseña incorrectos." | Las dos veces: "Mail o contraseña incorrectos." | Paso | 2026/11/12 |
| CP-HU16-03 | Bloqueo después de 5 fallos (CA3) | Sin sesión | 1. Cinco intentos con `bruno@demo.com` y una contraseña mal 2. Un sexto con `demo1234` 3. Reiniciar la API y volver a entrar | En el sexto: "Demasiados intentos. Probá de nuevo en 15 minutos."; después de reiniciar entra (el contador vive en memoria) | En el sexto intento aparece "Demasiados intentos. Probá de nuevo en 15 minutos."; al reiniciar la API deja entrar | Paso | 2026/11/12 |
| CP-HU16-04 | Un usuario de otro local no entra (CA4) | Sin sesión | 1. Abrir `otra.localhost:5173/ingresar` 2. Entrar como `admin@demo.com` | "Mail o contraseña incorrectos." | Aparece "Mail o contraseña incorrectos." | Paso | 2026/11/12 |
| CP-HU16-05 | Vuelve a la página pedida (CA6) | Sin sesión, L1 | 1. Abrir `demo.localhost:5173/agenda?vista=semana&dia=2026-12-01` 2. Entrar como `bruno@demo.com` | Lleva al login; después de entrar muestra "Semana del 30 de noviembre al 6 de diciembre" | Lleva al login y, después de entrar, muestra "Semana del 30 de noviembre al 6 de diciembre" | Paso | 2026/11/12 |
| CP-HU16-06 | Salir en una pestaña cierra las dos (CA7) | Sesión de Bruno | 1. Abrir la agenda en dos pestañas 2. En la A, "Salir" 3. En la B, tocar "Día siguiente" | Las dos terminan en el login | Al salir en la A, la B también va al login al tocar cualquier cosa | Paso | 2026/11/12 |
| CP-HU16-07 | Contraseña con argon2id (CA8) | — | 1. Correr C7 | Las dos filas con un hash que empieza con `$argon2id$` | C7 muestra las dos filas con un hash que empieza con `$argon2id$` | Paso | 2026/11/12 |
| CP-HU17-01 | Agenda del día (CA1) | L1 | 1. Como cliente, reservar "Barba" con Bruno el martes que viene a las 10:00 2. Entrar como `bruno@demo.com` 3. Ir con › hasta ese martes | "10:00 a 10:20" con el nombre del cliente, "Barba", su celular y "Confirmado" | Después de reservar como cliente, en la agenda de Bruno aparece "10:00 a 10:20" con el nombre del cliente, "Barba", su celular y "Confirmado" | Paso | 2026/11/12 |
| CP-HU17-02 | Vista semana (CA2) | Después de CP-HU17-01 | 1. Tocar "Semana" 2. "Semana siguiente" y "Semana anterior" | Los 7 días de lunes a domingo, el turno en su martes, y se puede pasar de semana | Funcionan los tres: "Semana", "Semana siguiente" y "Semana anterior" | Paso | 2026/11/12 |
| CP-HU17-03 | Agenda de otro: 403 (CA3) | Sesión de Bruno | 1. En la consola, correr C8 | `403` | C8 en la consola responde `403` | Paso | 2026/11/12 |
| CP-HU17-04 | Día sin turnos (CA4) | Sesión de Bruno | 1. Ir a un miércoles sin turnos | "Sin turnos", sin mensaje de error | "Sin turnos", sin mensaje de error | Paso | 2026/11/12 |
| CP-HU17-05 | Bloqueos y pausas (CA5) | Después de CP-HU17-01, S11 | 1. Ver la agenda de Bruno del martes que viene | "Pausa 13:00 a 16:00" (borde punteado) y "Bloqueado: Médico" de 17:00 a 18:00 (con rayas), distintos del turno | Después de S11, en la agenda de Bruno del martes están "Pausa 13:00 a 16:00" (borde punteado) y "Bloqueado: Médico" de 17:00 a 18:00 (con rayas), distintos del turno | Paso | 2026/11/12 |
| CP-HU18-01 | El admin ve a todos y filtra (CA1) | Después de CP-HU17-05 | 1. Entrar como `admin@demo.com` 2. Ir al martes que viene 3. Elegir "Bruno" en "Profesional" y después "Todos" | Con "Todos", Ana y Bruno con su horario; con "Bruno", solo él | Con "Todos", Ana y Bruno con su horario; con "Bruno", solo él | Paso | 2026/11/12 |
| CP-HU18-02 | Profesional inexistente: 404 (CA2) | Sesión de admin | 1. En la consola, correr C9 | `404` | C9 en la consola responde `404` (Not Found) | Paso | 2026/11/12 |
| CP-HU18-03 | Profesional en un endpoint de admin (CA3) | Sesión de Bruno | 1. En la consola, correr C10 | `403` | C10 con la sesión de Bruno responde `403` | Paso | 2026/11/12 |

### Comandos de la Etapa 5

S11 y C7 se corren desde `C:\dev\slotly-java`; C8 a C10, en la consola del navegador.

**S11** · Bruno bloqueado el martes que viene de 17:00 a 18:00, por "Médico":

```bash
docker compose exec db psql -U slotly -d slotly -c "WITH m AS (SELECT date_trunc('week', now() AT TIME ZONE 'America/Argentina/Buenos_Aires') + interval '8 days' AS d) INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta, motivo) SELECT '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002', 'bloqueo', 'confirmado', (d + time '17:00') AT TIME ZONE 'America/Argentina/Buenos_Aires', (d + time '18:00') AT TIME ZONE 'America/Argentina/Buenos_Aires', (d + time '18:00') AT TIME ZONE 'America/Argentina/Buenos_Aires', 'Médico' FROM m;"
```

**C7** · Usuarios del panel y el comienzo de su hash:

```bash
docker compose exec db psql -U slotly -d slotly -c "SELECT email, rol, left(password_hash, 10) AS hash FROM usuario ORDER BY email;"
```

**C8** · Siendo Bruno, pide la agenda de Ana:

```js
fetch('/api/panel/agenda?dias=1&profesional=30000000-0000-0000-0000-000000000001').then(r => r.status)
```

**C9** · Siendo admin, pide la agenda de un profesional que no existe en el local:

```js
fetch('/api/panel/agenda?dias=1&profesional=00000000-0000-0000-0000-000000000000').then(r => r.status)
```

**C10** · Siendo Bruno, usa un endpoint de admin:

```js
fetch('/api/admin/algo').then(r => r.status)
```

## Etapa 6 · Cliente: Mi turno

**Precondiciones comunes:** base, API y front levantados, con la API reiniciada después del paso 46. "Reservar" es hacer la reserva como cliente en `demo.localhost:5173`: "Barba" con Bruno el martes que viene, celular `11 2345-6789`, pago en efectivo. Los códigos de "entrar con mi teléfono" aparecen en la terminal de la API como `[SIMULADO] Código para +549…: 123456`. Para "otro dispositivo" se usa una ventana de incógnito.

Quedan cubiertos solo con tests automáticos: HU-21 CA3 (el horario nuevo se ocupó mientras elegía; `MiTurnoTest` y `Reprogramar.test`), por lo mismo que CP-HU12-01: al volver a la pestaña la lista se actualiza y la hora ya no se puede elegir; HU-22 CA5 con el código vencido (pasan 10 minutos; `CodigoTest`), a mano se prueba el código ya usado; y el 409 de la API al cancelar fuera de plazo (HU-20 CA2, `MiTurnoTest`), a mano se ve que el botón no aparece.

| ID | Qué se prueba | Precondiciones | Pasos | Resultado esperado | Resultado real | Estado | Fecha |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CP-HU19-01 | Mi turno completo (CA1) | L1 | 1. Reservar a las 10:00 | La confirmación muestra el día, "Con Bruno", Barba, el total, "Dónde: San Martín 1200, Villa María" y los botones "Reprogramar" y "Cancelar turno" | Al confirmar aparecen "Con Bruno", Barba, el total, "Dónde: San Martín 1200, Villa María" y los botones "Reprogramar" y "Cancelar turno" | Paso | 2026/11/13 |
| CP-HU19-02 | Link inválido (CA2) | Después de CP-HU19-01 | 1. En la dirección, cambiar una letra después de `#t=` y apretar Enter | "Este link no es válido" con "Entrar con mi teléfono" | Al cambiar una letra del link aparece "Este link no es válido" con "Entrar con mi teléfono" | Paso | 2026/11/13 |
| CP-HU19-03 | Link vencido (CA3) | Después de CP-HU19-01 | 1. Correr S13 2. Recargar el link original | "Este link venció" con "Entrar con mi teléfono" | Después de S13, al volver al link original aparece "Este link venció" con "Entrar con mi teléfono" | Paso | 2026/11/13 |
| CP-HU19-04 | En la base solo el hash (CA4) | Después de CP-HU19-01 | 1. Correr C12 2. Comparar con lo que va después de `#t=` en el link | El `token_hash` de C12 no se parece al token del link | En la base no está el token del link, solo su hash: el token empieza con `1c6BgU1bCq6Y` y el token_hash con `9d7d7ab4b99c` | Paso | 2026/11/13 |
| CP-HU19-05 | Link en otro local (CA5) | Un link de Mi turno | 1. Abrir el mismo link cambiando `demo.` por `otra.` | "Este link no es válido" | Al cambiar `demo.` por `otra.` aparece "Este link no es válido" | Paso | 2026/11/13 |
| CP-HU20-01 | Cancelar libera el horario (CA1) | L1 | 1. Reservar a las 10:00 2. "Cancelar turno" → "Sí, cancelar" 3. Empezar otra reserva igual hasta las horas | "Cancelaste tu turno. El horario quedó libre para otra persona." y las 10:00 aparecen libres | Al cancelar aparece "Cancelaste tu turno. El horario quedó libre para otra persona." y las 10:00 aparecen libres | Paso | 2026/11/13 |
| CP-HU20-02 | Menos de 3 horas (CA2) | L1 | 1. Reservar a las 10:00 2. Correr S12 3. Recargar Mi turno | No aparecen "Cancelar turno" ni "Reprogramar"; aparece "Falta poco para tu turno…" con "Escribir por WhatsApp", que abre `wa.me/5493534000000` | Después de S12 aparece "Falta poco para tu turno…" con "Escribir por WhatsApp", que abre `wa.me/5493534000000` | Paso | 2026/11/13 |
| CP-HU20-03 | Cancelar dos veces (CA3) | L1 | 1. Reservar y abrir el link en dos pestañas 2. Cancelar en la A 3. En la B, "Cancelar turno" → "Sí, cancelar" | En la B se ve el turno cancelado, sin error | Al cancelar en la segunda pestaña se ve el turno cancelado, sin error | Paso | 2026/11/13 |
| CP-HU21-01 | Reprogramar (CA1, CA2) | L1 | 1. Reservar a las 10:00 2. "Reprogramar" 3. Elegir ese martes 4. Mirar las horas 5. Elegir 11:00 → "Confirmar cambio" 6. Correr C12 | Arriba dice "Estás cambiando tu turno del…" y el calendario "Con Bruno."; entre las horas están 10:00 y 10:15 (tu turno no te bloquea); al final "Listo, cambiaste tu turno" a las 11:00; en C12 el de las 10:00 está cancelado y el de las 11:00 confirmado con `reprogramado` en t | Se cumplió todo lo esperado | Paso | 2026/11/13 |
| CP-HU21-02 | Fuera de plazo (CA4) | Después de CP-HU20-02 | 1. Ver Mi turno | No aparece "Reprogramar" | No aparece "Reprogramar" (ni "Cancelar turno"): solo el aviso para escribirle al local | Paso | 2026/11/13 |
| CP-HU21-03 | Conserva el precio (CA5) | L1 | 1. Reservar a las 10:00 2. Correr S15 3. Reprogramar a las 11:00 4. Correr S16 | El turno nuevo sigue diciendo $ 8.000 | El turno nuevo sigue diciendo $ 8.000 | Paso | 2026/11/13 |
| CP-HU22-01 | Entrar con el código (CA1, CA3) | L1, reservar | 1. En incógnito, abrir `demo.localhost:5173/mis-turnos` → "Entrar con mi teléfono" 2. Celular `11 2345-6789` → "Pedir código" 3. Copiar el código de la terminal de la API → "Entrar" | "Si tenés turnos en este local, te mandamos un código de 6 números… Vence en 10 minutos."; después entra a Mis turnos con el turno en "Próximos" | Se cumplió todo lo esperado | Paso | 2026/11/13 |
| CP-HU22-02 | Teléfono sin turnos (CA2) | — | 1. En `/entrar`, pedir código con `11 5555-4444` | El mismo mensaje que en CP-HU22-01; en la terminal de la API no aparece ningún código | El mismo mensaje; en la terminal de la API no apareció ningún código | Paso | 2026/11/13 |
| CP-HU22-03 | 5 intentos (CA4) | — | 1. Pedir código con `11 2345-6789` 2. Ingresar 5 veces `000000` 3. Ingresar el código correcto | Las 6 veces: "El código no es válido o venció. Revisalo o pedí uno nuevo." | Las 5 veces dijo "El código no es válido o venció. Revisalo o pedí uno nuevo." y con el correcto también | Paso | 2026/11/13 |
| CP-HU22-04 | Código usado (CA5) | Después de CP-HU22-01 | 1. "Salir en este navegador" 2. Entrar con el mismo código de CP-HU22-01 | "El código no es válido o venció…" | Dice "El código no es válido o venció…" | Paso | 2026/11/13 |
| CP-HU22-05 | Límite por hora (CA6) | Después de CP-HU22-02 | 1. Pedir código con `11 5555-4444` hasta que aparezca un aviso | Al cuarto pedido de la hora (contando el de CP-HU22-02): "Pediste muchos códigos. Probá de nuevo en una hora." | Al cuarto pedido aparece "Pediste muchos códigos. Probá de nuevo en una hora." | Paso | 2026/11/13 |
| CP-HU22-06 | En la base solo el hash (CA7) | Después de CP-HU22-01 | 1. Correr C11 | Ninguna fila muestra el código de 6 números, solo el comienzo del hash | Ninguna fila muestra el código de 6 números, solo el comienzo del hash | Paso | 2026/11/13 |
| CP-HU23-01 | Historial (CA1) | L1 | 1. Reservar a las 10:00 y a las 11:00 2. Cancelar el de las 11:00 3. Correr S14 4. Entrar con el teléfono | "Próximos" vacío; en "Historial", primero el de las 11:00 ("Cancelado") y después el de las 10:00 de hace dos semanas ("Fuiste") | Se cumplió todo lo esperado | Paso | 2026/11/13 |
| CP-HU23-02 | Reservar lo mismo (CA2) | Después de CP-HU23-01 | 1. En el turno "Fuiste", "Reservar lo mismo" | Abre "Elegí el día" con "Con Bruno." | "Reservar lo mismo" abre "Elegí el día" con "Con Bruno." | Paso | 2026/11/13 |
| CP-HU23-03 | Servicio ya no activo (CA3) | Después de CP-HU23-01, S17 | 1. "Reservar lo mismo" 2. Al terminar, correr S18 | Queda en "Elegí los servicios" con el aviso "Algún servicio de ese turno ya no está disponible…" | Queda en "Elegí los servicios" con el aviso "Algún servicio de ese turno ya no está disponible…"; después se corrió S18 | Paso | 2026/11/13 |

### Comandos de la Etapa 6 (desde `C:\dev\slotly-java`)

**S12** · Mueve el turno confirmado más próximo para que empiece en 2 horas:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE turno SET inicio = now() + interval '2 hours', fin = now() + interval '2 hours 20 minutes', ocupado_hasta = now() + interval '2 hours 20 minutes' WHERE id = (SELECT id FROM turno WHERE estado = 'confirmado' ORDER BY inicio LIMIT 1);"
```

**S13** · Vence el link del turno confirmado más próximo:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE turno SET token_vence_en = now() - interval '1 minute' WHERE id = (SELECT id FROM turno WHERE estado = 'confirmado' ORDER BY inicio LIMIT 1);"
```

**S14** · Pasa el turno confirmado más próximo a hace dos semanas, como atendido:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE turno SET inicio = inicio - interval '14 days', fin = fin - interval '14 days', ocupado_hasta = ocupado_hasta - interval '14 days', estado = 'completado' WHERE id = (SELECT id FROM turno WHERE estado = 'confirmado' ORDER BY inicio LIMIT 1);"
```

**S15** · Barba sube a $ 9.000:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE servicio SET precio = 9000 WHERE nombre = 'Barba';"
```

**S16** · Barba vuelve a $ 8.000:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE servicio SET precio = 8000 WHERE nombre = 'Barba';"
```

**S17** · Barba deja de estar activa:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE servicio SET activo = false WHERE nombre = 'Barba';"
```

**S18** · Barba vuelve a estar activa:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE servicio SET activo = true WHERE nombre = 'Barba';"
```

**C11** · Códigos pedidos y accesos, sin el código:

```bash
docker compose exec db psql -U slotly -d slotly -c "SELECT telefono, intentos, usado_en IS NOT NULL AS usado, left(codigo_hash, 12) AS hash FROM codigo_verificacion ORDER BY creado_en; SELECT left(token_hash, 12) AS acceso, vence_en FROM acceso_cliente;"
```

**C12** · Turnos de reserva con su estado y si vienen de una reprogramación:

```bash
docker compose exec db psql -U slotly -d slotly -c "SELECT estado, inicio AT TIME ZONE 'America/Argentina/Buenos_Aires' AS inicio, reprogramado_de_id IS NOT NULL AS reprogramado, left(token_hash, 12) AS token_hash FROM turno WHERE tipo = 'reserva' AND estado <> 'vencido' ORDER BY inicio;"
```

## Etapa 7 · Gestión del local en el panel

**Precondiciones comunes:** base, API y front levantados, con la API reiniciada después del paso 54. "Admin" es entrar con `admin@demo.com` y "Bruno" con `bruno@demo.com` (contraseña `demo1234`). "Reservar" es hacer la reserva como cliente: "Barba" con Bruno el martes que viene, celular `11 2345-6789`. Los casos que cambian datos de la demo dicen cómo dejarlos como estaban.

Quedan solo con tests automáticos: los 403 de la API para un profesional (salvo C13), el servicio y el cliente de otro local, la imagen de más de 500 KB y el cierre de las sesiones abiertas del profesional dado de baja (a mano se prueba que no puede volver a entrar).

| ID | Qué se prueba | Precondiciones | Pasos | Resultado esperado | Resultado real | Estado | Fecha |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CP-HU24-01 | Crear un servicio (CA1) | Admin | 1. Servicios → "Nuevo servicio": Color, 90 min, $ 30.000 → Guardar 2. Abrir la home | Aparece último en la lista y en la home | Si, aparece ultimo en la lista y en el home | Paso | 2026/11/15 |
| CP-HU24-02 | Cambiar el precio no toca lo reservado (CA2) | L1, reservar | 1. Servicios → Editar Barba → precio 9000 → Guardar 2. Abrir el link del turno 3. Correr S16 | El turno sigue diciendo $ 8.000 | Si , despues de reservar y editar el precio sigue en 8000, el precio previo a editar | Paso | 2026/11/15 |
| CP-HU24-03 | Ocultar (CA3) | Admin | 1. "Ocultar" en Corte infantil 2. Abrir la home 3. "Mostrar" | Aparece "Oculto" y la home no lo muestra; al mostrarlo vuelve | Si, al ocultar corte infantil  aparece "Oculto" y la home no lo muestra; al mostrarlo vuelve | Paso | 2026/11/15 |
| CP-HU24-04 | Datos inválidos (CA4) | Admin | 1. "Nuevo servicio": duración 42, precio -1 → Guardar | Errores junto a la duración y al precio; no se guarda | Si, al intentar crear ese servicio sale "La duración tiene que ser múltiplo de 5 minutos." y "El precio no puede ser negativo." | Paso | 2026/11/15 |
| CP-HU24-05 | Un profesional no administra (CA5) | Bruno | 1. Abrir `demo.localhost:5173/admin/servicios` 2. En la consola, correr C13 | "No tenés permiso para ver esto" y C13 responde `403` | Sale "No tenés permiso para ver esto" y C13 responde `403` | Paso | 2026/11/15 |
| CP-HU25-01 | Alta con foto y servicios (CA1) | Admin, una imagen PNG o JPG chica | 1. Profesionales → "Nuevo profesional": Carla, la foto, "Corte de pelo" → Guardar 2. Reservar "Corte de pelo" hasta el paso 2 | Carla aparece en el paso 2, con su foto | Si, al crar a Carla con su foto aparece con su foto al reservar turno | Paso | 2026/11/15 |
| CP-HU25-02 | Foto que no es imagen (D10) | Admin | 1. Editar Carla → elegir como foto un archivo `.txt` con texto 2. Elegir un `.txt` vacío | Las dos veces: "La imagen tiene que ser PNG, JPG o WebP." | Con el .txt con texto y con el .txt vacío sale 'La imagen tiene que ser PNG, JPG o WebP.'. (Antes del arreglo, el vacío decía 'La imagen puede pesar hasta 500 KB.') | Paso | 2026/11/16 |
| CP-HU25-03 | Mail repetido (CA4) | Después de CP-HU25-01 | 1. "Crear usuario" de Carla con `bruno@demo.com` | Junto al mail: "Ese mail ya tiene un usuario en este local." | Si , al intentar crear el usuario con el mail `bruno@demo.com` salta "Ese mail ya tiene un usuario en este local." | Paso | 2026/11/15 |
| CP-HU25-04 | Usuario del profesional (CA2) | Después de CP-HU25-03 | 1. "Crear usuario" de Carla: `carla@demo.com` / `carla1234` 2. En incógnito, entrar con ese usuario | Entra a la agenda de Carla, sin menú de secciones | Al entrar a la agenda de Carla esta sin menu de seccciones | Paso | 2026/11/15 |
| CP-HU25-05 | Baja con turnos (CA3) | L1, después de CP-HU25-04 | 1. Reservar "Corte de pelo" con Carla el martes que viene 2. Admin → "Dar de baja" a Carla 3. "Dar de baja igual" 4. Reservar "Corte de pelo" hasta el paso 2 5. Agenda del admin, ese martes 6. Intentar entrar como Carla | Antes de confirmar muestra su turno; después Carla ya no aparece en el paso 2, en la agenda sigue su turno y no puede entrar | Antes de confirmar la baja se mostró el turno del martes. Después, Carla no aparece en el paso 2 de la reserva; en la agenda del admin sigue su turno de “Corte de pelo” ese martes; al ingresar con carla@demo.com la rechaza. | Paso | 2026/11/15 |
| CP-HU26-01 | Horario con pausa (CA1) | Admin | 1. Profesionales → "Horario" de Bruno: martes de 10 a 12 y de 15 a 19 → Guardar 2. Reservar hasta las horas 3. Para dejarlo como estaba: volver el martes a 10–13 y 16–20 | Se ofrece hasta 11:40 y desde 15:00; no 12:00 ni 19:00 | Con 10–12 y 15–19 se ofrece hasta 11:40 y desde 15:00; no 12:00 ni 19:00. Al volver a 10–13 y 16–20 vuelven a aparecer 12:00 y 19:00, como corresponde. | Paso | 2026/11/16 |
| CP-HU26-02 | Franjas que se pisan (CA2) | Admin | 1. "Horario" de Bruno: martes 10–13 y 12–14 → Guardar | "El martes tiene dos franjas que se pisan: 10:00 a 13:00 y 12:00 a 14:00." | Paso , al intentar poner ese horario salta el mensaje El martes tiene dos franjas que se pisan: 10:00 a 13:00 y 12:00 a 14:00. | Paso | 2026/11/15 |
| CP-HU26-03 | Turnos que quedan afuera (CA3) | L1 | 1. Reservar a las 17:00 2. "Horario" de Bruno: martes solo 10–13 → Guardar 3. Volver a 10–13 y 16–20 | "Hay 1 turno fuera del horario nuevo…" con el turno; en la agenda el turno sigue | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU27-01 | Bloquear horas (CA1) | L1, admin | 1. Agenda → "Bloquear": Bruno, martes que viene de 10:00 a 12:00, motivo "Médico" 2. Reservar hasta las horas | La agenda muestra "Bloqueado: Médico"; la reserva no ofrece de 10:00 a 11:40 | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU27-02 | Feriado de todo el local (CA1) | Admin | 1. "Bloquear": Todo el local, Días enteros, el miércoles que viene, motivo "Feriado" 2. Reservar "Corte de pelo" hasta el calendario | Ese miércoles aparece en gris para Ana y para Bruno | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU27-03 | Con turnos en el rango (CA2) | L1 | 1. Reservar a las 10:00 2. Admin → "Bloquear": Bruno, ese martes de 9:00 a 12:00 | "Hay 1 turno en ese rango. No se guardó…" con el turno | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU27-04 | El profesional bloquea su agenda (CA3) | Bruno | 1. Agenda → "Bloquear" | No aparece "A quién"; el bloqueo queda en la agenda de Bruno | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU27-05 | Quitar un bloqueo (CA4) | Después de CP-HU27-01 | 1. "Quitar" → "Sí, quitar" 2. Reservar hasta las horas | Las 10:00 vuelven a ofrecerse | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU28-01 | Turno manual (CA1) | L1, admin | 1. Agenda del martes que viene → "+ Agregar turno" de Bruno: 11:00, Barba, nombre Pedro, sin celular 2. Reservar hasta las horas | Aparece en la agenda; la reserva no ofrece 11:00 | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU28-02 | Turno manual ocupado (CA2) | Después de CP-HU28-01 | 1. "+ Agregar turno" de Bruno: 11:10, Barba, Juan | "Ese horario se pisa con otro turno o un bloqueo." | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU28-03 | Sin anticipación (CA3) | Admin | 1. "+ Agregar turno" de Bruno para hoy, dentro de 30 minutos | Se guarda (a la web no la deja reservar con menos de 1 hora) | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU29-01 | Mover (CA1) | Después de CP-HU28-01 | 1. "Mover" el turno de Pedro → 12:00 2. Reservar hasta las horas | Queda a las 12:00 y las 11:00 vuelven a ofrecerse | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU29-02 | Mover a un hueco ocupado (CA2) | Después de CP-HU29-01 | 1. "Bloquear" a Bruno ese martes de 15:00 a 16:00 2. "Mover" el turno de Pedro → 15:00 | "Ese horario se pisa…" y el turno sigue a las 12:00 | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU29-03 | Cancelar sin plazo (CA3) | L1 | 1. Reservar 2. Correr S12 3. Agenda de hoy → "Cancelar" → "Sí, cancelar" | Se cancela, aunque falten 2 horas | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU30-01 | Vino (CA1) | L1 | 1. Reservar 2. Correr S19 3. Agenda de hoy → "Vino" 4. Clientes | El turno dice "Vino"; el cliente tiene 1 visita | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU30-02 | Todavía no empezó (CA2) | L1 | 1. Reservar 2. Agenda de ese martes | El turno no ofrece "Vino" ni "No vino" | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU30-03 | Corregir el mismo día (CA3) | Después de CP-HU30-01 | 1. "Corregir: no vino" 2. Clientes | Dice "No vino"; el cliente tiene 0 visitas y 1 falta | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU31-01 | Lista y búsqueda (CA1) | Después de CP-HU30-03 | 1. Clientes → buscar `2345` | Aparece el cliente con su teléfono, visitas, faltas y última visita | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU31-02 | Historial (CA2) | Después de CP-HU31-01 | 1. "Ver historial" | Sus turnos, del más nuevo al más viejo, con servicios, profesional, total y estado | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU31-03 | Un profesional no ve clientes (CA3) | Bruno | 1. Abrir `demo.localhost:5173/admin/clientes` | "No tenés permiso para ver esto" | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU32-01 | Los datos se ven en la web (CA1) | Admin | 1. Local: nombre "Barbería Nueva", Instagram `@barberia.nueva`, un logo → Guardar 2. Recargar la home 3. Correr S20 | La home muestra el nombre, el logo y, en Contacto, "Instagram @barberia.nueva" que abre instagram.com/barberia.nueva | Se cumplió lo esperado. (La primera vez el Instagram no aparecía: la home no tenía sección de contacto; se agregó en el cierre.) | Paso | 2026/11/16 |
| CP-HU32-02 | Las reglas valen en la reserva (CA2) | Admin | 1. Local: intervalo 30 → Guardar 2. Reservar hasta las horas 3. Correr S20 | Solo horas en punto y media | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU32-03 | Fuera de rango (CA3) | Admin | 1. Local: intervalo 61, CBU `123` → Guardar | Errores junto al intervalo y al CBU; no se guarda | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU32-04 | Aviso de contraste (CA4) | Admin | 1. Local: escribir el color `#ff0000` | "Con este color, el texto de los botones va a costar leerse…" antes de guardar | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU33-01 | Resumen de la semana (CA1) | Algunos turnos esta semana | 1. Resumen 2. Correr C14 | Los números coinciden con C14 | Se cumplió lo esperado. | Paso | 2026/11/16 |
| CP-HU34-01 | Borrar datos (CA1) | Después de CP-HU28-01, sin correr L1 en el medio, con el turno de Pedro cancelado | 1. Clientes → Pedro → "Ver historial" → "Borrar los datos de este cliente" → "Sí, borrar sus datos" | Pasa a "Cliente borrado", sin teléfono; su turno sigue en el historial y ya no ofrece borrar sus datos | Se cumplió lo esperado. (La primera vez el botón de borrar seguía apareciendo; se corrigió en el cierre.) | Paso | 2026/11/16 |
| CP-HU34-02 | Con turnos por delante (CA2) | L1, reservar | 1. Clientes → el cliente → "Borrar los datos…" → "Sí, borrar sus datos" | "Tiene 1 turno por delante. Cancelalos antes de borrar sus datos." | Se cumplió lo esperado. | Paso | 2026/11/16 |

### Comandos de la Etapa 7 (desde `C:\dev\slotly-java`)

**S19** · El turno confirmado más próximo pasa a haber empezado hace una hora:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE turno SET inicio = now() - interval '1 hour', fin = now() - interval '40 minutes', ocupado_hasta = now() - interval '40 minutes' WHERE id = (SELECT id FROM turno WHERE estado = 'confirmado' AND tipo <> 'bloqueo' ORDER BY inicio LIMIT 1);"
```

**S20** · Vuelve el local demo a sus datos y reglas:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE local SET nombre = 'Barbería Demo', instagram = 'barberia.demo', logo_url = NULL, color_acento = '#2e7d32', intervalo_min = 15 WHERE subdominio = 'demo';"
```

**C13** · Siendo Bruno, pide los servicios de administración (consola del navegador):

```js
fetch('/api/admin/servicios').then(r => r.status)
```

**C14** · Turnos, ingresos y faltas de esta semana (de lunes a domingo, en hora del local):

```bash
docker compose exec db psql -U slotly -d slotly -c "WITH s AS (SELECT date_trunc('week', now() AT TIME ZONE 'America/Argentina/Buenos_Aires') AS lunes) SELECT count(*) AS turnos, count(*) FILTER (WHERE t.estado = 'ausente') AS faltas, coalesce(sum((SELECT sum(precio) FROM turno_servicio ts WHERE ts.turno_id = t.id)), 0) AS ingresos FROM turno t, s WHERE t.tipo <> 'bloqueo' AND t.estado IN ('confirmado', 'completado', 'ausente') AND t.inicio AT TIME ZONE 'America/Argentina/Buenos_Aires' >= s.lunes AND t.inicio AT TIME ZONE 'America/Argentina/Buenos_Aires' < s.lunes + interval '7 days';"
```

## Etapa 8 · Mensajes y avisos

**Precondiciones comunes:** base, API y front levantados, con la API reiniciada después del paso 59. "Admin", "Bruno" y "Reservar" son lo mismo que en la Etapa 7. Los mensajes salen por el envío simulado: aparecen en la terminal de la API como `[SIMULADO] WhatsApp a …`. El trabajo que los manda corre cada 30 s, y el de reseñas cada minuto.

Quedan solo con tests automáticos: los reintentos a 1, 5 y 15 minutos y el paso a `fallido` (HU-35 CA3; a mano, S22 simula el fallo); que correr los trabajos dos veces no duplique nada (HU-35 CA2, HU-36 CA4, HU-38 CA5); que dos instancias no tomen el mismo mensaje (`SKIP LOCKED`); el pedido de reseña para un "no vino" o un local sin link (HU-38 CA3 y CA4); y que el recordatorio siga al turno movido desde el panel.

| ID | Qué se prueba | Precondiciones | Pasos | Resultado esperado | Resultado real | Estado | Fecha |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CP-HU35-01 | Confirmación con el link (CA1) | L1 | 1. Reservar 2. Esperar hasta 30 s y mirar la terminal de la API 3. Abrir el link | `[SIMULADO] WhatsApp a +5491123456789: Hola, … Tu turno en Barbería Demo quedó reservado para el martes … a las …. Para verlo, cancelarlo o cambiarlo: http://demo.localhost:5173/turno/…#t=…`; el link abre Mi turno | Se cumplió lo esperado. | 2026/11/18 |
| CP-HU35-02 | El link no queda guardado | Después de CP-HU35-01 | 1. Correr C15 | La confirmación figura `enviado`, con `tiene_link` en `f`; el recordatorio figura `pendiente`, 24 h antes del turno, con `tiene_link` en `t` | Se cumplió lo esperado. | 2026/11/18 |
| CP-HU36-01 | Recordatorio (CA1) | Después de CP-HU35-02 | 1. Correr S21 2. Esperar hasta 30 s | En la terminal: "Te recordamos tu turno en Barbería Demo el martes … a las …", con el link | Se cumplió lo esperado. | 2026/11/18 |
| CP-HU36-02 | Reservado con menos de 24 h (CA2) | L1, un horario libre dentro de las próximas 24 h | 1. Reservar ese horario 2. Correr C15 | Está la confirmación y no hay recordatorio | Se cumplió lo esperado. | 2026/11/18 |
| CP-HU36-03 | Reprogramado (CA3) | L1, reservar | 1. Mi turno → "Reprogramar" a otra hora 2. Correr S21 3. Esperar 30 s 4. Correr C15 | Un solo "Te recordamos…" en la terminal, con la hora nueva; en C15, el recordatorio del turno viejo figura `descartado` | Se cumplió lo esperado. (La primera vez se reprogramó a menos de 24 h y, como corresponde, no hubo recordatorio; se repitió a más de 24 h.) | 2026/11/18 |
| CP-HU37-01 | Aviso por una reserva (CA1, CA2) | L1, Bruno en incógnito | 1. Reservar 2. En la pestaña de Bruno, ir a Agenda 3. Tocar "Avisos" 4. Volver a Agenda | Antes el botón dice "Avisos 1"; la lista muestra "Nuevo" y "Nuevo turno de …: Barba, el martes … a las …"; después, el botón queda sin número | Se cumplió lo esperado. | 2026/11/18 |
| CP-HU37-02 | Cancelación del cliente (CA1) | Después de CP-HU37-01 | 1. En Mi turno, cancelar 2. Bruno va a Agenda y abre "Avisos" | "Se canceló el turno de … del martes … a las …" | Se cumplió lo esperado. | 2026/11/18 |
| CP-HU37-03 | Cambio hecho por otro y por él mismo (CA3) | Admin y Bruno | 1. Admin → "+ Agregar turno" de Bruno: 11:00, Barba, Pedro 2. Admin → "Mover" ese turno a 12:00 3. Bruno → "Mover" el mismo turno a 12:30 4. Bruno abre "Avisos" | Están "Nuevo turno de Pedro…" y "Se movió el turno de Pedro del … a las 11:00 al … a las 12:00"; no hay aviso por el cambio a 12:30 | Se cumplió lo esperado. (La primera vez los avisos mostraban la hora actual del turno, 12:30, en vez de la de cada momento; se arregló en el cierre con V13 y se repitió.) | Paso | 2026/11/18 |
| CP-HU37-04 | Solo los suyos (CA4) | Después de CP-HU37-01 | 1. Admin (que es Ana) abre "Avisos" | No aparece ningún aviso de Bruno | Se cumplió lo esperado. | 2026/11/18 |
| CP-HU38-01 | Pedido de reseña (CA1) | L1, S24 | 1. Reservar 2. Correr S23 3. Esperar hasta 2 minutos | En la terminal: "¡Gracias por venir a Barbería Demo! … reseña: https://g.page/r/barberia-demo/review" | Se cumplió lo esperado. | 2026/11/18 |
| CP-HU38-02 | Una vez cada 90 días (CA2) | Después de CP-HU38-01 | 1. Reservar de nuevo con el mismo celular 2. Correr S23 3. Esperar 2 minutos 4. Correr S25 | No aparece otro pedido de reseña | Se cumplió lo esperado. | 2026/11/18 |
| CP-HU39-01 | Estado de los mensajes (CA1) | L1, reservar, esperar 30 s | 1. Agenda del admin, ese martes | El turno dice "Confirmación enviada · Recordatorio pendiente" | Se cumplió lo esperado. | 2026/11/18 |
| CP-HU39-02 | Un mensaje que no llegó (CA2) | Después de CP-HU39-01 | 1. Correr S22 2. Recargar la agenda | El turno tiene borde rojo y dice "No le llegó un mensaje: llamalo." | Se cumplió lo esperado. | 2026/11/18 |
| CP-E8-01 | Apartados vencidos | — | 1. Reservar hasta el paso de datos y no confirmar 2. Esperar 6 minutos 3. Correr C16 | Ese apartado figura `vencido` | Se cumplió lo esperado. | 2026/11/18 |

### Comandos de la Etapa 8 (desde `C:\dev\slotly-java`)

**S21** · Los recordatorios pendientes salen ya:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE mensaje SET enviar_en = now() WHERE tipo = 'recordatorio' AND estado = 'pendiente';"
```

**S22** · El último mensaje enviado pasa a no entregado:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE mensaje SET estado = 'fallido', intentos = 4, ultimo_error = 'Prueba manual' WHERE id = (SELECT id FROM mensaje WHERE estado = 'enviado' ORDER BY terminado_en DESC LIMIT 1);"
```

**S23** · El turno confirmado más próximo pasa a "vino", terminado hace 2 horas:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE turno SET inicio = now() - interval '150 minutes', fin = now() - interval '2 hours', ocupado_hasta = now() - interval '2 hours', estado = 'completado' WHERE id = (SELECT id FROM turno WHERE estado = 'confirmado' AND tipo <> 'bloqueo' ORDER BY inicio LIMIT 1);"
```

**S24** · El local demo con link de reseña:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE local SET link_resena = 'https://g.page/r/barberia-demo/review' WHERE subdominio = 'demo';"
```

**S25** · El local demo sin link de reseña, como viene:

```bash
docker compose exec db psql -U slotly -d slotly -c "UPDATE local SET link_resena = NULL WHERE subdominio = 'demo';"
```

**C15** · Los mensajes con su estado, en hora del local:

```bash
docker compose exec db psql -U slotly -d slotly -c "SELECT m.tipo, m.estado, m.enviar_en AT TIME ZONE 'America/Argentina/Buenos_Aires' AS enviar_en, m.intentos, m.link IS NOT NULL AS tiene_link, t.inicio AT TIME ZONE 'America/Argentina/Buenos_Aires' AS turno FROM mensaje m JOIN turno t ON t.id = m.turno_id ORDER BY m.creado_en;"
```

**C16** · Los últimos apartados:

```bash
docker compose exec db psql -U slotly -d slotly -c "SELECT estado, vence_en AT TIME ZONE 'America/Argentina/Buenos_Aires' AS vence FROM turno WHERE tipo = 'reserva' AND estado IN ('apartado', 'vencido') ORDER BY vence_en DESC LIMIT 3;"
```

## Smoke test (versión Etapa 8, ~13 minutos)

1. `docker compose up -d`, API y front levantados sin errores en las terminales.
2. `http://demo.localhost:5173` muestra la home de Barbería Demo con servicios, horarios y mapa.
3. `http://noexiste.localhost:5173` muestra "Este local no existe en Slotly".
4. "Reservar turno" → marcar "Corte de pelo" → "Siguiente" muestra "Cualquiera disponible", Ana y Bruno.
5. Volver, marcar solo "Barba" → "Siguiente" muestra el calendario con un día elegido → "Siguiente" muestra horas.
6. Elegir una hora → completar los datos → "Confirmar" muestra "¡Listo! Tu turno quedó reservado"; recargar muestra el mismo turno.
7. En menos de 30 s, la terminal de la API muestra `[SIMULADO] WhatsApp a …` con el link del turno.
8. En Mi turno, "Reprogramar" → otro horario → "Confirmar cambio" muestra "Listo, cambiaste tu turno".
9. `demo.localhost:5173/agenda` lleva al login; con `admin@demo.com` / `demo1234` muestra la agenda con el filtro "Profesional".
10. El admin ve el menú con Agenda, Servicios, Profesionales, Clientes, Local y Resumen, y cada sección carga sin errores.
11. `.\mvnw.cmd test` en `backend/` y `npm test` en `frontend/` terminan en verde.
12. El último commit tiene el ✅ del CI en GitHub.

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
