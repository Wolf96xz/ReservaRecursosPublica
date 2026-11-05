package io.github.aburgos.reservas.application.command;

import io.github.aburgos.reservas.domain.model.HorarioApertura;
import io.github.aburgos.reservas.domain.model.TipoRecurso;

import java.time.ZoneId;

/** {@code zona} puede ser null: se usa la zona por defecto del recurso (Europe/Madrid). */
public record CrearRecursoCommand(String nombre, TipoRecurso tipo, ZoneId zona, HorarioApertura horario) {
}
