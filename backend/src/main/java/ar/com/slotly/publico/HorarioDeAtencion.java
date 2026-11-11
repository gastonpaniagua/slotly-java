package ar.com.slotly.publico;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ar.com.slotly.profesional.Horario;

// Arma el horario de atención del local juntando las franjas de sus profesionales (doc 00).
// Las franjas que se pisan o se tocan se unen: Ana 10-13 y Bruno 12-15 → el local atiende de 10 a 15.
final class HorarioDeAtencion {

    private HorarioDeAtencion() {
    }

    // Devuelve siempre los 7 días, de domingo (0) a sábado (6); los días sin franjas quedan con la lista vacía.
    static List<DiaPublicoResponse> calcular(List<Horario> horarios) {
        List<DiaPublicoResponse> dias = new ArrayList<>();
        for (int dia = 0; dia <= 6; dia++) {
            int diaActual = dia;
            List<Horario> delDia = horarios.stream()
                    .filter(h -> h.getDiaSemana() == diaActual)
                    .sorted(Comparator.comparing(Horario::getHoraInicio))
                    .toList();
            dias.add(new DiaPublicoResponse(dia, unir(delDia)));
        }
        return dias;
    }

    // Recibe las franjas de un día ordenadas por inicio y junta las que se pisan o se tocan.
    private static List<FranjaPublicaResponse> unir(List<Horario> ordenadas) {
        List<FranjaPublicaResponse> franjas = new ArrayList<>();
        LocalTime desde = null;
        LocalTime hasta = null;
        for (Horario h : ordenadas) {
            if (desde == null) {
                desde = h.getHoraInicio();
                hasta = h.getHoraFin();
            } else if (!h.getHoraInicio().isAfter(hasta)) {
                // Empieza antes o justo cuando termina la actual: se estira la franja.
                if (h.getHoraFin().isAfter(hasta)) {
                    hasta = h.getHoraFin();
                }
            } else {
                // Hay un hueco: se cierra la franja actual y empieza otra.
                franjas.add(new FranjaPublicaResponse(desde, hasta));
                desde = h.getHoraInicio();
                hasta = h.getHoraFin();
            }
        }
        if (desde != null) {
            franjas.add(new FranjaPublicaResponse(desde, hasta));
        }
        return franjas;
    }
}
