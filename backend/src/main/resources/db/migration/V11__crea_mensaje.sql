-- V11: mensajes al cliente (Etapa 8, D2). La tabla es también la cola: un trabajo programado toma los pendientes
-- con SELECT … FOR UPDATE SKIP LOCKED, así dos instancias de la API nunca mandan el mismo mensaje.

CREATE TABLE mensaje (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    local_id        uuid NOT NULL,
    turno_id        uuid NOT NULL,
    tipo            text NOT NULL CHECK (tipo IN ('confirmacion', 'recordatorio', 'resena')),
    -- pendiente: espera su hora (o un reintento); enviado; fallido: agotó los reintentos (HU-39);
    -- descartado: a su hora ya no correspondía mandarlo (turno cancelado, cliente sin teléfono…).
    estado          text NOT NULL DEFAULT 'pendiente'
                    CHECK (estado IN ('pendiente', 'enviado', 'fallido', 'descartado')),
    -- El link personal del turno. El token se guarda solo hasheado en turno, así que el link entra acá al reservar
    -- y se borra cuando el mensaje termina (enviado, fallido o descartado): la base no queda con links en claro.
    link            text,
    -- Cuándo toca mandarlo. En cada reintento se corre para adelante.
    enviar_en       timestamptz NOT NULL,
    intentos        integer NOT NULL DEFAULT 0 CHECK (intentos BETWEEN 0 AND 4),
    ultimo_error    text CHECK (char_length(ultimo_error) <= 300),
    creado_en       timestamptz NOT NULL,
    terminado_en    timestamptz,

    -- Un solo mensaje de cada tipo por turno: si algo lo encola dos veces, la base frena el segundo.
    UNIQUE (turno_id, tipo),
    FOREIGN KEY (local_id, turno_id) REFERENCES turno (local_id, id),
    CHECK (estado = 'pendiente' OR (terminado_en IS NOT NULL AND link IS NULL))
);

-- Lo que busca el trabajo en cada vuelta: los pendientes a los que ya les toca.
CREATE INDEX mensaje_pendiente ON mensaje (enviar_en) WHERE estado = 'pendiente';
