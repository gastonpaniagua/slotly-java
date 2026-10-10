# Historias de usuario (backlog v1)

Contrato del trabajo de la v1. Sale de los docs 01 a 04 y de las decisiones del 2026-10-02 y 2026-10-05. Gaston lo aprobó el 2026-10-05; el 2026-10-10 se reinició el proyecto en Java + Spring Boot + React y se conserva tal cual, con todas las historias en Pendiente y las URLs de desarrollo de Vite (puerto 5173). Cada historia se resuelve en una etapa y no pasa a **Hecha** hasta que todos sus criterios estén cubiertos por un test automático o por un caso manual ejecutado por Gaston (ver `06-estrategia-de-testing.md` y `07-plan-de-pruebas.md`).

## Cómo leer este documento

- **Formato:** "Como [rol] quiero [acción] para [beneficio]". Roles: _cliente_ (reserva sin cuenta), _profesional_, _admin_ (dueño del local) y, solo si hace falta, _Slotly_ (la plataforma).
- **Prioridad:** Debe (sin esto no hay v1), Debería (importante, pero la v1 sale sin ella si hace falta), Podría (si sobra tiempo).
- **Tamaño:** S (menos de un día), M (uno a tres días), L (más de tres días). Es una estimación para ordenar, no un compromiso.
- **Criterios:** se citan como `HU-07 CA3`. Ese ID va en el nombre del test o del caso manual que lo cubre.
- **Estado:** Pendiente, En curso, Hecha.
- **Datos de ejemplo:** "el local demo" es el del seed: `demo.localhost:5173`, zona `America/Argentina/Buenos_Aires`, 2 profesionales (Ana y Bruno), horario 10 a 13 y 16 a 20, intervalo de 15 min, anticipación mínima 1 h, tope 30 días.

## Resumen

| ID    | Historia                                          | Rol                 | Prioridad | Tamaño | Etapa | Estado    |
| ----- | ------------------------------------------------- | ------------------- | --------- | ------ | ----- | --------- |
| HU-01 | Ver los servicios del local con precio y duración | cliente             | Debe      | S      | 1     | Pendiente |
| HU-02 | Ver horarios de atención y ubicación              | cliente             | Debe      | S      | 1     | Pendiente |
| HU-03 | Tener mi dirección web con mi marca               | admin               | Debe      | M      | 1     | Pendiente |
| HU-04 | Que los datos de mi local no se mezclen con otro  | admin               | Debe      | M      | 1     | Pendiente |
| HU-05 | Elegir uno o varios servicios y ver el total      | cliente             | Debe      | S      | 2     | Pendiente |
| HU-06 | Elegir profesional o "cualquiera"                 | cliente             | Debe      | S      | 2     | Pendiente |
| HU-07 | Ver solo las horas en las que entra lo que elegí  | cliente             | Debe      | L      | 3     | Pendiente |
| HU-08 | Ver en gris los días sin lugar                    | cliente             | Debe      | M      | 3     | Pendiente |
| HU-09 | Que la lista de horas se actualice sola           | cliente             | Debería   | S      | 3     | Pendiente |
| HU-10 | Reservar sin crear cuenta                         | cliente             | Debe      | M      | 4     | Pendiente |
| HU-11 | Que nadie me saque el horario mientras completo   | cliente             | Debe      | M      | 4     | Pendiente |
| HU-12 | Elegir otra hora si la mía se ocupó               | cliente             | Debe      | S      | 4     | Pendiente |
| HU-13 | Que nunca haya dos turnos superpuestos            | admin               | Debe      | M      | 4     | Pendiente |
| HU-14 | Máximo 2 turnos futuros por teléfono              | admin               | Debe      | S      | 4     | Pendiente |
| HU-15 | Frenar reservas automáticas                       | admin               | Debe      | S      | 4     | Pendiente |
| HU-16 | Iniciar y cerrar sesión en el panel               | profesional y admin | Debe      | M      | 5     | Pendiente |
| HU-17 | Ver mi agenda del día y de la semana              | profesional         | Debe      | M      | 5     | Pendiente |
| HU-18 | Ver la agenda de todos los profesionales          | admin               | Debe      | S      | 5     | Pendiente |
| HU-19 | Ver mi turno desde el link                        | cliente             | Debe      | M      | 6     | Pendiente |
| HU-20 | Cancelar mi turno                                 | cliente             | Debe      | S      | 6     | Pendiente |
| HU-21 | Reprogramar mi turno                              | cliente             | Debe      | M      | 6     | Pendiente |
| HU-22 | Volver a entrar con mi teléfono y un código       | cliente             | Debería   | M      | 6     | Pendiente |
| HU-23 | Ver mi historial y reservar lo mismo de nuevo     | cliente             | Podría    | S      | 6     | Pendiente |
| HU-24 | Administrar servicios                             | admin               | Debe      | M      | 7     | Pendiente |
| HU-25 | Administrar profesionales y sus usuarios          | admin               | Debe      | M      | 7     | Pendiente |
| HU-26 | Cargar el horario semanal con pausas              | admin               | Debe      | M      | 7     | Pendiente |
| HU-27 | Bloquear días y horas (feriados, vacaciones)      | admin y profesional | Debe      | M      | 7     | Pendiente |
| HU-28 | Cargar un turno manual                            | admin y profesional | Debe      | S      | 7     | Pendiente |
| HU-29 | Mover o cancelar un turno desde la agenda         | admin y profesional | Debe      | M      | 7     | Pendiente |
| HU-30 | Marcar "vino" o "no vino"                         | admin y profesional | Debe      | S      | 7     | Pendiente |
| HU-31 | Ver mis clientes con visitas y faltas             | admin               | Debe      | S      | 7     | Pendiente |
| HU-32 | Configurar datos y reglas del local               | admin               | Debe      | M      | 7     | Pendiente |
| HU-33 | Ver un resumen de la semana                       | admin               | Podría    | S      | 7     | Pendiente |
| HU-34 | Borrar los datos de un cliente que lo pide        | admin               | Podría    | S      | 7     | Pendiente |
| HU-35 | Recibir la confirmación con el link de mi turno   | cliente             | Debe      | S      | 8     | Pendiente |
| HU-36 | Recibir un recordatorio 24 h antes                | cliente             | Debe      | M      | 8     | Pendiente |
| HU-37 | Enterarme de cada reserva, cancelación o cambio   | profesional         | Debe      | M      | 8     | Pendiente |
| HU-38 | Pedirle reseña al cliente sin cansarlo            | admin               | Debe      | M      | 8     | Pendiente |
| HU-39 | Ver si el mensaje al cliente se entregó           | admin               | Debería   | S      | 8     | Pendiente |

