package io.github.aburgos.reservas.infrastructure.persistence;

import io.github.aburgos.reservas.domain.EstadoNotificacion;
import io.github.aburgos.reservas.domain.Notificacion;
import io.github.aburgos.reservas.domain.TipoNotificacion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificacionRepositoryTest extends AbstractPersistenceTest {

    private static final Instant T0 = Instant.parse("2026-01-05T09:00:00Z");

    @Autowired
    NotificacionRepository repository;

    @Autowired
    EventoProcesadoRepository eventos;

    @Autowired
    EntityManager em;

    private static Clock reloj(Instant instante) {
        return Clock.fixed(instante, ZoneOffset.UTC);
    }

    /** Registra el evento (la FK lo exige) y guarda una notificación creada en {@code creada}. */
    private Notificacion guardar(Instant creada) {
        UUID eventId = UUID.randomUUID();
        eventos.insertarSiNoExiste(eventId, creada);
        Notificacion n = Notificacion.nueva(eventId, UUID.randomUUID(), TipoNotificacion.RESERVA_CREADA,
                "ana@example.org", "{\"a\":1}", reloj(creada));
        return repository.save(n);
    }

    private void flushYLimpiar() {
        em.flush();
        em.clear();
    }

    @Test
    void guardaYRecuperaTodosLosCampos() {
        Notificacion n = guardar(T0);
        n.registrarFallo("smtp caído");
        repository.save(n);
        flushYLimpiar();

        Notificacion leida = repository.findById(n.getId()).orElseThrow();

        assertThat(leida.getEventId()).isEqualTo(n.getEventId());
        assertThat(leida.getReservaId()).isEqualTo(n.getReservaId());
        assertThat(leida.getTipo()).isEqualTo(TipoNotificacion.RESERVA_CREADA);
        assertThat(leida.getDestinatario()).isEqualTo("ana@example.org");
        assertThat(leida.getEstado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(leida.getIntentos()).isEqualTo(1);
        assertThat(leida.getUltimoError()).isEqualTo("smtp caído");
        assertThat(leida.getPayload()).isEqualTo("{\"a\":1}");
        assertThat(leida.getCreadaEn()).isEqualTo(T0);
        assertThat(leida.getEnviadaEn()).isNull();
    }

    @Test
    void seEncuentraPorElIdDelEvento() {
        Notificacion n = guardar(T0);
        flushYLimpiar();

        assertThat(repository.findByEventId(n.getEventId())).get()
                .extracting(Notificacion::getId).isEqualTo(n.getId());
        assertThat(repository.findByEventId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void noSePuedeCrearUnaNotificacionDeUnEventoNoRegistrado() {
        Notificacion huerfana = Notificacion.nueva(UUID.randomUUID(), UUID.randomUUID(),
                TipoNotificacion.RESERVA_CREADA, "ana@example.org", "{}", reloj(T0));

        assertThatThrownBy(() -> {
            repository.save(huerfana);
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unEventoSoloPuedeTenerUnaNotificacion() {
        Notificacion primera = guardar(T0);
        Notificacion repetida = Notificacion.nueva(primera.getEventId(), UUID.randomUUID(),
                TipoNotificacion.RESERVA_CREADA, "otra@example.org", "{}", reloj(T0));

        assertThatThrownBy(() -> {
            repository.save(repetida);
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laBaseDeDatosRechazaUnEstadoNoValido() {
        Notificacion n = guardar(T0);
        em.flush();

        assertThatThrownBy(() -> {
            em.createNativeQuery("UPDATE notificacion SET estado = 'EXPLOTADA' WHERE id = :id")
                    .setParameter("id", n.getId()).executeUpdate();
            em.flush();
        }).hasMessageContaining("notificacion_estado_valido");
    }

    // --- reintentos ---

    @Test
    void idsReintentablesIncluyeSoloLasPendientesYFallidasConIntentosYAntiguas() {
        Notificacion pendienteAntigua = guardar(T0);
        Notificacion fallidaAntigua = guardar(T0.plusSeconds(10));
        fallidaAntigua.registrarFallo("e");
        fallidaAntigua.registrarFallo("e");
        Notificacion agotada = guardar(T0.plusSeconds(20));
        for (int i = 0; i < 5; i++) {
            agotada.registrarFallo("e");
        }
        Notificacion enviada = guardar(T0.plusSeconds(30));
        enviada.marcarEnviada(reloj(T0.plusSeconds(31)));
        Notificacion reciente = guardar(T0.plusSeconds(600));   // creada después del corte
        flushYLimpiar();

        List<UUID> ids = repository.idsReintentables(5, T0.plusSeconds(300), PageRequest.of(0, 50));

        // De la más antigua a la más reciente; sin la agotada, la enviada ni la demasiado reciente.
        assertThat(ids).containsExactly(pendienteAntigua.getId(), fallidaAntigua.getId());
        assertThat(ids).doesNotContain(agotada.getId(), enviada.getId(), reciente.getId());
    }

    @Test
    void idsReintentablesRespetaElLimiteDelLote() {
        guardar(T0);
        guardar(T0.plusSeconds(1));
        guardar(T0.plusSeconds(2));
        flushYLimpiar();

        List<UUID> ids = repository.idsReintentables(5, T0.plusSeconds(300), PageRequest.of(0, 2));

        assertThat(ids).hasSize(2);
    }
}
