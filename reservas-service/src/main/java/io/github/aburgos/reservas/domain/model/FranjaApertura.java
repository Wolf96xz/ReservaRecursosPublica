package io.github.aburgos.reservas.domain.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Tramo de apertura de un día de la semana, en hora local del recurso: [apertura, cierre).
 * No puede cruzar la medianoche, por lo que se exige apertura &lt; cierre en el mismo día.
 */
@Embeddable
public record FranjaApertura(@Enumerated(EnumType.STRING) DayOfWeek dia, LocalTime apertura, LocalTime cierre) {

    public FranjaApertura {
        Objects.requireNonNull(dia, "dia es obligatorio");
        Objects.requireNonNull(apertura, "apertura es obligatoria");
        Objects.requireNonNull(cierre, "cierre es obligatorio");
        if (!apertura.isBefore(cierre)) {
            throw new IllegalArgumentException(
                    "apertura debe ser anterior a cierre (sin cruzar medianoche): " + apertura + " / " + cierre);
        }
    }

    boolean solapaCon(FranjaApertura otra) {
        return dia == otra.dia && apertura.isBefore(otra.cierre) && otra.apertura.isBefore(cierre);
    }
}
