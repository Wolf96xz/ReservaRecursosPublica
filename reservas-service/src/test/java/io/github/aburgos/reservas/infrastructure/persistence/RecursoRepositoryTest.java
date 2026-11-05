package io.github.aburgos.reservas.infrastructure.persistence;

import io.github.aburgos.reservas.domain.model.FranjaApertura;
import io.github.aburgos.reservas.domain.model.HorarioApertura;
import io.github.aburgos.reservas.domain.model.Recurso;
import io.github.aburgos.reservas.domain.model.TipoRecurso;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecursoRepositoryTest extends AbstractPersistenceTest {

    @Autowired
    RecursoRepository repository;

    @Autowired
    EntityManager em;

    private static FranjaApertura ap(DayOfWeek d, String abre, String cierra) {
        return new FranjaApertura(d, LocalTime.parse(abre), LocalTime.parse(cierra));
    }

    /** Fuerza el INSERT/UPDATE y vacía la caché de primer nivel para releer de la base de datos. */
    private void flushYLimpiar() {
        em.flush();
        em.clear();
    }

    @Test
    void guardaYRecuperaUnRecursoConSuHorarioYZona() {
        Recurso original = new Recurso(UUID.randomUUID(), "Pista central", TipoRecurso.PISTA,
                ZoneId.of("Atlantic/Canary"),
                new HorarioApertura(List.of(
                        ap(DayOfWeek.MONDAY, "09:00", "14:00"),
                        ap(DayOfWeek.MONDAY, "16:00", "21:00"),
                        ap(DayOfWeek.SATURDAY, "10:00", "13:30"))),
                true);
        repository.save(original);
        flushYLimpiar();

        Recurso leido = repository.findById(original.getId()).orElseThrow();

        assertThat(leido.getNombre()).isEqualTo("Pista central");
        assertThat(leido.getTipo()).isEqualTo(TipoRecurso.PISTA);
        assertThat(leido.getZona()).isEqualTo(ZoneId.of("Atlantic/Canary"));
        assertThat(leido.isActivo()).isTrue();
        assertThat(leido.getHorario().franjas()).containsExactlyInAnyOrder(
                ap(DayOfWeek.MONDAY, "09:00", "14:00"),
                ap(DayOfWeek.MONDAY, "16:00", "21:00"),
                ap(DayOfWeek.SATURDAY, "10:00", "13:30"));
    }

    @Test
    void cambiarHorarioReemplazaLasFranjasPersistidas() {
        Recurso r = Recurso.crear("Sala 2", TipoRecurso.SALA,
                new HorarioApertura(List.of(ap(DayOfWeek.MONDAY, "09:00", "14:00"))));
        repository.save(r);
        flushYLimpiar();

        Recurso gestionado = repository.findById(r.getId()).orElseThrow();
        gestionado.cambiarHorario(new HorarioApertura(List.of(ap(DayOfWeek.FRIDAY, "08:00", "12:00"))));
        flushYLimpiar();

        assertThat(repository.findById(r.getId()).orElseThrow().getHorario().franjas())
                .containsExactly(ap(DayOfWeek.FRIDAY, "08:00", "12:00"));
    }

    @Test
    void desactivarSePersiste() {
        Recurso r = Recurso.crear("Sala 3", TipoRecurso.SALA, new HorarioApertura(List.of()));
        repository.save(r);
        flushYLimpiar();

        repository.findById(r.getId()).orElseThrow().desactivar();
        flushYLimpiar();

        assertThat(repository.findById(r.getId()).orElseThrow().isActivo()).isFalse();
    }

    @Test
    void recursoSinHorarioSeGuardaConHorarioVacio() {
        Recurso r = Recurso.crear("Instalación sin horario", TipoRecurso.INSTALACION, new HorarioApertura(List.of()));
        repository.save(r);
        flushYLimpiar();

        assertThat(repository.findById(r.getId()).orElseThrow().getHorario().franjas()).isEmpty();
    }
}
