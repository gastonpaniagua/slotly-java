-- V4: horario semanal de cada profesional, en hora del local. Una pausa son dos franjas.

-- Tipo rango para horas del día: Postgres no trae uno para "time".
CREATE TYPE rango_hora AS RANGE (subtype = time);

CREATE TABLE horario (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    local_id        uuid NOT NULL,
    profesional_id  uuid NOT NULL,
    dia_semana     integer NOT NULL CHECK (dia_semana BETWEEN 0 AND 6),
    hora_inicio     time NOT NULL,
    hora_fin        time NOT NULL,
   
    CHECK (hora_fin > hora_inicio),
    FOREIGN KEY (local_id, profesional_id) REFERENCES profesional(local_id, id),
    EXCLUDE USING gist (profesional_id WITH =, dia_semana WITH =, rango_hora(hora_inicio, hora_fin) WITH &&)
);