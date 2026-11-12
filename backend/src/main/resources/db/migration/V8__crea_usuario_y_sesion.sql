-- V8: usuarios del panel (HU-16) y las tablas donde Spring Session guarda las sesiones (D1).

CREATE TABLE usuario (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    local_id         uuid NOT NULL REFERENCES local(id),
    -- Se guarda en minúsculas (lo hace la API) para que "Ana@Demo.com" y "ana@demo.com" sean el mismo usuario.
    email            text NOT NULL CHECK (email = lower(email) AND email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    -- Hash argon2id con su sal y parámetros, en el formato de Spring Security ($argon2id$v=19$...). Nunca la contraseña.
    password_hash    text NOT NULL CHECK (password_hash LIKE '$argon2id$%'),
    rol              text NOT NULL CHECK (rol IN ('admin', 'profesional')),
    -- El profesional cuya agenda ve. Obligatorio para un profesional; opcional para el admin (el dueño que también corta).
    profesional_id   uuid,
    activo           boolean NOT NULL DEFAULT true,

    -- El login es por subdominio: el mismo mail puede tener cuenta en dos locales, pero no dos veces en el mismo.
    UNIQUE (local_id, email),
    -- Un profesional tiene a lo sumo un usuario.
    UNIQUE (local_id, profesional_id),
    -- El profesional tiene que ser del mismo local que el usuario (misma regla que el resto del multi-tenant).
    FOREIGN KEY (local_id, profesional_id) REFERENCES profesional(local_id, id),
    CONSTRAINT usuario_profesional_con_agenda CHECK (rol = 'admin' OR profesional_id IS NOT NULL)
);

-- Tablas de Spring Session JDBC: copiadas tal cual del schema-postgresql.sql de spring-session-jdbc 4.1.1.
-- Las crea Flyway (y no Spring Session al arrancar) para que la base de tests y la de producción sean iguales.
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BYTEA NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
