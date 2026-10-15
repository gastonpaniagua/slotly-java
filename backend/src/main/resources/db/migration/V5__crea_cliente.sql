-- V5: clientes de cada local. Se identifican por teléfono (normalizado); sin teléfono solo en turnos manuales.

CREATE TABLE cliente (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    local_id                 uuid NOT NULL REFERENCES local(id),
    nombre                   text NOT NULL CHECK (char_length(nombre) BETWEEN 1 AND 80),
    telefono                 text CHECK (telefono ~ '^\+[0-9]{1,15}$'),
    email                    text CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    consentimiento_en        timestamptz,
    ultima_resena_pedida_en  timestamptz,
    UNIQUE (local_id, telefono),
    UNIQUE (local_id, id)
);