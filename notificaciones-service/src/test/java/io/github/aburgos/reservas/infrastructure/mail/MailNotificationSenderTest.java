package io.github.aburgos.reservas.infrastructure.mail;

import io.github.aburgos.reservas.event.ReservaCreada;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MailNotificationSenderTest {

    @Mock
    JavaMailSender mail;

    private static ReservaCreada evento() {
        Instant inicio = Instant.parse("2026-01-12T10:00:00Z");
        return new ReservaCreada(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Pista 1",
                "Europe/Madrid", "user-1", "ana@example.org", inicio, inicio.plusSeconds(3600),
                Instant.parse("2026-01-05T09:00:00Z"));
    }

    @Test
    void enviaUnCorreoAlEmailDelEventoConLaPlantilla() {
        new MailNotificationSender(mail, "no-reply@reservas.test").enviar(evento());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captor.capture());
        SimpleMailMessage mensaje = captor.getValue();
        assertThat(mensaje.getFrom()).isEqualTo("no-reply@reservas.test");
        assertThat(mensaje.getTo()).containsExactly("ana@example.org");
        assertThat(mensaje.getSubject()).isEqualTo("Reserva confirmada: Pista 1");
        assertThat(mensaje.getText()).contains("Horario: 11:00 - 12:00 (Europe/Madrid)");
    }

    @Test
    void unFalloDelServidorDeCorreoSePropagaParaAnotarlo() {
        doThrow(new MailSendException("smtp caído")).when(mail).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> new MailNotificationSender(mail, "no-reply@reservas.test").enviar(evento()))
                .isInstanceOf(MailSendException.class);
    }
}
