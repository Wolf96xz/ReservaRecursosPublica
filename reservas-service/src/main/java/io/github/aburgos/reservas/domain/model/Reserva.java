package io.github.aburgos.reservas.domain.model;

import io.github.aburgos.reservas.domain.exception.CancelacionNoPermitidaException;
import io.github.aburgos.reservas.domain.policy.PoliticaCancelacion;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root de las reservas. Guarda instantes UTC; la lógica de cancelación vive aquí.
 * <p>
 * {@code @Version} protege contra modificaciones concurrentes de la MISMA reserva (por ejemplo,
 * dos cancelaciones simultáneas). No evita el solapamiento entre reservas distintas del mismo
 * recurso (son filas distintas): eso lo garantiza la restricción de exclusión de la base de datos.
 */
@Entity
@Table(name = "reserva")
public class Reserva {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID recursoId;

    @Column(nullable = false)
    private String usuarioId;

    @Embedded
    private FranjaHoraria franja;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReserva estado;

    @Column(nullable = false)
    private Instant creadaEn;

    private Instant canceladaEn;

    @Version
    private Long version;

    protected Reserva() {
        // requerido por JPA
    }

    public Reserva(UUID id, UUID recursoId, String usuarioId, FranjaHoraria franja,
                   EstadoReserva estado, Instant creadaEn, Instant canceladaEn) {
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.recursoId = Objects.requireNonNull(recursoId, "recursoId es obligatorio");
        this.usuarioId = validarUsuario(usuarioId);
        this.franja = Objects.requireNonNull(franja, "franja es obligatoria");
        this.estado = Objects.requireNonNull(estado, "estado es obligatorio");
        this.creadaEn = Objects.requireNonNull(creadaEn, "creadaEn es obligatoria");
        this.canceladaEn = canceladaEn;
    }

    /**
     * Crea una reserva CONFIRMADA tras validar el recurso (activo y dentro de horario).
     * La comprobación de solapamiento con otras reservas requiere consultar el repositorio
     * y se hace en la capa de aplicación (más la restricción en base de datos).
     */
    public static Reserva crear(Recurso recurso, String usuarioId, FranjaHoraria franja, Clock clock) {
        Objects.requireNonNull(recurso, "recurso es obligatorio");
        Objects.requireNonNull(clock, "clock es obligatorio");
        recurso.validarReserva(franja);
        return new Reserva(UUID.randomUUID(), recurso.getId(), usuarioId, franja,
                EstadoReserva.CONFIRMADA, clock.instant(), null);
    }

    /**
     * Cancela la reserva si sigue confirmada y se respeta la antelación mínima de la política.
     *
     * @throws CancelacionNoPermitidaException si ya estaba cancelada o se incumple la antelación
     */
    public void cancelar(Clock clock, PoliticaCancelacion politica) {
        Objects.requireNonNull(clock, "clock es obligatorio");
        Objects.requireNonNull(politica, "politica es obligatoria");
        if (estado == EstadoReserva.CANCELADA) {
            throw new CancelacionNoPermitidaException("La reserva " + id + " ya está cancelada");
        }
        Instant ahora = clock.instant();
        if (!politica.permiteCancelar(franja.inicio(), ahora)) {
            throw new CancelacionNoPermitidaException(
                    "La reserva " + id + " solo puede cancelarse con una antelación mínima de "
                            + politica.antelacionMinima());
        }
        this.estado = EstadoReserva.CANCELADA;
        this.canceladaEn = ahora;
    }

    /** Una reserva solo bloquea la franja de su recurso mientras está CONFIRMADA. */
    public boolean bloquea(UUID otroRecursoId, FranjaHoraria otraFranja) {
        return estado == EstadoReserva.CONFIRMADA
                && recursoId.equals(otroRecursoId)
                && franja.solapaCon(otraFranja);
    }

    private static String validarUsuario(String usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        if (usuarioId.isBlank()) {
            throw new IllegalArgumentException("usuarioId no puede estar vacío");
        }
        return usuarioId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecursoId() {
        return recursoId;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public FranjaHoraria getFranja() {
        return franja;
    }

    public EstadoReserva getEstado() {
        return estado;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getCanceladaEn() {
        return canceladaEn;
    }

    public Long getVersion() {
        return version;
    }
}
