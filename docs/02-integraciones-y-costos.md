# Slotly · Integraciones y costos (v1)

Hilo 2 del plan. Parte de las cifras de Gaston (WhatsApp ~37 mil por 1.000 mensajes, hosting ~15 mil, reseñas gratis) y de la definición del producto (`01-definicion-producto.md`). Precios verificados el 2026-10-02.

**Idea central:** el costo se divide en dos.

- **Fijo de la plataforma** (hosting, dominio): se paga una vez y se reparte entre todos los locales. Cuantos más locales, menos pesa.
- **Variable por local** (mensajes de WhatsApp): crece con los turnos de cada local. Es el costo que manda.

---

## 1. Supuestos

| Supuesto                                               | Valor                                                                                                                          |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ |
| Dólar                                                  | Oficial Banco Nación venta, 1/10/2026: **$1.545**                                                                              |
| Local típico                                           | 1 peluquero, **200 turnos online por mes** (~8-9 por día, 24 días). Local chico: 100.                                          |
| Cancelaciones o reprogramaciones                       | 10 % de los turnos                                                                                                             |
| Clientes nuevos por mes (piden código de verificación) | 15 % de los turnos                                                                                                             |
| Pedido de reseña                                       | Una vez cada 90 días por cliente → ~1 de cada 3 turnos (los clientes de peluquería vuelven cada 3-5 semanas)                   |
| Impuestos                                              | No incluidos. Si Meta cobra en dólares a una tarjeta argentina pueden sumarse IVA y percepciones (hasta ~30 %). Ver sección 6. |

---

## 2. WhatsApp Business (API oficial de Meta, Cloud API)

**Cómo cobra Meta (desde julio 2025):** por cada mensaje-plantilla entregado, según su categoría. Los mensajes que el negocio responde dentro de las 24 h después de que el cliente escribió son gratis, y las plantillas de tipo "utilidad" mandadas dentro de esa ventana también.

| Categoría                                                 | USD por mensaje (Argentina) | En pesos |
| --------------------------------------------------------- | --------------------------- | -------- |
| Utilidad (confirmación, recordatorio, aviso al peluquero) | 0,026                       | **~$40** |
| Autenticación (código de verificación)                    | 0,026                       | **~$40** |
| Marketing                                                 | 0,0618                      | **~$95** |

Las tarifas son las de Meta usando la API directa, sin intermediarios. Proveedores como Twilio o Salesforce suman un recargo (en la tarifa de Salesforce de mayo 2026 es ~24 % más). Meta da descuentos por volumen en utilidad y autenticación, pero recién a partir de muchos miles de mensajes por mes.

> Tu cálculo de 37 mil por 1.000 mensajes coincide con la tarifa de utilidad (~$37-40 por mensaje). Bien hecho. Lo que cambia es que **esos 1.000 mensajes no son de toda la plataforma: son casi lo que gasta un solo local**.

**Ojo con el pedido de reseña:** Meta revisa cada plantilla y decide su categoría. Un mensaje que pide una reseña suele quedar como **marketing** (más del doble de caro). Lo calculo como marketing para ir a lo seguro; si Meta lo aprueba como utilidad, el costo baja.

### Mensajes por turno

| Evento                                          | A quién             | Categoría     | Por cada           |
| ----------------------------------------------- | ------------------- | ------------- | ------------------ |
| Confirmación con link para cancelar/reprogramar | Cliente             | Utilidad      | Turno              |
| Recordatorio 24 h antes                         | Cliente             | Utilidad      | Turno              |
| Aviso de nueva reserva                          | Peluquero           | Utilidad      | Turno              |
| Aviso de cancelación o cambio + confirmación    | Peluquero + cliente | Utilidad      | Cancelación (10 %) |
| Pedido de reseña                                | Cliente             | Marketing     | 1 de cada 3 turnos |
| Código de verificación                          | Cliente nuevo       | Autenticación | 15 % de turnos     |

### Costo mensual de WhatsApp por local

