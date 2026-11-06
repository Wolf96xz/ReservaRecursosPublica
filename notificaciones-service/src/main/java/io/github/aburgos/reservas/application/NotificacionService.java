package io.github.aburgos.reservas.application;

import io.github.aburgos.reservas.application.port.NotificationSender;
import io.github.aburgos.reservas.domain.Notificacion;
import io.github.aburgos.reservas.domain.TipoNotificacion;
import io.github.aburgos.reservas.event.ReservaCancelada;
import io.github.aburgos.reservas.event.ReservaCreada;
import io.github.aburgos.reservas.event.ReservaEvent;
import io.github.aburgos.reservas.infrastructure.persistence.EventoProcesadoRepository;
import io.github.aburgos.reservas.infrastructure.persistence.NotificacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Convierte eventos de reserva en notificaciones por correo, de forma idempotente.
 *
 * <p><b>Dos fases, deliberadamente separadas.</b> (1) Registrar: en una transacción corta se anota el evento
 * como procesado y se crea la notificación {@code PENDIENTE}; o entra todo o nada. (2) Enviar: fuera de esa
 * transacción (un servidor de correo lento no debe retener una conexión a la base de datos) se manda el
 * correo y se anota el resultado. Si el envío falla, el evento ya consta como procesado y Kafka no lo
 * reentregará, así que el reintento se hace sobre la notificación guardada ({@link #reintentarPendientes()}),
 * no volviendo a Kafka. Un fallo del envío no se propaga al listener.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository notificaciones;
    private final EventoProcesadoRepository eventosProcesados;
    private final NotificationSender sender;
    private final JsonMapper json;
    private final TransactionTemplate transaccion;
    private final Clock clock;
    private final int maxIntentos;
    private final Duration margenReintento;
    private final int lote;

    public NotificacionService(NotificacionRepository notificaciones,
                               EventoProcesadoRepository eventosProcesados,
                               NotificationSender sender,
                               JsonMapper json,
                               TransactionTemplate transaccion,
                               Clock clock,
                               @Value("${notificaciones.reintentos.max-intentos:5}") int maxIntentos,
                               @Value("${notificaciones.reintentos.margen-segundos:30}") long margenSegundos,
                               @Value("${notificaciones.reintentos.lote:50}") int lote) {
        this.notificaciones = notificaciones;
        this.eventosProcesados = eventosProcesados;
        this.sender = sender;
        this.json = json;
        this.transaccion = transaccion;
        this.clock = clock;
        this.maxIntentos = maxIntentos;
        this.margenReintento = Duration.ofSeconds(margenSegundos);
        this.lote = lote;
    }

    /** Trata un evento recibido de Kafka. Seguro ante duplicados: el mismo {@code eventId} solo actúa una vez. */
    public void procesar(ReservaEvent evento) {
        UUID notificacionId = transaccion.execute(estado -> registrar(evento));
        if (notificacionId != null) {
            intentarEnvio(notificacionId);
        }
    }

    /** Reintenta las notificaciones pendientes o fallidas a las que les quedan intentos. Devuelve cuántas tocó. */
    public int reintentarPendientes() {
        List<UUID> ids = transaccion.execute(estado -> notificaciones.idsReintentables(
                maxIntentos, clock.instant().minus(margenReintento), PageRequest.of(0, lote)));
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        ids.forEach(this::intentarEnvio);
        return ids.size();
    }

    /** Fase 1. Devuelve el id de la notificación a enviar, o {@code null} si no hay nada que enviar. */
    private UUID registrar(ReservaEvent evento) {
        int nuevos = eventosProcesados.insertarSiNoExiste(evento.eventId(), clock.instant());
        if (nuevos == 0) {
            log.info("Evento {} ya procesado: se ignora el duplicado", evento.eventId());
            return null;
        }
        if (evento.email() == null || evento.email().isBlank()) {
            // Se deja constancia de que se vio (no se reprocesará), pero no hay a quién escribir.
            log.warn("Evento {} ({}) sin email: no se envía notificación", evento.eventId(), evento.tipo());
            return null;
        }
        Notificacion notificacion = Notificacion.nueva(evento.eventId(), evento.reservaId(), tipoDe(evento),
                evento.email(), json.writeValueAsString(evento), clock);
        notificaciones.save(notificacion);
        return notificacion.getId();
    }

    /** Fase 2: envía fuera de transacción y anota el resultado en otra. */
    private void intentarEnvio(UUID notificacionId) {
        Notificacion pendiente = transaccion.execute(estado -> notificaciones.findById(notificacionId).orElse(null));
        if (pendiente == null || !pendiente.reintentable(maxIntentos)) {
            return;
        }
        String error = null;
        try {
            sender.enviar(leerEvento(pendiente));
        } catch (RuntimeException e) {
            error = e.toString();
            log.warn("No se pudo enviar la notificación {} (intento {}): {}",
                    notificacionId, pendiente.getIntentos() + 1, error);
        }
        String errorFinal = error;
        transaccion.executeWithoutResult(estado -> {
            Notificacion actual = notificaciones.findById(notificacionId).orElseThrow();
            if (errorFinal == null) {
                actual.marcarEnviada(clock);
            } else {
                actual.registrarFallo(errorFinal);
            }
            notificaciones.save(actual);
        });
    }

    private static TipoNotificacion tipoDe(ReservaEvent evento) {
        return switch (evento) {
            case ReservaCreada creada -> TipoNotificacion.RESERVA_CREADA;
            case ReservaCancelada cancelada -> TipoNotificacion.RESERVA_CANCELADA;
        };
    }

    private ReservaEvent leerEvento(Notificacion notificacion) {
        return switch (notificacion.getTipo()) {
            case RESERVA_CREADA -> json.readValue(notificacion.getPayload(), ReservaCreada.class);
            case RESERVA_CANCELADA -> json.readValue(notificacion.getPayload(), ReservaCancelada.class);
        };
    }
}
