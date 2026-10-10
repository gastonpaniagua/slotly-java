# Slotly · Definición del producto (v1)

Qué hace Slotly, qué pantallas tiene, cómo funcionan los turnos y qué queda para después. Escrito el 2026-10-02 y actualizado con las decisiones del 2026-10-05. No depende del stack: vale igual para la versión en Java + Spring Boot + React. Las marcas **[Hilo 2/3/4]** indican qué conversación del plan original resolvía ese punto; los costos están en `02-integraciones-y-costos.md` y las decisiones en `00-contexto-y-decisiones.md`.

Supuesto general: Slotly se vende a varios locales, así que desde el primer día cada local es un "negocio" con su propia marca, servicios, profesionales y dirección web (por ejemplo `barberiajuan.slotly.com.ar` o su propio dominio). El cliente final nunca ve que hay otros locales.

---

## 1. Funciones

### 1.1 Núcleo (viene en todos los planes)

| Función                 | Detalle                                                                                                                                                                                                             |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Catálogo de servicios   | Nombre, precio, duración, descripción corta y foto opcional. El admin los crea, edita, ordena y oculta.                                                                                                             |
| Profesionales           | Uno o más. Cada profesional elige qué servicios hace y tiene su propio horario. El cliente puede elegir uno o "cualquiera disponible".                                                                              |
| Reserva de turno        | El cliente elige servicio(s), profesional, día y hora libre. Se confirma al instante, sin aprobación.                                                                                                               |
| "Carrito" de servicios  | Un turno puede tener varios servicios (corte + barba). La duración y el precio se suman y se reservan como un bloque continuo. No hace falta un carrito de compras aparte: el resumen del turno cumple esa función. |
| Horarios libres por día | Al tocar un día se ven solo las horas en las que entra completo lo que eligió el cliente.                                                                                                                           |
| Forma de pago           | Efectivo o transferencia, elegida al reservar. Si es transferencia se muestran alias/CBU y titular para copiar. El pago se hace en el local; Slotly solo registra lo elegido.                                       |
| Ubicación               | Dirección, mapa embebido y botón "Cómo llegar" que abre Google Maps.                                                                                                                                                |
| Aviso al profesional    | Notificación en el panel al reservar, cancelar o reprogramar (decisión 2026-10-05). El WhatsApp al profesional queda para después.                                                                                  |
| WhatsApp al cliente     | Confirmación, recordatorio 24 h antes y, después del servicio, pedido de reseña con el link directo a Google. **[Hilo 2]**                                                                                          |
| Cancelar y reprogramar  | Desde el link que le llega al cliente por WhatsApp, sin llamar, hasta X horas antes (lo configura el local).                                                                                                        |
| Reseñas de Google       | Puntaje y últimas reseñas en la página de inicio. **[Hilo 2]**                                                                                                                                                      |
| Panel admin             | Agenda, turnos manuales, servicios, profesionales, horarios, bloqueos, clientes y datos del local (ver sección 2).                                                                                                  |

### 1.2 Extras con costo adicional (decisión tomada: van aparte)

| Extra                 | Detalle                                                                                                                                                                                                                                              |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Mercado Pago          | Link de pago o QR para cobrar el turno completo o una **seña** al reservar. La seña es la mejor arma contra el cliente que no viene; vale la pena venderla así. Si el pago no entra en X minutos, el turno se libera. **[Hilo 2: comisiones de MP]** |
| Catálogo de productos | Vidriera de productos con foto, precio y stock simple. En v1 sin envío ni pago online: el cliente lo agrega a su turno y lo retira y paga en el local.                                                                                               |

Precio de cada extra: **[Hilo 3]**.

### 1.3 Para después (no entra en la v1)

- Lista de espera: si se libera un turno, avisar al primero que lo quería.
- Fidelización: "tu 10.º corte con descuento".
- Estadísticas avanzadas (servicios más vendidos, horas pico, ingresos por profesional). En la v1 alcanza con turnos del día/semana y ausencias.
- Cupones y promociones.
- Envíos y pago online del catálogo.
- App instalable (la web ya va a funcionar bien en el celular).

### 1.4 Cosas que se te pasaron por alto

