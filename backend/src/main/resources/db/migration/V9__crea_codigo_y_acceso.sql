-- V9: entrar con teléfono y código (HU-22). El cliente pide un código, lo recibe (en la v1, por un envío simulado)
-- y, si es correcto, recibe un acceso de 30 días para ver y gestionar sus turnos del local desde cualquier navegador.

CREATE TABLE codigo_verificacion (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    local_id      uuid NOT NULL REFERENCES local(id),
    -- Normalizado como en cliente. Sin clave foránea a cliente a propósito: se guarda una fila por cada pedido,
    -- tenga o no turnos ese teléfono, así la respuesta y el límite por hora son iguales para todos (CA2 y CA6).
    telefono      text NOT NULL CHECK (telefono ~ '^\+[0-9]{1,15}$'),
    -- Hash del código (nunca el código, CA7), con el id de la fila como sal.
    codigo_hash   text NOT NULL,
    -- Intentos fallidos. Con 5 el código queda inválido (CA4).
    intentos      integer NOT NULL DEFAULT 0 CHECK (intentos BETWEEN 0 AND 5),
    creado_en     timestamptz NOT NULL,
    vence_en      timestamptz NOT NULL CHECK (vence_en > creado_en),
    -- Cuándo se usó bien. Un código usado no vale de nuevo (CA5).
    usado_en      timestamptz
);

-- Para contar los pedidos de la última hora de un teléfono y encontrar su último código.
CREATE INDEX codigo_verificacion_telefono ON codigo_verificacion (local_id, telefono, creado_en);

CREATE TABLE acceso_cliente (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    local_id      uuid NOT NULL,
    cliente_id    uuid NOT NULL,
    -- Hash del token que guarda el navegador (como el token de "Mi turno": nunca el token).
    token_hash    text NOT NULL UNIQUE,
    creado_en     timestamptz NOT NULL,
    vence_en      timestamptz NOT NULL CHECK (vence_en > creado_en),

    FOREIGN KEY (local_id, cliente_id) REFERENCES cliente (local_id, id)
);
