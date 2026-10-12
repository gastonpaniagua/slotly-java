-- V1: extensión para el constraint de exclusión y tabla local (raíz del multi-tenant).

-- btree_gist permite combinar "=" (mismo profesional) con "&&" (rangos que se cruzan)
-- en un constraint de exclusión. Se usa en la V6.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE local (
    -- Identidad
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre                text NOT NULL CHECK (char_length(nombre) BETWEEN 1 AND 80),
    subdominio            text NOT NULL UNIQUE CHECK (subdominio ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$'),
    zona_horaria          text NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    activo                boolean NOT NULL DEFAULT true,

    -- Marca
    descripcion           text,
    logo_url              text,
    color_acento          text CHECK (color_acento ~ '^#[0-9a-f]{6}$'),

    -- Contacto (todo opcional; en las redes se guarda el usuario, no la URL)
    whatsapp              text CHECK (whatsapp ~ '^\+[0-9]{1,15}$'),
    instagram             text CHECK (instagram ~ '^[a-zA-Z0-9._]{1,30}$'),
    tiktok                text CHECK (tiktok ~ '^[a-zA-Z0-9._]{1,30}$'),
    facebook              text CHECK (facebook ~ '^[a-zA-Z0-9.]{1,50}$'),

    -- Ubicación
    direccion             text CHECK (char_length(direccion) BETWEEN 1 AND 120),
    ciudad                text CHECK (char_length(ciudad) BETWEEN 1 AND 80),
    latitud               numeric(9,6) CHECK (latitud BETWEEN -90 AND 90),
    longitud              numeric(9,6) CHECK (longitud BETWEEN -180 AND 180),

    -- Pago
    alias                 text CHECK (char_length(alias) BETWEEN 1 AND 80),
    titular_cuenta        text CHECK (char_length(titular_cuenta) BETWEEN 1 AND 80),
    cbu                   text CHECK (cbu ~ '^[0-9]{22}$'),
    link_resena           text,

        -- Reglas de turnos (doc 03)
    intervalo_min         integer NOT NULL DEFAULT 15 CHECK (intervalo_min BETWEEN 5 AND 60),
    limpieza_min          integer NOT NULL DEFAULT 0  CHECK (limpieza_min BETWEEN 0 AND 30),
    anticipacion_min      integer NOT NULL DEFAULT 60 CHECK (anticipacion_min BETWEEN 0 AND 1440),
    tope_dias             integer NOT NULL DEFAULT 30 CHECK (tope_dias BETWEEN 1 AND 365),
    cancelar_hasta_horas  integer NOT NULL DEFAULT 3  CHECK (cancelar_hasta_horas BETWEEN 0 AND 168),
    max_turnos_futuros    integer NOT NULL DEFAULT 2  CHECK (max_turnos_futuros BETWEEN 1 AND 10),
    apartado_min          integer NOT NULL DEFAULT 5  CHECK (apartado_min BETWEEN 1 AND 30)
);