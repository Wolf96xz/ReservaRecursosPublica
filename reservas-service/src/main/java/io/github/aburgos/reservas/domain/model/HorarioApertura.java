package io.github.aburgos.reservas.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Horario semanal fijo de un recurso. Un día sin franjas configuradas es un día cerrado.
 * Las franjas contiguas del mismo día (p. ej. 09-14 y 14-21) se fusionan al construir,
 * de modo que una reserva puede abarcarlas; las que se solapan se rechazan.
 */
public record HorarioApertura(List<FranjaApertura> franjas) {

    public HorarioApertura {
        Objects.requireNonNull(franjas, "franjas es obligatorio");
        franjas = normalizar(franjas);
    }

    /**
     * La reserva es válida si cae entera dentro de una franja de apertura del día local de su inicio.
     * Como ninguna franja cruza la medianoche, una reserva que la cruce nunca queda permitida.
     * La comparación se hace en instantes, de modo que los cambios de hora (DST) se respetan.
     */
    public boolean permite(FranjaHoraria franja, ZoneId zona) {
        Objects.requireNonNull(franja, "franja es obligatoria");
        Objects.requireNonNull(zona, "zona es obligatoria");
        LocalDate fecha = franja.inicio().atZone(zona).toLocalDate();
        for (FranjaApertura f : franjas) {
            if (f.dia() != fecha.getDayOfWeek()) {
                continue;
            }
            Instant abre = fecha.atTime(f.apertura()).atZone(zona).toInstant();
            Instant cierra = fecha.atTime(f.cierre()).atZone(zona).toInstant();
            if (!franja.inicio().isBefore(abre) && !franja.fin().isAfter(cierra)) {
                return true;
            }
        }
        return false;
    }

    private static List<FranjaApertura> normalizar(List<FranjaApertura> entrada) {
        List<FranjaApertura> ordenadas = new ArrayList<>(entrada);
        ordenadas.forEach(f -> Objects.requireNonNull(f, "franja nula"));
        ordenadas.sort(Comparator.comparing(FranjaApertura::dia).thenComparing(FranjaApertura::apertura));

        List<FranjaApertura> resultado = new ArrayList<>();
        for (FranjaApertura actual : ordenadas) {
            if (resultado.isEmpty()) {
                resultado.add(actual);
                continue;
            }
            FranjaApertura previa = resultado.get(resultado.size() - 1);
            if (previa.solapaCon(actual)) {
                throw new IllegalArgumentException("Franjas de apertura solapadas: " + previa + " / " + actual);
            }
            if (previa.dia() == actual.dia() && previa.cierre().equals(actual.apertura())) {
                resultado.set(resultado.size() - 1,
                        new FranjaApertura(previa.dia(), previa.apertura(), actual.cierre()));
            } else {
                resultado.add(actual);
            }
        }
        return List.copyOf(resultado);
    }
}