1. **Clientes que no vienen (no-show).** Es el dolor número uno de cualquier peluquería con turnos online. Proponemos: el admin marca "no vino", se lleva la cuenta por cliente, y el local puede bloquear o exigir seña a quien falta seguido.
2. **Turnos cargados a mano.** El peluquero también recibe gente por teléfono o que entra sin turno. Tiene que poder cargarlos en dos toques para que ese horario deje de aparecer libre.
3. **Login del cliente sin contraseña.** Nadie quiere crear una cuenta para cortarse el pelo. Recomendación: el cliente reserva con nombre y teléfono, y gestiona su turno desde el link que le llega por WhatsApp. Si vuelve, entra con su teléfono y un código que le llega por WhatsApp. Así se cumple el "login cliente" sin fricción.
4. **Evitar reservas falsas.** Límite de turnos futuros por teléfono (por ejemplo 2). La verificación del teléfono con código en la primera reserva queda para después (decisión 2026-10-05: se reserva solo con nombre y teléfono).
5. **Privacidad de datos.** Guardamos nombre y teléfono, así que hace falta una política de privacidad corta y un checkbox de consentimiento para recibir WhatsApp (Meta también lo exige). **[Hilo 2]**
6. **Personalización por local.** Logo, colores, fotos y textos. Es lo que hace que cada cliente sienta que la web es "suya" y lo que te permite vender lo mismo muchas veces.
7. **Mobile primero.** Casi todos van a reservar desde el celular, entrando desde Instagram. El diseño se piensa para el celular y después se adapta a la compu.

---

## 2. Pantallas

Estilo minimalista: fondo claro, una sola tipografía, fotos grandes, el color del local como acento y un botón "Reservar" siempre visible.

### 2.1 Públicas (las 3 que pediste)

**A. Inicio**

- Portada con foto del local, logo, nombre y botón grande "Reservar turno".
- Servicios destacados con precio y duración (link a la lista completa).
- Reseñas de Google: puntaje promedio, 3 a 5 reseñas y botón "Ver todas".
- Galería de trabajos (fotos subidas por el admin o las últimas de Instagram). _Sugerencia: es lo que más vende en peluquería._
- Horarios de atención de la semana, con aviso si hoy está cerrado o de vacaciones.
- Ubicación: dirección, mapita y "Cómo llegar".
- Contacto y redes: WhatsApp, teléfono, Instagram, TikTok, Facebook.
- Botón flotante de WhatsApp para consultas.
- Catálogo de productos (solo si el local tiene el extra).

**B. Reservar** (un paso por pantalla, con barra de progreso y botón "atrás")

1. Elegir servicio(s). Se ve el total de tiempo y precio abajo.
2. Elegir profesional o "cualquiera". Se salta si el local tiene uno solo.
3. Elegir día en un calendario semanal; los días sin lugar aparecen grisados.
4. Elegir hora entre las disponibles.
5. Datos: nombre, teléfono, mail opcional, nota opcional, forma de pago y consentimiento. El horario queda apartado unos minutos mientras completa.
6. Resumen y confirmar. Pantalla final con "Agregar al calendario" y aviso de que le llega el WhatsApp.

**C. Mi turno**

- Se llega desde el link del WhatsApp o entrando con el teléfono.
- Ver turno próximo, reprogramar, cancelar, historial y "reservar lo mismo de nuevo".

### 2.2 Panel admin

| Pantalla            | Qué hace                                                                                                                                                |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Agenda              | Vista día y semana por profesional. Tocar un hueco crea un turno manual; tocar un turno permite moverlo, cancelarlo o marcarlo como "vino" o "no vino". |
| Servicios           | Alta, edición, precio, duración, orden, ocultar.                                                                                                        |
| Profesionales       | Alta, foto, servicios que hace, horario propio.                                                                                                         |
| Horarios y bloqueos | Horario semanal con pausas (por ejemplo cerrado de 13 a 16), feriados, vacaciones y bloqueos puntuales ("el jueves salgo a las 18").                    |
| Clientes            | Lista con teléfono, cantidad de visitas, faltas y última visita.                                                                                        |
| Configuración       | Datos del local, fotos, logo, colores, redes, alias/CBU, link de reseña de Google, reglas de turnos (sección 3.3) y textos de los mensajes.             |
| Resumen             | Turnos de hoy y de la semana, ingresos estimados y faltas.                                                                                              |

Usuarios del panel: el dueño (ve todo) y, si hay varios profesionales, cada uno con su usuario que solo ve su agenda.

---

## 3. Reglas de turnos

### 3.1 Que nunca haya dos turnos en el mismo horario

- La regla vive en la **base de datos**, no solo en la página: la base rechaza cualquier turno que se pise con otro del mismo profesional. Así, aunque dos personas toquen "Confirmar" en el mismo segundo, entra una sola y a la otra le aparece "Ese horario se acaba de ocupar, elegí otro". **[Hilo 4: elegir una base que lo soporte de forma nativa, por ejemplo PostgreSQL]**
- "Pisarse" no es solo la misma hora de inicio: un turno de 10:00 a 10:45 bloquea uno que empiece 10:30.
- Los turnos manuales del admin, los bloqueos y las vacaciones pasan por la misma regla.
- **Reserva en curso:** cuando el cliente llega al paso de datos, el horario queda apartado unos minutos (por ejemplo 5) para que no se lo saquen mientras escribe. Si no confirma, se libera solo. Con seña de Mercado Pago, el apartado dura hasta que entra el pago o vence.

