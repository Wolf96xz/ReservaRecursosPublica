package io.github.aburgos.reservas.infrastructure.mail;

import io.github.aburgos.reservas.event.ReservaCancelada;
import io.github.aburgos.reservas.event.ReservaCreada;
import io.github.aburgos.reservas.event.ReservaEvent;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Redacta el correo de cada tipo de evento. Las horas se muestran en la <b>zona del recurso</b> (la que
 * viaja en el evento), nunca en la del servidor: una reserva en {@code Europe/Madrid} se ve a la hora de
 * Madrid aunque el servicio corra en UTC, y los cambios de hora de marzo y octubre salen bien.
 */
public final class PlantillaCorreo {

    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", ES);

    private PlantillaCorreo() {
    }

    public record Correo(String asunto, String cuerpo) {
    }

    public static Correo renderizar(ReservaEvent evento) {
        return switch (evento) {
            case ReservaCreada creada -> new Correo(
                    "Reserva confirmada: " + creada.recursoNombre(),
                    cuerpo("Tu reserva ha quedado confirmada.", creada));
            case ReservaCancelada cancelada -> new Correo(
                    "Reserva cancelada: " + cancelada.recursoNombre(),
                    cuerpo("Tu reserva ha sido cancelada.", cancelada));
        };
    }

    private static String cuerpo(String frase, ReservaEvent evento) {
        ZoneId zona = ZoneId.of(evento.zona());
        var inicio = evento.inicio().atZone(zona);
        var fin = evento.fin().atZone(zona);
        return """
                Hola,

                %s

                Recurso: %s
                Fecha: %s
                Horario: %s - %s (%s)

                Un saludo.
                """.formatted(frase, evento.recursoNombre(), FECHA.format(inicio),
                HORA.format(inicio), HORA.format(fin), zona.getId());
    }
}
