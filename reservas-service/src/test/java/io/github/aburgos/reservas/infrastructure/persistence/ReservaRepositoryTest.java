package io.github.aburgos.reservas.infrastructure.persistence;

import io.github.aburgos.reservas.domain.model.EstadoReserva;
import io.github.aburgos.reservas.domain.model.FranjaHoraria;
import io.github.aburgos.reservas.domain.model.HorarioApertura;
import io.github.aburgos.reservas.domain.model.Recurso;
import io.github.aburgos.reservas.domain.model.Reserva;
import io.github.aburgos.reservas.domain.model.TipoRecurso;
import io.github.aburgos.reservas.domain.policy.PoliticaCancelacion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservaRepositoryTest extends AbstractPersistenceTest {

    private static final Instant CREADA = Instant.parse("2026-01-05T09:00:00Z");

    @Autowired
    ReservaRepository reservas;

    @Autowired
    RecursoRepository recursos;

    @Autowired
    EntityManager em;

    private Recurso recurso;

    @BeforeEach
    void crearRecurso() {
        recurso = recursos.saveAndFlush(
                Recurso.crear("Pista 1", TipoRecurso.PISTA, new HorarioApertura(List.of())));
    }

    private static FranjaHoraria franja(String ini, String fin) {
        return new FranjaHoraria(Instant.parse(ini), Instant.parse(fin));
    }

    /** Se construye directamente: estos tests prueban la base de datos, no las reglas del dominio. */
    private Reserva reserva(UUID recursoId, FranjaHoraria franja) {
        return new Reserva(UUID.randomUUID(), recursoId, "user-1", franja,
                EstadoReserva.CONFIRMADA, CREADA, null);
    }

    private Reserva reservaCancelada(UUID recursoId, FranjaHoraria franja) {
        return new Reserva(UUID.randomUUID(), recursoId, "user-1", franja,
                EstadoReserva.CANCELADA, CREADA, CREADA.plusSeconds(60));
    }

    private static final FranjaHoraria DE_10_A_11 = franja("2026-01-12T10:00:00Z", "2026-01-12T11:00:00Z");

    // --- mapeo ---

    @Test
    void guardaYRecuperaUnaReservaConTodosSusCampos() {
        Reserva r = reservas.saveAndFlush(reserva(recurso.getId(), DE_10_A_11));
        em.clear();

        Reserva leida = reservas.findById(r.getId()).orElseThrow();

        assertThat(leida.getRecursoId()).isEqualTo(recurso.getId());
        assertThat(leida.getUsuarioId()).isEqualTo("user-1");
        assertThat(leida.getFranja()).isEqualTo(DE_10_A_11);
        assertThat(leida.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(leida.getCreadaEn()).isEqualTo(CREADA);
        assertThat(leida.getCanceladaEn()).isNull();
        assertThat(leida.getVersion()).isZero();
    }

    @Test
    void cancelarSePersisteEIncrementaLaVersion() {
        Reserva r = reservas.saveAndFlush(reserva(recurso.getId(), DE_10_A_11));
        em.clear();

        Reserva gestionada = reservas.findById(r.getId()).orElseThrow();
        gestionada.cancelar(Clock.fixed(Instant.parse("2026-01-10T09:00:00Z"), ZoneOffset.UTC),
                PoliticaCancelacion.POR_DEFECTO);
        reservas.flush();
        em.clear();

        Reserva leida = reservas.findById(r.getId()).orElseThrow();
        assertThat(leida.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(leida.getCanceladaEn()).isEqualTo(Instant.parse("2026-01-10T09:00:00Z"));
        assertThat(leida.getVersion()).isEqualTo(1L);
    }

    // --- consulta de solapamiento ---

    @Test
    void existeSolapadaDetectaSolapeParcialContenidoEIdentico() {
        reservas.saveAndFlush(reserva(recurso.getId(), DE_10_A_11));
        UUID id = recurso.getId();

        assertThat(reservas.existeSolapada(id, Instant.parse("2026-01-12T10:30:00Z"), Instant.parse("2026-01-12T11:30:00Z"))).isTrue();
        assertThat(reservas.existeSolapada(id, Instant.parse("2026-01-12T09:30:00Z"), Instant.parse("2026-01-12T10:30:00Z"))).isTrue();
        assertThat(reservas.existeSolapada(id, Instant.parse("2026-01-12T10:15:00Z"), Instant.parse("2026-01-12T10:45:00Z"))).isTrue();
        assertThat(reservas.existeSolapada(id, Instant.parse("2026-01-12T09:00:00Z"), Instant.parse("2026-01-12T12:00:00Z"))).isTrue();
        assertThat(reservas.existeSolapada(id, DE_10_A_11.inicio(), DE_10_A_11.fin())).isTrue();
    }

    @Test
    void existeSolapadaNoCuentaFranjasContiguas() {
        reservas.saveAndFlush(reserva(recurso.getId(), DE_10_A_11));
        UUID id = recurso.getId();

        assertThat(reservas.existeSolapada(id, Instant.parse("2026-01-12T11:00:00Z"), Instant.parse("2026-01-12T12:00:00Z"))).isFalse();
        assertThat(reservas.existeSolapada(id, Instant.parse("2026-01-12T09:00:00Z"), Instant.parse("2026-01-12T10:00:00Z"))).isFalse();
    }

    @Test
    void existeSolapadaIgnoraReservasCanceladasYOtrosRecursos() {
        Recurso otro = recursos.saveAndFlush(
                Recurso.crear("Pista 2", TipoRecurso.PISTA, new HorarioApertura(List.of())));
        reservas.saveAndFlush(reservaCancelada(recurso.getId(), DE_10_A_11));
        reservas.saveAndFlush(reserva(otro.getId(), DE_10_A_11));

        assertThat(reservas.existeSolapada(recurso.getId(), DE_10_A_11.inicio(), DE_10_A_11.fin())).isFalse();
    }

    // --- restricción de exclusión de la base de datos ---

    @Test
    void laBaseDeDatosRechazaDosReservasConfirmadasSolapadasDelMismoRecurso() {
        reservas.saveAndFlush(reserva(recurso.getId(), DE_10_A_11));

        assertThatThrownBy(() -> reservas.saveAndFlush(reserva(recurso.getId(),
                franja("2026-01-12T10:30:00Z", "2026-01-12T11:30:00Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laBaseDeDatosAdmiteReservasContiguas() {
        reservas.saveAndFlush(reserva(recurso.getId(), DE_10_A_11));
        reservas.saveAndFlush(reserva(recurso.getId(), franja("2026-01-12T11:00:00Z", "2026-01-12T12:00:00Z")));
        reservas.saveAndFlush(reserva(recurso.getId(), franja("2026-01-12T09:00:00Z", "2026-01-12T10:00:00Z")));

        assertThat(reservas.count()).isEqualTo(3);
    }

    @Test
    void laBaseDeDatosAdmiteLaMismaFranjaEnRecursosDistintos() {
        Recurso otro = recursos.saveAndFlush(
                Recurso.crear("Pista 2", TipoRecurso.PISTA, new HorarioApertura(List.of())));
        reservas.saveAndFlush(reserva(recurso.getId(), DE_10_A_11));
        reservas.saveAndFlush(reserva(otro.getId(), DE_10_A_11));

        assertThat(reservas.count()).isEqualTo(2);
    }

    @Test
    void unaReservaCanceladaLiberaLaFranja() {
        reservas.saveAndFlush(reservaCancelada(recurso.getId(), DE_10_A_11));
        reservas.saveAndFlush(reserva(recurso.getId(), DE_10_A_11));

        assertThat(reservas.count()).isEqualTo(2);
    }

    @Test
    void cancelarUnaReservaPermiteReservarDeNuevoLaMismaFranja() {
        Reserva primera = reservas.saveAndFlush(reserva(recurso.getId(), DE_10_A_11));
        primera.cancelar(Clock.fixed(Instant.parse("2026-01-10T09:00:00Z"), ZoneOffset.UTC),
                PoliticaCancelacion.POR_DEFECTO);
        reservas.flush();

        reservas.saveAndFlush(reserva(recurso.getId(), DE_10_A_11));

        assertThat(reservas.count()).isEqualTo(2);
    }

    @Test
    void laBaseDeDatosRechazaUnaReservaDeUnRecursoInexistente() {
        assertThatThrownBy(() -> reservas.saveAndFlush(reserva(UUID.randomUUID(), DE_10_A_11)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