La Etapa 9 (calidad, observabilidad y cierre) no agrega historias nuevas: cubre HU-03 CA6 y refuerza la cobertura de las anteriores (e2e, carga, logs, deploy).

---

## Etapa 1 · Cimientos y home del local

### HU-01 · Ver los servicios del local

Como **cliente** quiero ver los servicios del local con su precio y duración para saber qué puedo reservar y cuánto me va a salir.
Prioridad: Debe · Tamaño: S · Etapa: 1 · Estado: Pendiente

- **CA1.** Dado el local demo con servicios activos, cuando abro `demo.localhost:5173`, entonces veo cada servicio activo con nombre, descripción corta, precio en pesos (`$ 8.000`) y duración (`45 min`, `1 h 15 min`), en el orden que definió el local.
- **CA2.** Dado un servicio oculto (inactivo), cuando abro la home, entonces no aparece ni en la página ni en la respuesta de la API pública.
- **CA3.** Dado un local sin servicios activos, cuando abro la home, entonces veo "Todavía no hay servicios para reservar" y no un error.
- **CA4.** Dado que la API no responde, cuando abro la home, entonces veo un mensaje amable de "No pudimos cargar la página, probá de nuevo en un rato" sin detalles técnicos.

### HU-02 · Ver horarios de atención y ubicación

Como **cliente** quiero ver los horarios de atención de la semana y cómo llegar para saber cuándo y dónde ir.
Prioridad: Debe · Tamaño: S · Etapa: 1 · Estado: Pendiente

- **CA1.** Dado el horario del seed (10 a 13 y 16 a 20, de martes a sábado), cuando abro la home, entonces veo cada día de la semana con sus franjas en hora del local y "Cerrado" en los días sin horario.
- **CA2.** Dado que mi navegador está en otra zona horaria, cuando abro la home, entonces los horarios se siguen mostrando en hora del local.
- **CA3.** Dado un local con dirección cargada, cuando abro la home, entonces veo la dirección, un mapa embebido y un botón "Cómo llegar" que abre Google Maps con esa dirección.
- **CA4.** Dado un local sin dirección cargada, cuando abro la home, entonces la sección de ubicación no aparece (no se muestra un mapa vacío).

### HU-03 · Mi dirección web con mi marca

Como **admin** quiero que mi local tenga su propia dirección (`milocal.slotly.com.ar`) con mi nombre, logo y color para que mis clientes sientan que la web es mía.
Prioridad: Debe · Tamaño: M · Etapa: 1 · Estado: Pendiente

- **CA1.** Dado el local demo, cuando abro `demo.localhost:5173`, entonces veo su nombre, logo y color de acento, y el título de la pestaña es el nombre del local.
- **CA2.** Dado un subdominio que no corresponde a ningún local (`noexiste.localhost:5173`), cuando lo abro, entonces la API responde 404 `LOCAL_NO_ENCONTRADO` y veo una página "Este local no existe en Slotly", sin errores técnicos.
- **CA3.** Dado un pedido a la API con un host con mayúsculas o caracteres raros (`DEMO.localhost`, `de mo`, `../`), cuando llega, entonces se normaliza o se responde 404 `LOCAL_NO_ENCONTRADO`, nunca 500. (Se prueba con MockMvc enviando el header `Host`.)
- **CA4.** Dada cualquier página pública, cuando la abro en el navegador, entonces el documento tiene `lang="es-AR"` y el título y la descripción del local.
- **CA5.** Dado el dominio raíz sin subdominio (`localhost:5173`), cuando lo abro, entonces veo una página simple de Slotly y no la home de un local.
- **CA6.** Dado el link del local pegado en WhatsApp o Instagram, cuando se genera la vista previa, entonces muestra nombre, descripción e imagen del local. (Etapa 9: en producción, quien sirve `index.html` reemplaza los metadatos según el subdominio antes de mandarlo.)

### HU-04 · Datos aislados entre locales

Como **admin** quiero que ningún otro local pueda ver ni tocar mis datos para confiarle a Slotly la agenda y los teléfonos de mis clientes.
Prioridad: Debe · Tamaño: M · Etapa: 1 (y se refuerza en cada etapa) · Estado: Pendiente

- **CA1.** Dados dos locales A y B, cuando pido la home pública de A, entonces la respuesta no incluye ningún servicio, profesional ni horario de B.
- **CA2.** Dado un turno del local A, cuando intento guardarlo (aun con SQL directo) apuntando a un profesional, servicio o cliente del local B, entonces la base lo rechaza.
- **CA3.** Dado un ID de un recurso del local B, cuando lo pido a la API desde el subdominio de A, entonces recibo 404 (no 403, para no confirmar que existe).

Los criterios de aislamiento del panel van en HU-16, HU-17 y HU-18.

---

## Etapa 2 · Profesionales y primeros pasos de la reserva

### HU-05 · Elegir servicios y ver el total

Como **cliente** quiero elegir uno o varios servicios y ver el total de tiempo y precio para saber cuánto voy a tardar y pagar antes de elegir el horario.
Prioridad: Debe · Tamaño: S · Etapa: 2 · Estado: Pendiente

