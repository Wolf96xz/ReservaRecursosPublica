package io.github.aburgos.reservas.event;

/** Nombres de topics y cabeceras compartidos entre productor y consumidor. */
public final class Topics {

    /** Un solo topic para todos los eventos de reserva; la clave del mensaje es el id de la reserva. */
    public static final String RESERVAS = "reservas.eventos";

    /** Cabecera con el tipo de evento ({@link ReservaEvent#tipo()}), para que el consumidor sepa qué leer. */
    public static final String HEADER_TIPO_EVENTO = "eventType";

    private Topics() {
    }
}
