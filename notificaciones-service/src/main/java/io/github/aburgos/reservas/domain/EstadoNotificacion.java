package io.github.aburgos.reservas.domain;

public enum EstadoNotificacion {
    /** Registrada y sin intentar todavía (o interrumpida antes de saber el resultado). */
    PENDIENTE,
    ENVIADA,
    /** El último intento falló; se reintenta hasta agotar los intentos. */
    FALLIDA
}