- **CA1.** Dado que elijo "Corte" (45 min, $8.000) y "Barba" (30 min, $5.000), cuando miro el resumen, entonces dice "1 h 15 min · $ 13.000".
- **CA2.** Dado que no elegí ningún servicio, cuando miro el botón "Siguiente", entonces está deshabilitado y el resumen no aparece.
- **CA3.** Dado que elegí servicios y avancé, cuando toco "Atrás", entonces mi selección sigue marcada.
- **CA4.** Dado que estoy en el paso 1, cuando miro la pantalla, entonces veo una barra de progreso que indica el paso 1 de 6.

### HU-06 · Elegir profesional o "cualquiera"

Como **cliente** quiero elegir con quién atenderme o dejar que me asignen a cualquiera disponible para atenderme con quien prefiero o conseguir turno antes.
Prioridad: Debe · Tamaño: S · Etapa: 2 · Estado: Pendiente

- **CA1.** Dados los servicios que elegí, cuando llego al paso 2, entonces veo solo los profesionales activos que hacen **todos** esos servicios, más la opción "Cualquiera disponible".
- **CA2.** Dado que ningún profesional hace todos los servicios elegidos, cuando llego al paso 2, entonces veo "Ningún profesional hace esta combinación, probá sacando un servicio" y puedo volver atrás.
- **CA3.** Dado que un solo profesional hace todos los servicios elegidos, cuando avanzo desde el paso 1, entonces el paso 2 se salta, queda elegido ese profesional y "Atrás" desde el paso 3 me lleva al paso 1.
- **CA4.** Dado que pido a la API los profesionales de un servicio de otro local, cuando respondo, entonces devuelve 404.

---

## Etapa 3 · Disponibilidad

### HU-07 · Ver solo las horas en las que entra lo que elegí

Como **cliente** quiero ver solo las horas en las que entra completo lo que elegí para no reservar algo que después no se puede cumplir.
Prioridad: Debe · Tamaño: L · Etapa: 3 · Estado: Pendiente

- **CA1.** Dado un servicio de 1 h y cierre a las 20:00, cuando veo las horas del día, entonces la última opción es 19:00 (no 19:15, 19:30 ni 19:45).
- **CA2.** Dado un servicio de 45 min y pausa de 13:00 a 16:00, cuando veo las horas, entonces 12:15 aparece y 12:30 no (no se ofrece nada que cruce la pausa).
- **CA3.** Dado un turno existente de 10:00 a 10:45, cuando pido un servicio de 30 min, entonces no aparecen 9:45 (si se trabaja), 10:00, 10:15 ni 10:30, y sí aparece 10:45.
- **CA4.** Dado un bloqueo de 11:00 a 12:00, cuando veo las horas, entonces no aparece ninguna opción que se cruce con el bloqueo.
- **CA5.** Dado un apartado vigente de otro cliente, cuando veo las horas, entonces ese rango no se ofrece; dado un apartado vencido, entonces sí se ofrece.
- **CA6.** Dada la anticipación mínima de 1 h y que son las 10:20, cuando veo las horas de hoy, entonces la primera opción es 11:30 (la primera del intervalo de 15 min que respeta 1 h).
- **CA7.** Dado el tope de 30 días, cuando pido el día 31, entonces no hay horas; el día 30 sí.
- **CA8.** Dado un tiempo de limpieza de 10 min, cuando hay un turno que termina 10:45, entonces la primera opción siguiente es 11:00 (10:55 redondeado al intervalo).
- **CA9.** Dado "Cualquiera disponible", cuando veo las horas, entonces aparece cada hora en la que al menos un profesional que hace todos los servicios está libre, y al reservar se asigna al que tenga menos turnos ese día (empate: el primero por orden).
- **CA10.** Dado un local en una zona con cambio de horario de verano, cuando pido el día del cambio, entonces las horas se calculan en hora local sin duplicar ni perder opciones.
- **CA11.** Dado un pedido con servicios de otro local o un profesional que no hace el servicio, cuando lo hago, entonces la API responde 400 o 404 con un mensaje claro.

### HU-08 · Días sin lugar en gris

Como **cliente** quiero ver en gris los días sin lugar para no tocar día por día buscando un hueco.
Prioridad: Debe · Tamaño: M · Etapa: 3 · Estado: Pendiente

- **CA1.** Dada una semana del calendario, cuando la veo, entonces los días cerrados, bloqueados, completos o fuera del tope aparecen en gris y no se pueden tocar.
- **CA2.** Dado un día con al menos una hora libre para lo que elegí, cuando veo la semana, entonces el día aparece habilitado.
- **CA3.** Dado que estoy en la semana actual, cuando miro el calendario, entonces no puedo ir a semanas anteriores; y no puedo avanzar más allá de la semana que contiene el último día permitido.
- **CA4.** Dado que entro al paso 3, cuando se carga el calendario, entonces queda elegido el primer día con lugar.

### HU-09 · La lista de horas se actualiza sola

Como **cliente** quiero que la lista de horas se actualice sola para no elegir una hora que alguien ya tomó.
Prioridad: Debería · Tamaño: S · Etapa: 3 · Estado: Pendiente

- **CA1.** Dado que estoy mirando las horas de un día, cuando otra persona reserva una de ellas, entonces desaparece de mi lista en el próximo refresco (cada 15 s) sin recargar la página.
- **CA2.** Dado que cambié de pestaña y vuelvo, cuando la pestaña recupera el foco, entonces la lista se refresca en ese momento.
- **CA3.** Dado que un refresco falla, cuando pasa, entonces sigo viendo la última lista con un aviso discreto, y el siguiente refresco lo vuelve a intentar.
- **CA4.** Dado que la hora que tenía elegida desaparece en un refresco, cuando pasa, entonces se deselecciona y veo "Esa hora se acaba de ocupar".

---

## Etapa 4 · Reservar un turno

### HU-10 · Reservar sin crear cuenta

Como **cliente** quiero reservar dejando solo mi nombre y teléfono para no tener que crear una cuenta para cortarme el pelo.
Prioridad: Debe · Tamaño: M · Etapa: 4 · Estado: Pendiente

