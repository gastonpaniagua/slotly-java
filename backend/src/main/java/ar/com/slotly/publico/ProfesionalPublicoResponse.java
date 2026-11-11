package ar.com.slotly.publico;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import ar.com.slotly.profesional.Profesional;

// Un profesional tal como lo ve el cliente al elegir con quién atenderse. Sin teléfono ni datos internos.
public record ProfesionalPublicoResponse(
        UUID id,
        String nombre,
        @Schema(types = {"string", "null"}) String fotoUrl
) {

    static ProfesionalPublicoResponse desde(Profesional profesional) {
        return new ProfesionalPublicoResponse(profesional.getId(), profesional.getNombre(), profesional.getFotoUrl());
    }
}