|                                                                            | Local típico (200 turnos)    | Local chico (100 turnos) |
| -------------------------------------------------------------------------- | ---------------------------- | ------------------------ |
| **Versión completa** (todo por WhatsApp)                                   | ~707 mensajes → **~$33.300** | **~$16.600**             |
| **Versión ahorro** (aviso al peluquero por notificación del panel, gratis) | ~507 mensajes → **~$24.500** | **~$12.200**             |

Detalle del local típico, versión completa: 640 de utilidad ($25.700) + 67 de marketing ($6.400) + 30 de autenticación ($1.200).

### Cómo bajar el costo de WhatsApp

1. **Avisarle al peluquero por notificación push del panel (web instalable en su celular) en vez de WhatsApp.** Es gratis y ahorra 1 de cada 3 mensajes. Se puede dejar WhatsApp como opción paga. _Recomendado._
2. **Pedido de reseña 1 vez cada 90 días** (ya está en la definición). Mandarlo a cada turno lo triplicaría.
3. **Aprovechar la ventana gratis:** si el cliente responde el recordatorio ("dale, voy"), cualquier mensaje de utilidad en las 24 h siguientes es gratis.
4. **Código de verificación solo la primera vez** y recordar el teléfono en el navegador.

### Un número de Slotly o uno por local

- **Un número de Slotly para todos (recomendado para arrancar):** una sola verificación con Meta, una sola cuenta y una sola factura. Cada plantilla lleva el nombre del local ("Barbería Juan: tu turno es el jueves…"). Contra: el cliente ve el número de Slotly.
- **Número propio de cada local:** más "suyo", pero cada local tiene que verificar su negocio con Meta y es más trabajo de alta. Meta ya permite usar el mismo número en la app de WhatsApp Business y en la API a la vez ("coexistencia"), así que es viable como upgrade más adelante.

Requisitos: cuenta de Meta Business verificada, plantillas aprobadas por Meta (tarda de minutos a un día) y **consentimiento del cliente** para recibir WhatsApp (checkbox al reservar).

---

## 3. Reseñas de Google

**Opción A · Places API de Google (recomendada para arrancar).** Devuelve puntaje, cantidad de reseñas y **hasta 5 reseñas** (las que Google elige como más relevantes).

- Precio: el pedido con reseñas cae en la categoría "Enterprise", US$20 cada 1.000 pedidos, con **1.000 pedidos gratis por mes**.
- Si Slotly guarda las reseñas y las actualiza **una vez por día por local**, son ~30 pedidos por local por mes. Hasta ~33 locales: **gratis**. Con 100 locales serían ~US$40/mes. Nunca hay que consultar a Google cada vez que alguien abre la página.
- Requisito: cuenta de Google Cloud con tarjeta cargada (aunque no cobre).
- Hay que mostrar la marca "Google" y el autor de cada reseña.

**Opción B · API de Perfil de Empresa de Google.** Gratis y trae todas las reseñas, pero el local tiene que darle acceso a su perfil a Slotly y Google tiene que aprobar el acceso a la API. Sirve como mejora para después.

**Link para pedir reseña:** cada local tiene un link directo para dejar reseña (se saca de su perfil de Google Maps). Gratis. Se guarda en la configuración del local.

**Mapa y "Cómo llegar":** el mapa embebido de Google (Maps Embed API) y el link a Google Maps son **gratis**.

---

## 4. Hosting y fijos de la plataforma

| Ítem                                    | Opción económica                                                                 | Opción administrada                                          |
| --------------------------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| Servidor web + base de datos PostgreSQL | Servidor virtual (VPS) de 2-4 GB: **~$10-15 mil/mes** (tu cifra)                 | Vercel Pro (US$20) + Supabase Pro (US$25) = **~$70 mil/mes** |
| Dominio `slotly.com.ar`                 | ~**$1-2 mil/mes** prorrateado (se paga anual en NIC Argentina; verificar precio) | Igual                                                        |
| Emails (respaldo de avisos)             | Gratis hasta ~3.000 por mes (Resend, Brevo)                                      | Igual                                                        |
| Google Maps y reseñas                   | Gratis (ver sección 3)                                                           | Igual                                                        |
| **Total fijo**                          | **~$17 mil/mes**                                                                 | **~$72 mil/mes**                                             |