- **CA1.** Dado que elegí servicios, profesional, día y hora, cuando completo nombre, teléfono, forma de pago y acepto el consentimiento, y toco "Confirmar", entonces veo la pantalla de confirmación con el día en palabras ("jueves 9 de octubre, 10:30"), profesional, servicios, total y forma de pago.
- **CA2.** Dado que no acepto el consentimiento (con link a la política de privacidad), cuando toco "Confirmar", entonces no se envía y se marca el campo.
- **CA3.** Dado un teléfono con formato argentino válido escrito de distintas formas (`11 2345-6789`, `+54 9 11 2345 6789`), cuando reservo, entonces se guarda normalizado y las dos formas identifican al mismo cliente del local.
- **CA4.** Dado un teléfono inválido, un nombre vacío o de más de 80 caracteres, o un mail mal escrito, cuando toco "Confirmar", entonces veo el error junto al campo y la API rechaza el pedido con 400 si se saltea el front.
- **CA5.** Dado que elijo "Transferencia", cuando veo el resumen y la confirmación, entonces aparecen alias, CBU y titular del local con botón de copiar.
- **CA6.** Dado que toco "Confirmar" dos veces seguidas, cuando se procesa, entonces se crea un solo turno.
- **CA7.** Dado que recargo la pantalla de confirmación, cuando vuelve a cargar, entonces veo el mismo turno y no se crea otro.
- **CA8.** Dado que el turno se guardó, cuando miro la base, entonces la duración y el precio de cada servicio quedaron copiados en el turno.
- **CA9.** Dado que reservé, cuando vuelvo a la web del local en el mismo navegador, entonces mis datos (nombre y teléfono) aparecen precargados y tengo un acceso a "Mi turno".

### HU-11 · Apartado mientras completo mis datos

Como **cliente** quiero que el horario quede apartado mientras completo mis datos para que nadie me lo saque mientras escribo.
Prioridad: Debe · Tamaño: M · Etapa: 4 · Estado: Pendiente

- **CA1.** Dado que elegí una hora y paso al formulario de datos, cuando se crea el apartado, entonces esa hora deja de ofrecerse a otros clientes.
- **CA2.** Dado un apartado de 5 minutos, cuando pasan 5 minutos sin confirmar, entonces la hora vuelve a ofrecerse y, si intento confirmar, veo "Se venció el tiempo para confirmar" y vuelvo al paso de horas.
- **CA3.** Dado que vuelvo atrás y elijo otra hora, cuando se crea el nuevo apartado, entonces el anterior se libera.
- **CA4.** Dado que estoy en el formulario, cuando miro la pantalla, entonces veo cuánto tiempo me queda para confirmar.
- **CA5.** Dado que alguien intenta apartar una hora ya apartada por otro, cuando lo hace, entonces recibe el mismo error que un horario ocupado (HU-12).

### HU-12 · Elegir otra hora si la mía se ocupó

Como **cliente** quiero que me avisen si el horario se ocupó y me dejen elegir otro para no perder todo lo que completé.
Prioridad: Debe · Tamaño: S · Etapa: 4 · Estado: Pendiente

- **CA1.** Dado que la hora se ocupó entre que la elegí y confirmé, cuando toco "Confirmar", entonces veo "Ese horario se acaba de ocupar, elegí otro" y vuelvo al paso de horas con la lista actualizada.
- **CA2.** Dado ese caso, cuando vuelvo al paso de horas, entonces mis servicios, profesional y datos personales se conservan.
- **CA3.** Dado ese caso, cuando miro la respuesta de la API, entonces es un 409 con un código de error propio (`HORARIO_OCUPADO`), no un 500 ni el error de la base.

### HU-13 · Nunca dos turnos superpuestos

Como **admin** quiero que nunca haya dos turnos superpuestos del mismo profesional para no tener dos clientes esperando a la misma hora.
Prioridad: Debe · Tamaño: M · Etapa: 4 (el constraint se crea en la 1) · Estado: Pendiente

- **CA1.** Dado un turno de 10:00 a 10:45, cuando se intenta guardar otro del mismo profesional de 10:30 a 11:00 (por API o SQL directo), entonces la base lo rechaza.
- **CA2.** Dado un turno de 10:00 a 10:45, cuando se guarda otro de 10:45 a 11:30, entonces se acepta (turnos consecutivos no se pisan).
- **CA3.** Dado un turno cancelado de 10:00 a 10:45, cuando se guarda otro en ese rango, entonces se acepta.
- **CA4.** Dadas 10 reservas simultáneas al mismo horario y profesional, cuando se procesan a la vez, entonces entra exactamente una y las demás reciben `HORARIO_OCUPADO`.
- **CA5.** Dado un turno de otro profesional en el mismo horario, cuando se guarda, entonces se acepta.
- **CA6.** Dados un bloqueo o un turno manual, cuando se cruzan con un turno, entonces la misma regla los rechaza.

### HU-14 · Máximo 2 turnos futuros por teléfono

Como **admin** quiero que un mismo teléfono no pueda tener más de 2 turnos futuros para evitar que alguien me llene la agenda con reservas falsas.
Prioridad: Debe · Tamaño: S · Etapa: 4 · Estado: Pendiente

- **CA1.** Dado un teléfono con 2 turnos futuros confirmados en el local, cuando intenta reservar un tercero, entonces ve "Ya tenés 2 turnos reservados" con acceso a "Mi turno" y no se crea.
- **CA2.** Dado un teléfono con 2 turnos, uno ya pasado, cuando reserva, entonces se acepta (solo cuentan los futuros).
- **CA3.** Dado un teléfono con 2 turnos futuros, uno cancelado, cuando reserva, entonces se acepta.
- **CA4.** Dado un teléfono con 2 turnos futuros en el local A, cuando reserva en el local B, entonces se acepta (el límite es por local).
- **CA5.** Dado un teléfono con 1 turno futuro que manda 2 reservas simultáneas, cuando se procesan a la vez, entonces entra solo una.

