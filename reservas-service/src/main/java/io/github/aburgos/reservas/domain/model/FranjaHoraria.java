package io.github.aburgos.reservas.domain.model;

import jakarta.persistence.Embeddable;

import java.time.Instant;
import java.util.Objects;

/**
 * Intervalo temporal semiabierto [inicio, fin) expresado en UTC.
 * Franjas contiguas (fin de una == inicio de otra) NO se solapan.
 */
@Embeddable
public record FranjaHoraria(Instant inicio, Instant fin) {

    public FranjaHoraria {
        Objects.requireNonNull(inicio, "inicio es obligatorio");
        Objects.requireNonNull(fin, "fin es obligatorio");
        if (!inicio.isBefore(fin)) {
            throw new IllegalArgumentException("inicio debe ser anterior a fin: " + inicio + " / " + fin);
        }
    }

    public boolean solapaCon(FranjaHoraria otra) {
        Objects.requireNonNull(otra, "otra es obligatoria");
        return inicio.isBefore(otra.fin) && otra.inicio.isBefore(fin);
    }
}