Notas:

- El plan gratis de Vercel no permite uso comercial y el gratis de Supabase se pausa si no tiene uso, así que no sirven para clientes reales.
- La opción económica alcanza de sobra para decenas de locales al principio, pero hay que encargarse de backups y actualizaciones. La elección final va en el **Hilo 4 (tecnología)**.

---

## 5. Costo mensual por local

Fórmula: **costo por local = (fijo ÷ cantidad de locales) + WhatsApp de ese local**.

Con hosting económico ($17 mil fijo), local típico de 200 turnos:

| Locales | Fijo por local | WhatsApp completo | **Total completo** | WhatsApp ahorro | **Total ahorro** |
| ------- | -------------- | ----------------- | ------------------ | --------------- | ---------------- |
| 1       | $17.000        | $33.300           | **$50.300**        | $24.500         | **$41.500**      |
| 5       | $3.400         | $33.300           | **$36.700**        | $24.500         | **$27.900**      |
| 10      | $1.700         | $33.300           | **$35.000**        | $24.500         | **$26.200**      |
| 30      | $570           | $33.300           | **$33.900**        | $24.500         | **$25.100**      |

Local chico (100 turnos), versión ahorro, con 10 locales: **~$13.900**.

**Conclusiones:**

1. Desde el segundo o tercer local, **el hosting casi no pesa; el costo real es WhatsApp**, y depende de cuántos turnos tenga cada local.
2. Un local típico te cuesta **~$25-35 mil por mes**. El precio que le cobres tiene que estar bien por encima de eso, o tiene que trasladar WhatsApp aparte.
3. Dos formas de cobrarlo, a decidir en el **Hilo 3**: un plan que incluya hasta X turnos o mensajes por mes, con excedente; o un precio por turno reservado. Cualquiera de las dos te protege de un local con mucho movimiento.

---

## 6. Mercado Pago, impuestos y privacidad

- **Mercado Pago (extra):** la comisión la paga el local, descontada de cada cobro, y depende del plazo en que quiera la plata (en el momento es lo más caro). **A Slotly no le cuesta nada**; la integración (link de pago o QR) es gratis. Conviene mostrarle al local la tabla vigente de Mercado Pago al vender el extra.
- **Impuestos al pagar servicios del exterior:** Meta, Google, Vercel y Supabase cobran en dólares. Pagado con tarjeta argentina se pueden sumar IVA y percepciones (las percepciones se pueden recuperar según tu situación fiscal). Conviene hablarlo con tu contador antes de facturar; en el peor caso suma ~30 % sobre esos costos.
- **Privacidad (Ley 25.326):** política de privacidad corta en cada web, checkbox de consentimiento para WhatsApp (Meta también lo exige) y opción para que el cliente pida borrar sus datos.

---

## 7. Pendientes

- **Hilo 3:** precio por local a partir de estos costos (piso de ~$35 mil para un local típico), cómo cobrar WhatsApp y el extra Mercado Pago.
- **Hilo 4:** elegir hosting (VPS económico o administrado) y notificaciones push del panel para el peluquero.
- **Confirmado:** número de WhatsApp único de Slotly (2026-10-02) y aviso al peluquero por notificación del panel en la v1 (2026-10-05), o sea la versión ahorro.

### Fuentes

- Meta, precios de WhatsApp Business Platform: https://developers.facebook.com/documentation/business-messaging/whatsapp/pricing
- Tarifa WhatsApp vía Salesforce (mayo 2026), usada para confirmar las tarifas de Argentina: https://www.salesforce.com/en-us/wp-content/uploads/sites/4/WhatsApp-Business-Messaging-Rate-Card-MM-Lite-May-1-2026.pdf
- Google Maps Platform, precios: https://developers.google.com/maps/billing-and-pricing/pricing
- Dólar 1/10/2026, Diario Río Negro: https://www.rionegro.com.ar/economia/dolar-hoy-como-abren-octubre-2026-el-dolar-oficial-el-mep-y-el-ccl-en-el-bpn-y-banco-patagonia-4743370/
