-- V12: avisos al profesional en el panel (HU-37, D7). Se crean en la misma transacción que el cambio del turno, así
-- no hay un aviso de algo que al final no pasó ni un cambio sin su aviso.

CREATE TABLE notificacion (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    local_id         uuid NOT NULL,
    profesional_id   uuid NOT NULL,
    -- El turno como quedó: el reservado, el cancelado, el nuevo de una reprogramación o el movido.
    turno_id         uuid NOT NULL,
    tipo             text NOT NULL CHECK (tipo IN ('reserva', 'cancelacion', 'reprogramacion', 'movido')),
    -- En una reprogramación o un turno movido, cuándo era antes.
    inicio_anterior  timestamptz,
    creada_en        timestamptz NOT NULL,
    leida_en         timestamptz,

    FOREIGN KEY (local_id, profesional_id) REFERENCES profesional (local_id, id),
    FOREIGN KEY (local_id, turno_id) REFERENCES turno (local_id, id),
    CHECK ((tipo IN ('reprogramacion', 'movido')) = (inicio_anterior IS NOT NULL))
);

-- Las de un profesional, de la más nueva a la más vieja (la lista y el contador de no leídas).
CREATE INDEX notificacion_profesional ON notificacion (profesional_id, creada_en DESC);
