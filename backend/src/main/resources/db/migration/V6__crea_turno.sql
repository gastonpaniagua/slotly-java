-- V6: turnos (reservas, turnos manuales, bloqueos y apartados en una sola tabla) y sus servicios.
-- Que dos turnos de un profesional nunca se pisen lo garantiza el constraint de exclusión, no el código.

CREATE TABLE turno (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    local_id            uuid NOT NULL,
    profesional_id      uuid NOT NULL,
    cliente_id          uuid,
    tipo                text NOT NULL CHECK (tipo IN ('reserva', 'manual', 'bloqueo')),
    estado              text NOT NULL CHECK (estado IN ('apartado', 'confirmado', 'cancelado', 'vencido', 'completado', 'ausente')),
    inicio              timestamptz NOT NULL,
    fin                 timestamptz NOT NULL,
    ocupado_hasta       timestamptz NOT NULL,
    vence_en            timestamptz,
    token_hash          text UNIQUE,
    token_vence_en      timestamptz,
    forma_pago          text CHECK (forma_pago IN ('efectivo', 'transferencia')),
    nota                text CHECK (char_length(nota) <= 300),
    motivo              text CHECK (char_length(motivo) <= 120),
    reprogramado_de_id  uuid,

    UNIQUE (local_id, id),

    -- Coherencia del turno (doc 03)
    CHECK (fin > inicio),
    CHECK (ocupado_hasta >= fin),
    CHECK (estado <> 'apartado' OR vence_en IS NOT NULL),
    CHECK (tipo <> 'bloqueo' OR cliente_id IS NULL),
    CHECK (tipo = 'bloqueo' OR estado = 'apartado' OR cliente_id IS NOT NULL),

    -- Multi-tenant: todo lo que el turno referencia es del mismo local
    FOREIGN KEY (local_id, profesional_id) REFERENCES profesional (local_id, id),
    FOREIGN KEY (local_id, cliente_id) REFERENCES cliente (local_id, id),
    FOREIGN KEY (local_id, reprogramado_de_id) REFERENCES turno (local_id, id),

    -- Dos turnos de un profesional nunca se pisan. Los cancelados y vencidos no cuentan.
    CONSTRAINT turno_sin_superposicion
        EXCLUDE USING gist (profesional_id WITH =, tstzrange(inicio, ocupado_hasta, '[)') WITH &&)
        WHERE (estado NOT IN ('cancelado', 'vencido'))
);

CREATE TABLE turno_servicio (
    local_id            uuid NOT NULL,
    turno_id            uuid NOT NULL,
    servicio_id         uuid NOT NULL,
    duracion_min        integer NOT NULL CHECK (duracion_min BETWEEN 5 AND 480),
    precio              integer NOT NULL CHECK (precio >= 0),

    PRIMARY KEY (turno_id, servicio_id),
    FOREIGN KEY (local_id, turno_id) REFERENCES turno (local_id, id),
    FOREIGN KEY (local_id, servicio_id) REFERENCES servicio (local_id, id)
);
