package io.github.aburgos.reservas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Notificación por correo derivada de un evento. Guarda el evento original ({@code payload}) para poder
 * reintentar el envío sin volver a Kafka: el evento ya está marcado como procesado y no se reentregaría.
 */
@Entity
@Table(name = "notificacion")
public class Notificacion {

    private static final int LONGITUD_MAXIMA_ERROR = 2000;

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    /** Referencia lógica a la reserva (otro servicio): sin clave foránea. */
    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacion tipo;

    @Column(nullable = false, length = 320)
    private String destinatario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoNotificacion estado;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error")
    private String ultimoError;

    /** Evento original en JSON, para rehacer el correo en un reintento. */
    @Column(nullable = false)
    private String payload;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "enviada_en")
    private Instant enviadaEn;

    protected Notificacion() {
        // requerido por JPA
    }

    private Notificacion(UUID id, UUID eventId, UUID reservaId, TipoNotificacion tipo, String destinatario,
                         String payload, Instant creadaEn) {
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.eventId = Objects.requireNonNull(eventId, "eventId es obligatorio");
        this.reservaId = Objects.requireNonNull(reservaId, "reservaId es obligatorio");
        this.tipo = Objects.requireNonNull(tipo, "tipo es obligatorio");
        if (destinatario == null || destinatario.isBlank()) {
            throw new IllegalArgumentException("El destinatario es obligatorio");
        }
        this.destinatario = destinatario;
        this.payload = Objects.requireNonNull(payload, "payload es obligatorio");
        this.creadaEn = Objects.requireNonNull(creadaEn, "creadaEn es obligatoria");
        this.estado = EstadoNotificacion.PENDIENTE;
        this.intentos = 0;
    }

    public static Notificacion nueva(UUID eventId, UUID reservaId, TipoNotificacion tipo, String destinatario,
                                     String payload, Clock clock) {
        return new Notificacion(UUID.randomUUID(), eventId, reservaId, tipo, destinatario, payload, clock.instant());
    }

    public void marcarEnviada(Clock clock) {
        this.intentos++;
        this.estado = EstadoNotificacion.ENVIADA;
        this.enviadaEn = clock.instant();
        this.ultimoError = null;
    }

    public void registrarFallo(String error) {
        this.intentos++;
        this.estado = EstadoNotificacion.FALLIDA;
        this.ultimoError = recortar(error);
    }

    /** Se puede reintentar si no se envió y no se agotaron los intentos. */
    public boolean reintentable(int maxIntentos) {
        return estado != EstadoNotificacion.ENVIADA && intentos < maxIntentos;
    }

    private static String recortar(String error) {
        if (error == null) {
            return "error desconocido";
        }
        return error.length() <= LONGITUD_MAXIMA_ERROR ? error : error.substring(0, LONGITUD_MAXIMA_ERROR);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getReservaId() {
        return reservaId;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public EstadoNotificacion getEstado() {
        return estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getEnviadaEn() {
        return enviadaEn;
    }
}