### 3.2 Que lo reservado desaparezca enseguida de la página

- Los horarios libres se calculan en el momento a partir de la agenda real, nunca de una lista guardada que pueda quedar vieja.
- La pantalla de horarios se refresca sola cada pocos segundos y al volver a la pestaña; igualmente la confirmación final vuelve a verificar contra la base (3.1), que es la que manda.

### 3.3 Reglas que configura cada local

| Regla                                | Valor sugerido                                             |
| ------------------------------------ | ---------------------------------------------------------- |
| Intervalo entre opciones de inicio   | Cada 15 minutos                                            |
| Tiempo de limpieza entre turnos      | 0 a 10 min, por servicio o general                         |
| Anticipación mínima para reservar    | 1 hora (que no te caiga un turno para dentro de 5 minutos) |
| Hasta cuándo se puede reservar       | 30 días hacia adelante                                     |
| Cancelar o reprogramar sin penalidad | Hasta 3 horas antes                                        |
| Turnos futuros por teléfono          | Máximo 2                                                   |
| Seña (si tiene Mercado Pago)         | Desactivada, o un % / monto fijo                           |

### 3.4 Horario y zona horaria

- Cada local tiene **una zona horaria fija** (por ejemplo `America/Argentina/Buenos_Aires`). Todos los horarios se muestran en la hora del local, sin importar dónde esté el teléfono del cliente.
- Internamente se guardan en un formato universal (UTC) y se convierten solo para mostrar. Argentina hoy no cambia la hora en verano, pero así queda a salvo si algún día vuelve el horario de verano o se vende en otro país.
- Los mensajes de WhatsApp también usan la hora del local y dicen el día con nombre ("jueves 9 de octubre, 10:30") para que no haya dudas.

### 3.5 Casos borde que hay que cubrir

- **Un servicio que no entra antes del cierre o de la pausa:** no se ofrece. Un servicio de 1 h no aparece a las 19:30 si cierran a las 20.
- **"Cualquier profesional":** se asigna al que esté libre; si hay varios, al que tenga menos turnos ese día.
- **El admin bloquea un día que ya tiene turnos:** el panel avisa cuántos hay y ofrece mandarles un WhatsApp para reprogramar, en vez de borrarlos sin aviso.
- **Cambia la duración o el precio de un servicio:** solo afecta a turnos nuevos; los ya reservados mantienen lo que el cliente vio.
- **Se da de baja un profesional con turnos futuros:** mismo aviso que el bloqueo de días.
- **Reprogramar:** el turno viejo se libera solo cuando el nuevo quedó confirmado, nunca antes.
- **Recordatorios y pedidos de reseña:** no se mandan si el turno se canceló o se marcó como "no vino". La reseña se pide unas 2 horas después de terminado el turno, y una sola vez por cliente cada tanto (por ejemplo cada 90 días) para no cansarlo.

---

## 4. Pendientes del plan original

- **Hilo 2 · Integraciones y costos:** resuelto en `02-integraciones-y-costos.md`.
- **Hilo 3 · Modelo de negocio:** precio del plan base, por profesional extra, del extra Mercado Pago y del catálogo; prueba gratis; qué ofrece la competencia de la zona. Sigue abierto y no bloquea el desarrollo.
- **Hilo 4 · Tecnología:** primero se resolvió con TypeScript (NestJS + Next.js); el 2026-10-10 Gaston reinició con Java + Spring Boot + React. Ver `00-contexto-y-decisiones.md`.

## 5. Decisiones confirmadas con Gaston

1. **(2026-10-02)** Cliente sin contraseña: reserva sin cuenta (nombre + teléfono, mail opcional), link personal para ver, cancelar o reprogramar su turno, y código por WhatsApp solo para volver a entrar a "Mi turno" desde otro dispositivo. Un solo número de WhatsApp de Slotly.
2. **(2026-10-02)** Mercado Pago y catálogo como extras pagos; varios profesionales en el núcleo.
3. **(2026-10-02)** Productos del catálogo se retiran y pagan en el local en la v1.
4. **(2026-10-05)** El profesional recibe el aviso de reservas, cancelaciones y cambios por una notificación en el panel, no por WhatsApp (queda para después).
5. **(2026-10-05)** No se pide código en la primera reserva: se reserva solo con nombre y teléfono (el código en la primera reserva queda para después).
6. **(2026-10-05)** En la v1 el WhatsApp real queda apagado: los mensajes salen por un adaptador simulado y la pantalla de confirmación muestra el link de "Mi turno".
