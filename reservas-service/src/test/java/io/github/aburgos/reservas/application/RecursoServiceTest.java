package io.github.aburgos.reservas.application;

import io.github.aburgos.reservas.application.command.CrearRecursoCommand;
import io.github.aburgos.reservas.domain.exception.RecursoNoEncontradoException;
import io.github.aburgos.reservas.domain.model.HorarioApertura;
import io.github.aburgos.reservas.domain.model.Recurso;
import io.github.aburgos.reservas.domain.model.TipoRecurso;
import io.github.aburgos.reservas.infrastructure.persistence.RecursoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecursoServiceTest {

    @Mock
    RecursoRepository recursos;

    @Test
    void crearSinZonaUsaLaZonaPorDefecto() {
        when(recursos.save(any(Recurso.class))).thenAnswer(i -> i.getArgument(0));

        Recurso r = new RecursoService(recursos).crear(
                new CrearRecursoCommand("Pista 1", TipoRecurso.PISTA, null, new HorarioApertura(List.of())));

        assertThat(r.getZona()).isEqualTo(Recurso.ZONA_POR_DEFECTO);
        assertThat(r.isActivo()).isTrue();
    }

    @Test
    void crearConZonaExplicitaLaRespeta() {
        when(recursos.save(any(Recurso.class))).thenAnswer(i -> i.getArgument(0));

        Recurso r = new RecursoService(recursos).crear(new CrearRecursoCommand(
                "Sala", TipoRecurso.SALA, ZoneId.of("Atlantic/Canary"), new HorarioApertura(List.of())));

        assertThat(r.getZona()).isEqualTo(ZoneId.of("Atlantic/Canary"));
    }

    @Test
    void obtenerFallaSiNoExiste() {
        UUID id = UUID.randomUUID();
        when(recursos.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new RecursoService(recursos).obtener(id))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
