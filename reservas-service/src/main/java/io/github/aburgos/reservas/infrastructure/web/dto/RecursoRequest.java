package io.github.aburgos.reservas.infrastructure.web.dto;

import io.github.aburgos.reservas.domain.model.TipoRecurso;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** {@code zona} es opcional (id de zona IANA); por defecto Europe/Madrid. */
public record RecursoRequest(@NotBlank String nombre, @NotNull TipoRecurso tipo, String zona,
                             List<@Valid @NotNull FranjaAperturaDto> horario) {
}
