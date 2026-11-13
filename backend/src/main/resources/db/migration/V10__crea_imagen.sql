-- V10: imágenes del local (logo y fotos de profesionales), guardadas en Postgres (D10, decidido el 2026-11-13).
-- Así todo queda en la misma base y el mismo backup, sin servicios externos. Una imagen no se edita: si se cambia
-- la foto, se sube otra (con otro id), y por eso se puede servir con caché larga.

CREATE TABLE imagen (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    local_id    uuid NOT NULL REFERENCES local(id),
    -- El tipo lo decide la API mirando el contenido del archivo, no lo que dice el navegador.
    tipo        text NOT NULL CHECK (tipo IN ('image/png', 'image/jpeg', 'image/webp')),
    -- Hasta 500 KB (512.000 bytes).
    datos       bytea NOT NULL CHECK (octet_length(datos) BETWEEN 1 AND 512000),
    creado_en   timestamptz NOT NULL,

    UNIQUE (local_id, id)
);
