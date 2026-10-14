-- V3: profesionales de cada local y qué servicios hace cada uno.

CREATE TABLE profesional (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    local_id           uuid NOT NULL REFERENCES local(id),
    nombre             text NOT NULL CHECK (char_length(nombre) BETWEEN 1 AND 80),
    foto_url           text,
    telefono_whatsapp  text CHECK (telefono_whatsapp ~ '^\+[0-9]{1,15}$'),
    orden              integer NOT NULL DEFAULT 0,
    activo             boolean NOT NULL DEFAULT true,

    UNIQUE (local_id, id)
);

CREATE TABLE profesional_servicio (
    local_id          uuid NOT NULL,
    profesional_id    uuid NOT NULL,
    servicio_id       uuid NOT NULL,

    PRIMARY KEY (profesional_id, servicio_id),
    FOREIGN KEY (local_id, profesional_id) REFERENCES profesional(local_id, id),
    FOREIGN KEY (local_id, servicio_id) REFERENCES servicio(local_id, id)
);