### HU-15 · Frenar reservas automáticas

Como **admin** quiero que nadie pueda disparar reservas en masa para que un bot no me deje la agenda llena de turnos falsos.
Prioridad: Debe · Tamaño: S · Etapa: 4 · Estado: Pendiente

- **CA1.** Dada una misma IP que hace más de 10 pedidos de reserva o apartado por minuto, cuando hace el siguiente, entonces recibe 429 con "Demasiados intentos, esperá un minuto" y no se crea nada.
- **CA2.** Dado el límite superado, cuando pasa el minuto, entonces puede volver a intentar.
- **CA3.** Dado un pedido con campos que no existen en el contrato o tipos incorrectos, cuando llega a la API, entonces se rechaza con 400 (no se ignora en silencio un `estado` o `precio` enviado por el cliente).

---

## Etapa 5 · Acceso al panel y agenda

### HU-16 · Iniciar y cerrar sesión en el panel

Como **profesional o admin** quiero entrar al panel con mi usuario y contraseña para gestionar mi agenda sin que nadie más pueda verla.
Prioridad: Debe · Tamaño: M · Etapa: 5 · Estado: Pendiente

- **CA1.** Dado un usuario válido del local demo, cuando inicio sesión en `demo.localhost:5173/ingresar`, entonces entro a la agenda y la sesión queda en una cookie httpOnly.
- **CA2.** Dado un mail inexistente o una contraseña incorrecta, cuando intento entrar, entonces veo el mismo mensaje "Mail o contraseña incorrectos" y la respuesta tarda lo mismo en los dos casos.
- **CA3.** Dados 5 intentos fallidos seguidos para el mismo mail, cuando intento de nuevo, entonces recibo 429 durante 15 minutos.
- **CA4.** Dado un usuario del local A, cuando intenta iniciar sesión en el subdominio del local B, entonces falla con el mismo mensaje genérico.
- **CA5.** Dada una sesión del local A, cuando la uso contra la API del local B, entonces recibo 401.
- **CA6.** Dado que entro a cualquier página del panel sin sesión o con la sesión vencida (más de 7 días sin uso), cuando la abro, entonces me lleva al login y después del login vuelvo a la página que quería.
- **CA7.** Dado que cierro sesión en una pestaña, cuando hago algo en otra, entonces me lleva al login (la sesión se invalida en el servidor).
- **CA8.** Dada una contraseña guardada, cuando miro la base, entonces está hasheada con argon2id.

### HU-17 · Ver mi agenda

Como **profesional** quiero ver mis turnos del día y de la semana para organizarme.
Prioridad: Debe · Tamaño: M · Etapa: 5 · Estado: Pendiente

- **CA1.** Dado que inicié sesión como profesional, cuando abro la agenda, entonces veo mis turnos de hoy en hora del local con cliente, servicios y estado, ordenados por hora.
- **CA2.** Dado que cambio a vista semana, cuando la veo, entonces aparecen mis turnos de los 7 días y puedo pasar a la semana anterior y la siguiente.
- **CA3.** Dado que soy profesional, cuando pido la agenda de otro profesional (por URL o API), entonces recibo 403.
- **CA4.** Dado un día sin turnos, cuando lo veo, entonces aparece "Sin turnos" y no un error.
- **CA5.** Dados bloqueos y pausas, cuando veo la agenda, entonces aparecen marcados distinto de los turnos.

### HU-18 · Ver la agenda de todos

Como **admin** quiero ver la agenda de todos los profesionales para saber cómo viene el día del local.
Prioridad: Debe · Tamaño: S · Etapa: 5 · Estado: Pendiente

- **CA1.** Dado que inicié sesión como admin, cuando abro la agenda, entonces puedo ver todos los profesionales juntos o filtrar por uno.
- **CA2.** Dado un profesional de otro local, cuando el admin pide su agenda, entonces recibe 404.
- **CA3.** Dado un profesional, cuando intenta usar los endpoints de admin, entonces recibe 403.

---

## Etapa 6 · Cliente: Mi turno

### HU-19 · Ver mi turno desde el link

Como **cliente** quiero abrir mi turno desde el link personal para consultarlo sin llamar al local.
Prioridad: Debe · Tamaño: M · Etapa: 6 · Estado: Pendiente

- **CA1.** Dado un link válido, cuando lo abro, entonces veo día, hora, profesional, servicios, total, dirección y las acciones permitidas.
- **CA2.** Dado un link con un token inexistente o adulterado, cuando lo abro, entonces veo "Este link no es válido" con acceso a entrar con mi teléfono, y la API responde 404.
- **CA3.** Dado un link de un turno que ya pasó hace más de 30 días, cuando lo abro, entonces veo "Este link venció".
- **CA4.** Dado un token, cuando miro la base, entonces solo está guardado su hash.
- **CA5.** Dado un link del local A, cuando lo abro en el subdominio del local B, entonces se comporta como un link inválido.

### HU-20 · Cancelar mi turno

Como **cliente** quiero cancelar mi turno desde el link para liberar el horario si no puedo ir.
Prioridad: Debe · Tamaño: S · Etapa: 6 · Estado: Pendiente

- **CA1.** Dado un turno a más de 3 horas (regla del local), cuando toco "Cancelar" y confirmo, entonces queda cancelado y el horario vuelve a ofrecerse.
- **CA2.** Dado un turno a menos de 3 horas, cuando lo abro, entonces no veo "Cancelar" sino "Para cancelar, escribile al local" con su WhatsApp, y la API responde 409 si se intenta igual.
- **CA3.** Dado un turno ya cancelado, cuando intento cancelarlo otra vez, entonces veo que ya está cancelado y no cambia nada.

### HU-21 · Reprogramar mi turno

Como **cliente** quiero cambiar mi turno a otro día u hora para no perderlo si me surge algo.
Prioridad: Debe · Tamaño: M · Etapa: 6 · Estado: Pendiente

