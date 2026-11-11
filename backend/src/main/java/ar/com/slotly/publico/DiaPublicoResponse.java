package ar.com.slotly.publico;

import java.util.List;

// Un día del horario de atención. diaSemana va de 0 (domingo) a 6 (sábado), como en la base.
// Si franjas está vacía, ese día el local está cerrado.
public record DiaPublicoResponse(
        int diaSemana,
        List<FranjaPublicaResponse> franjas
) {
}
