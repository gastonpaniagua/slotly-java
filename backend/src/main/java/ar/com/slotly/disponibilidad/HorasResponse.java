package ar.com.slotly.disponibilidad;

import java.time.LocalDate;
import java.util.List;

// Las horas libres de un día: van como texto "10:15" en hora del local, ya ordenadas. La usan la reserva (HU-07)
// y reprogramar (HU-21).
public record HorasResponse(LocalDate fecha, List<String> horas) {
}
