package io.github.aburgos.reservas.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Horas locales del recurso, por ejemplo "09:00". */
public record FranjaAperturaDto(@NotNull DayOfWeek dia, @NotNull LocalTime apertura, @NotNull LocalTime cierre) {
}
