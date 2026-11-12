-- V7: un apartado que vence o se libera pasa a 'vencido' sin haber tenido cliente (doc 03, "Apartados vencidos").
-- La regla de la V6 solo dejaba una reserva sin cliente en estado 'apartado' y rechazaba ese final natural.
-- Se reemplaza por una con nombre, así los errores y los tests la pueden citar.

ALTER TABLE turno DROP CONSTRAINT turno_check4;

ALTER TABLE turno ADD CONSTRAINT turno_reserva_con_cliente
    CHECK (tipo = 'bloqueo' OR estado IN ('apartado', 'vencido') OR cliente_id IS NOT NULL);
