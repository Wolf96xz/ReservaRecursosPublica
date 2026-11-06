package io.github.aburgos.reservas.application.port;

import io.github.aburgos.reservas.event.ReservaEvent;

/** Salida de notificaciones (hoy, correo). Lanza una excepción si el envío falla. */
public interface NotificationSender {

    void enviar(ReservaEvent evento);
}
