-- V13: cada aviso guarda cuándo era el turno en el momento del aviso (HU-37). Antes la lista mostraba el horario
-- actual del turno: si después lo movía otra persona, los avisos viejos mostraban el horario nuevo.

ALTER TABLE notificacion ADD COLUMN inicio timestamptz;

-- Los avisos que ya existen se quedan con el horario actual del turno: no hay forma de saber el de ese momento.
UPDATE notificacion n SET inicio = t.inicio FROM turno t WHERE t.local_id = n.local_id AND t.id = n.turno_id;

ALTER TABLE notificacion ALTER COLUMN inicio SET NOT NULL;
