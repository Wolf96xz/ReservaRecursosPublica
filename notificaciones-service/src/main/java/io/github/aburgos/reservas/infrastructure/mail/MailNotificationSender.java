package io.github.aburgos.reservas.infrastructure.mail;

import io.github.aburgos.reservas.application.port.NotificationSender;
import io.github.aburgos.reservas.event.ReservaEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envía el correo por SMTP. Una {@code MailException} del servidor se propaga para que se anote el fallo. */
@Component
public class MailNotificationSender implements NotificationSender {

    private final JavaMailSender mail;
    private final String remitente;

    public MailNotificationSender(JavaMailSender mail,
                                  @Value("${notificaciones.mail.remitente:no-reply@reservas.local}") String remitente) {
        this.mail = mail;
        this.remitente = remitente;
    }

    @Override
    public void enviar(ReservaEvent evento) {
        PlantillaCorreo.Correo correo = PlantillaCorreo.renderizar(evento);
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(evento.email());
        mensaje.setSubject(correo.asunto());
        mensaje.setText(correo.cuerpo());
        mail.send(mensaje);
    }
}
