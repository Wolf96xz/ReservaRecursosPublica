package io.github.aburgos.reservas.infrastructure.web.dto;

import io.github.aburgos.reservas.domain.model.TipoRecurso;

import java.util.List;
import java.util.UUID;

public record RecursoResponse(UUID id, String nombre, TipoRecurso tipo, String zona, boolean activo,
                              List<FranjaAperturaDto> horario) {
}
