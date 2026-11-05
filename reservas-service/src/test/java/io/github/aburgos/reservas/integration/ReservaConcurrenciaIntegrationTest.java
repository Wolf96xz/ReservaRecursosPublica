package io.github.aburgos.reservas.integration;

import io.github.aburgos.reservas.application.RecursoService;
import io.github.aburgos.reservas.application.ReservaService;
import io.github.aburgos.reservas.application.command.CrearRecursoCommand;
import io.github.aburgos.reservas.application.command.CrearReservaCommand;
import io.github.aburgos.reservas.domain.exception.ReservaSolapadaException;
import io.github.aburgos.reservas.domain.model.EstadoReserva;
import io.github.aburgos.reservas.domain.model.FranjaApertura;
import io.github.aburgos.reservas.domain.model.HorarioApertura;
import io.github.aburgos.reservas.domain.model.Recurso;
import io.github.aburgos.reservas.domain.model.TipoRecurso;
import io.github.aburgos.reservas.infrastructure.persistence.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Varios hilos reservan a la vez la misma franja del mismo recurso: solo uno puede ganar.
 * Sin transacción en el test: cada llamada a {@link ReservaService#crear} abre la suya.
 * La consulta de solape no basta (todos la pasan a la vez); decide la restricción de exclusión de la BD.
 */
class ReservaConcurrenciaIntegrationTest extends AbstractIntegrationTest {

    private static final int HILOS = 8;

    @Autowired
    ReservaService reservaService;

    @Autowired
    RecursoService recursoService;

    @Autowired
    ReservaRepository reservas;

    @Test
    void conVariosHilosSobreLaMismaFranjaSoloUnoGana() throws Exception {
        Recurso recurso = recursoService.crear(new CrearRecursoCommand("Pista concurrente", TipoRecurso.PISTA, null,
                new HorarioApertura(List.of(
                        new FranjaApertura(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(21, 0))))));
        Instant inicio = Instant.parse("2026-01-12T10:00:00Z");
        Instant fin = Instant.parse("2026-01-12T11:00:00Z");

        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            String usuario = "user-" + i;
            Callable<Boolean> tarea = () -> {
                salida.await();
                try {
                    reservaService.crear(new CrearReservaCommand(recurso.getId(), usuario, inicio, fin));
                    return true;
                } catch (ReservaSolapadaException e) {
                    return false;
                }
            };
            resultados.add(pool.submit(tarea));
        }
        salida.countDown();

        long ganadores = 0;
        for (Future<Boolean> f : resultados) {
            if (f.get()) {
                ganadores++;
            }
        }
        pool.shutdown();

        assertThat(ganadores).isEqualTo(1);
        long confirmadas = reservas.findAll().stream()
                .filter(r -> r.getRecursoId().equals(recurso.getId()))
                .filter(r -> r.getEstado() == EstadoReserva.CONFIRMADA)
                .count();
        assertThat(confirmadas).isEqualTo(1);
    }
}