- **CA1.** Dado un turno reprogramable, cuando toco "Reprogramar", entonces paso al calendario con los mismos servicios y profesional, y el horario actual no cuenta como ocupado para mí.
- **CA2.** Dado que elijo un horario libre y confirmo, cuando se guarda, entonces el turno nuevo queda confirmado y el viejo cancelado en la misma transacción.
- **CA3.** Dado que el horario nuevo se ocupó, cuando confirmo, entonces veo `HORARIO_OCUPADO` y mi turno viejo sigue intacto.
- **CA4.** Dado un turno a menos de 3 horas, cuando lo abro, entonces no puedo reprogramar (mismo criterio que HU-20 CA2).
- **CA5.** Dado que reprogramo, cuando miro el turno nuevo, entonces los precios copiados son los del turno original (no cambia lo que el cliente vio).

### HU-22 · Volver a entrar con mi teléfono y un código

Como **cliente** quiero entrar con mi teléfono y un código para ver mis turnos desde otro dispositivo sin crear una contraseña.
Prioridad: Debería · Tamaño: M · Etapa: 6 · Estado: Pendiente

- **CA1.** Dado un teléfono con turnos en el local, cuando lo ingreso, entonces se genera un código de 6 dígitos que vence en 10 minutos y se envía por el canal de notificaciones (simulado en desarrollo).
- **CA2.** Dado un teléfono sin turnos en el local, cuando lo ingreso, entonces veo exactamente la misma respuesta que en CA1 (no se revela si el teléfono existe).
- **CA3.** Dado el código correcto dentro de los 10 minutos, cuando lo ingreso, entonces veo mis turnos.
- **CA4.** Dados 5 intentos fallidos, cuando ingreso otro código, entonces se invalida y tengo que pedir uno nuevo.
- **CA5.** Dado un código ya usado o vencido, cuando lo ingreso, entonces se rechaza.
- **CA6.** Dados más de 3 pedidos de código por teléfono en una hora, cuando pido otro, entonces recibo 429.
- **CA7.** Dado un código, cuando miro la base, entonces solo está guardado su hash.

### HU-23 · Historial y reservar lo mismo de nuevo

Como **cliente** quiero ver mis turnos anteriores y repetir uno para reservar mi corte de siempre en dos toques.
Prioridad: Podría · Tamaño: S · Etapa: 6 · Estado: Pendiente

- **CA1.** Dado que entré a "Mi turno", cuando miro el historial, entonces veo mis turnos pasados y cancelados del local, del más nuevo al más viejo.
- **CA2.** Dado un turno pasado, cuando toco "Reservar lo mismo", entonces el flujo de reserva arranca en el paso 3 con los mismos servicios y profesional (si siguen activos).
- **CA3.** Dado que un servicio de ese turno ya no está activo, cuando toco "Reservar lo mismo", entonces arranco en el paso 1 con los servicios activos preseleccionados y un aviso.

---

## Etapa 7 · Gestión del local en el panel

### HU-24 · Administrar servicios

Como **admin** quiero crear, editar, ordenar y ocultar servicios para mantener mi oferta al día.
Prioridad: Debe · Tamaño: M · Etapa: 7 · Estado: Pendiente

- **CA1.** Dado el formulario de servicio, cuando guardo nombre, descripción, precio, duración (múltiplo de 5, entre 5 y 480 min) y limpieza, entonces aparece en la lista y en la home pública.
- **CA2.** Dado un servicio con turnos reservados, cuando cambio su precio o duración, entonces los turnos ya reservados mantienen los valores copiados.
- **CA3.** Dado un servicio, cuando lo oculto, entonces deja de ofrecerse en la web pública y los turnos ya reservados siguen igual.
- **CA4.** Dados datos inválidos (precio negativo, duración 0), cuando guardo, entonces veo el error junto al campo y la API responde 400.
- **CA5.** Dado que soy profesional, cuando intento administrar servicios, entonces recibo 403.

### HU-25 · Administrar profesionales y sus usuarios

Como **admin** quiero dar de alta profesionales, elegir qué servicios hace cada uno y darles su usuario para que cada uno vea su agenda.
Prioridad: Debe · Tamaño: M · Etapa: 7 · Estado: Pendiente

- **CA1.** Dado el alta de un profesional con nombre, foto opcional y servicios, cuando guardo, entonces aparece en el paso 2 de la reserva para esos servicios.
- **CA2.** Dado un profesional, cuando le creo un usuario con mail y contraseña inicial, entonces puede iniciar sesión y solo ve su agenda.
- **CA3.** Dado un profesional con turnos futuros, cuando intento darlo de baja, entonces veo cuántos turnos tiene y la baja no se hace hasta que confirme; al confirmar deja de recibir reservas y sus turnos futuros quedan listados para reprogramar.
- **CA4.** Dado un mail ya usado en el local, cuando creo otro usuario con ese mail, entonces se rechaza.

### HU-26 · Horario semanal con pausas

Como **admin** quiero cargar el horario semanal de cada profesional con pausas para que solo se ofrezcan las horas en las que trabaja.
Prioridad: Debe · Tamaño: M · Etapa: 7 · Estado: Pendiente

- **CA1.** Dadas dos franjas el mismo día (10 a 13 y 16 a 20), cuando guardo, entonces la disponibilidad ofrece horas solo dentro de esas franjas.
- **CA2.** Dadas dos franjas que se pisan el mismo día o una franja con fin antes del inicio, cuando guardo, entonces se rechaza con un mensaje claro.
- **CA3.** Dado un cambio de horario que deja afuera turnos ya reservados, cuando guardo, entonces veo cuántos turnos quedan fuera del horario nuevo (no se cancelan solos).

### HU-27 · Bloqueos, feriados y vacaciones

Como **admin o profesional** quiero bloquear días u horas para que no se reserve cuando no voy a estar.
Prioridad: Debe · Tamaño: M · Etapa: 7 · Estado: Pendiente

