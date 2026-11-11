package ar.com.slotly.publico;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

// Una franja de atención en hora del local ("10:00" a "13:00").
public record FranjaPublicaResponse(
        @JsonFormat(pattern = "HH:mm") LocalTime desde,
        @JsonFormat(pattern = "HH:mm") LocalTime hasta
) {
}
