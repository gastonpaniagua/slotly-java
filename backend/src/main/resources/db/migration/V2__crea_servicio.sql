-- V2: servicios que ofrece cada local.

CREATE TABLE servicio (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),


    local_id        uuid NOT NULL REFERENCES local(id),
    nombre          text NOT NULL CHECK (char_length(nombre) BETWEEN 1 AND 80),
    descripcion     text CHECK (char_length(descripcion) <= 300),
    duracion_min    integer NOT NULL CHECK (duracion_min BETWEEN 5 AND 480 AND duracion_min % 5 = 0),
    precio          integer NOT NULL CHECK (precio >= 0),
    limpieza_min    integer CHECK (limpieza_min BETWEEN 0 AND 30),
    orden           integer NOT NULL DEFAULT 0,
    foto_url        text,
    activo          boolean NOT NULL DEFAULT true,
    
    UNIQUE (local_id, id)
);