- **CA1.** Dado un bloqueo de un rango (horas o días enteros) para un profesional o para todo el local, cuando lo guardo, entonces esas horas dejan de ofrecerse.
- **CA2.** Dado un rango con turnos reservados, cuando intento bloquearlo, entonces veo "Hay N turnos en ese rango" con la lista, y el bloqueo no se guarda hasta que esos turnos se reprogramen o cancelen (lo garantiza el mismo constraint de HU-13).
- **CA3.** Dado que soy profesional, cuando creo un bloqueo, entonces solo puedo hacerlo sobre mi agenda.
- **CA4.** Dado un bloqueo, cuando lo borro, entonces esas horas vuelven a ofrecerse.

### HU-28 · Turno manual

Como **admin o profesional** quiero cargar en dos toques un turno para quien llama o entra sin reserva para que ese horario deje de aparecer libre.
Prioridad: Debe · Tamaño: S · Etapa: 7 · Estado: Pendiente

- **CA1.** Dado un hueco de la agenda, cuando lo toco y completo servicios y nombre (teléfono opcional), entonces se crea el turno y deja de ofrecerse en la web pública.
- **CA2.** Dado un hueco que se superpone con otro turno o bloqueo, cuando guardo, entonces veo `HORARIO_OCUPADO`.
- **CA3.** Dado un turno manual, cuando lo cargo, entonces no cuenta para el límite de 2 turnos futuros del cliente y se permite fuera de la anticipación mínima.

### HU-29 · Mover o cancelar un turno desde la agenda

Como **admin o profesional** quiero mover o cancelar un turno desde la agenda para acomodar cambios que me piden por teléfono.
Prioridad: Debe · Tamaño: M · Etapa: 7 · Estado: Pendiente

- **CA1.** Dado un turno, cuando lo muevo a un hueco libre, entonces queda en el horario nuevo y el viejo se libera en la misma transacción.
- **CA2.** Dado un hueco ocupado, cuando intento moverlo ahí, entonces veo `HORARIO_OCUPADO` y el turno no cambia.
- **CA3.** Dado un turno, cuando lo cancelo desde el panel, entonces queda cancelado aunque falten menos de 3 horas (la regla de anticipación es para el cliente).

### HU-30 · Marcar "vino" o "no vino"

Como **admin o profesional** quiero marcar si el cliente vino para llevar la cuenta de quién falta.
Prioridad: Debe · Tamaño: S · Etapa: 7 · Estado: Pendiente

- **CA1.** Dado un turno que ya empezó, cuando lo marco "vino" o "no vino", entonces cambia su estado y se actualiza el contador del cliente.
- **CA2.** Dado un turno que todavía no empezó, cuando intento marcarlo, entonces no se permite.
- **CA3.** Dado un turno marcado por error, cuando lo cambio el mismo día, entonces se corrige el estado y el contador.

### HU-31 · Ver mis clientes

Como **admin** quiero ver mis clientes con visitas, faltas y última visita para saber quién es fiel y quién falta seguido.
Prioridad: Debe · Tamaño: S · Etapa: 7 · Estado: Pendiente

- **CA1.** Dada la lista de clientes, cuando la abro, entonces veo nombre, teléfono, visitas, faltas y última visita, y puedo buscar por nombre o teléfono.
- **CA2.** Dado un cliente, cuando lo abro, entonces veo su historial de turnos en el local.
- **CA3.** Dado que soy profesional, cuando intento ver la lista de clientes, entonces recibo 403.

### HU-32 · Configurar datos y reglas del local

Como **admin** quiero configurar los datos, la imagen y las reglas de turnos de mi local para que la web y las reservas funcionen como yo trabajo.
Prioridad: Debe · Tamaño: M · Etapa: 7 · Estado: Pendiente

- **CA1.** Dado el formulario de configuración, cuando cambio nombre, logo, color, dirección, redes, WhatsApp de contacto, alias/CBU/titular y link de reseña de Google, entonces se reflejan en la web pública.
- **CA2.** Dadas las reglas (intervalo 5 a 60 min, limpieza 0 a 30 min, anticipación mínima, tope de días, horas para cancelar, turnos futuros por teléfono), cuando las cambio, entonces la disponibilidad y las reservas nuevas las respetan.
- **CA3.** Dados valores fuera de rango, cuando guardo, entonces se rechazan con un mensaje claro.
- **CA4.** Dado un color de acento, cuando lo elijo, entonces se avisa si no tiene contraste suficiente con el texto.

### HU-33 · Resumen de la semana

Como **admin** quiero ver los turnos de hoy y de la semana, los ingresos estimados y las faltas para tener una idea rápida de cómo viene el local.
Prioridad: Podría · Tamaño: S · Etapa: 7 · Estado: Pendiente

- **CA1.** Dados los turnos de la semana, cuando abro el resumen, entonces veo cantidad de turnos de hoy y de la semana, suma de precios copiados de los no cancelados y cantidad de "no vino".

### HU-34 · Borrar los datos de un cliente

Como **admin** quiero borrar los datos personales de un cliente que lo pide para cumplir con la ley de datos personales.
Prioridad: Podría · Tamaño: S · Etapa: 7 · Estado: Pendiente

- **CA1.** Dado un cliente sin turnos futuros, cuando confirmo "Borrar datos", entonces su nombre, teléfono y mail se anonimizan y los turnos pasados quedan sin datos personales (para no romper el historial del local).
- **CA2.** Dado un cliente con turnos futuros, cuando intento borrarlo, entonces se pide cancelarlos antes.

---

## Etapa 8 · Trabajos en segundo plano y notificaciones

En la v1 los mensajes salen por un **adaptador simulado** (se guardan y se ven en el panel y en los logs). El adaptador de WhatsApp queda preparado pero apagado hasta que Gaston lo pida.

### HU-35 · Confirmación con el link de mi turno

Como **cliente** quiero recibir la confirmación con el link de mi turno para tenerlo a mano y poder cancelarlo o cambiarlo.
Prioridad: Debe · Tamaño: S · Etapa: 8 · Estado: Pendiente

- **CA1.** Dado un turno reservado, cuando se confirma, entonces se encola un mensaje al cliente con el nombre del local, el día en palabras, la hora del local y el link personal.
- **CA2.** Dado que el trabajo de envío se ejecuta dos veces, cuando termina, entonces hay un solo mensaje enviado.
- **CA3.** Dado que el envío falla, cuando pasa, entonces se reintenta hasta 3 veces y después queda marcado como no entregado (ver HU-39).

### HU-36 · Recordatorio 24 h antes

Como **cliente** quiero recibir un recordatorio el día anterior para no olvidarme del turno.
Prioridad: Debe · Tamaño: M · Etapa: 8 · Estado: Pendiente

- **CA1.** Dado un turno confirmado, cuando faltan 24 h, entonces se envía un recordatorio con día, hora y link.
- **CA2.** Dado un turno reservado con menos de 24 h de anticipación, cuando se confirma, entonces no se manda recordatorio.
- **CA3.** Dado un turno cancelado o reprogramado antes del recordatorio, cuando llega la hora, entonces no se manda (y el turno nuevo tiene su propio recordatorio).
- **CA4.** Dado que el trabajo corre dos veces, cuando termina, entonces hay un solo recordatorio.

### HU-37 · Aviso al profesional

Como **profesional** quiero enterarme de cada reserva, cancelación o cambio en mi agenda para no encontrarme sorpresas.
Prioridad: Debe · Tamaño: M · Etapa: 8 · Estado: Pendiente

- **CA1.** Dado un turno reservado, cancelado o reprogramado por un cliente, cuando pasa, entonces el profesional ve una notificación en el panel (contador y lista) con el detalle.
- **CA2.** Dado que abro las notificaciones, cuando las leo, entonces el contador baja a cero.
- **CA3.** Dado un cambio hecho por el mismo profesional desde el panel, cuando pasa, entonces no se le notifica a sí mismo.
- **CA4.** Dado un profesional, cuando pide las notificaciones, entonces solo ve las suyas.

Decisión 2026-10-05: en la v1 el profesional se entera solo por el panel; el WhatsApp al profesional queda para después (PD-18).

### HU-38 · Pedido de reseña sin cansar al cliente

Como **admin** quiero que a mis clientes les llegue un pedido de reseña de Google después del servicio para sumar reseñas sin molestar.
Prioridad: Debe · Tamaño: M · Etapa: 8 · Estado: Pendiente

- **CA1.** Dado un turno marcado "vino" (o terminado sin marcar), cuando pasan 2 horas desde el fin, entonces se envía el pedido con el link de reseña del local.
- **CA2.** Dado un cliente que recibió un pedido hace menos de 90 días, cuando termina otro turno, entonces no se le manda.
- **CA3.** Dado un turno cancelado o "no vino", cuando pasan las 2 horas, entonces no se manda.
- **CA4.** Dado un local sin link de reseña configurado, cuando termina un turno, entonces no se manda.
- **CA5.** Dado que el trabajo corre dos veces, cuando termina, entonces hay un solo pedido.

### HU-39 · Estado de entrega del mensaje

Como **admin** quiero ver si el mensaje al cliente se entregó para llamarlo si no le llegó.
Prioridad: Debería · Tamaño: S · Etapa: 8 · Estado: Pendiente

- **CA1.** Dado un turno, cuando lo abro en la agenda, entonces veo el estado de cada mensaje (pendiente, enviado, falló).
- **CA2.** Dado un mensaje fallido, cuando miro la agenda del día, entonces el turno aparece marcado.

---

## Para después (fuera de la v1)

No se implementan. Quedan anotadas para no perderlas.

| ID    | Idea                                                                | Origen                        |
| ----- | ------------------------------------------------------------------- | ----------------------------- |
| PD-01 | Lista de espera                                                     | Doc 01 §1.3                   |
| PD-02 | Fidelización ("10.º corte con descuento")                           | Doc 01 §1.3                   |
| PD-03 | Estadísticas avanzadas                                              | Doc 01 §1.3                   |
| PD-04 | Cupones y promociones                                               | Doc 01 §1.3                   |
| PD-05 | App instalable y notificaciones push                                | Doc 01 §1.3, doc 02 §2        |
| PD-06 | Mercado Pago: cobro, seña y apartado hasta que entra el pago        | Doc 01 §1.2 (extra pago)      |
| PD-07 | Catálogo de productos                                               | Doc 01 §1.2 (extra pago)      |
| PD-08 | Envío real por WhatsApp (adaptador preparado, apagado)              | Prompt 2026-10-05             |
| PD-09 | Reseñas de Google en la home (Places API, requiere tarjeta)         | Doc 01 §2.1, doc 02 §3        |
| PD-10 | Galería de trabajos                                                 | Doc 01 §2.1 (sugerencia)      |
| PD-11 | Liberar el turno si el cliente no toca "Confirmo"                   | Decisión 2026-10-02           |
| PD-12 | Bloquear o exigir seña a clientes que faltan seguido                | Doc 01 §1.4                   |
| PD-13 | Verificar el teléfono con código en la primera reserva              | Doc 01 §1.4 y §2.1 paso 5     |
| PD-14 | Dominio propio del local y número de WhatsApp propio                | Doc 01 intro, doc 02 §2       |
| PD-15 | "Agregar al calendario" en la confirmación                          | Doc 01 §2.1 paso 6            |
| PD-16 | Ofrecer WhatsApp de reprogramación a los turnos de un día bloqueado | Doc 01 §3.5                   |
| PD-17 | Recuperar contraseña del panel por mail                             | Hueco detectado en la Etapa 0 |
| PD-18 | WhatsApp al profesional por cada reserva, cancelación o cambio      | Decisión 2026-10-05           |
