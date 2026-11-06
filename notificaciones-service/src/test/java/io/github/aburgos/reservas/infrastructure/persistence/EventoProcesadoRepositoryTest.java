package io.github.aburgos.reservas.infrastructure.persistence;

import io.github.aburgos.reservas.domain.EventoProcesado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class EventoProcesadoRepositoryTest extends AbstractPersistenceTest {

    private static final Instant AHORA = Instant.parse("2026-01-05T09:00:00Z");

    @Autowired
    EventoProcesadoRepository repository;

    @Autowired
    PlatformTransactionManager transacciones;

    @Test
    void laPrimeraVezInsertaYDevuelve1() {
        UUID eventId = UUID.randomUUID();

        int insertadas = repository.insertarSiNoExiste(eventId, AHORA);

        assertThat(insertadas).isEqualTo(1);
        assertThat(repository.findById(eventId)).get()
                .extracting(EventoProcesado::getProcesadoEn).isEqualTo(AHORA);
    }

    @Test
    void laSegundaVezNoInsertaNiFallaYDevuelve0() {
        UUID eventId = UUID.randomUUID();
        repository.insertarSiNoExiste(eventId, AHORA);

        int insertadas = repository.insertarSiNoExiste(eventId, AHORA.plusSeconds(60));

        assertThat(insertadas).isZero();
        // No pisa el original ni deja la transacción inservible (con save() sí ocurriría en PostgreSQL).
        assertThat(repository.findById(eventId)).get()
                .extracting(EventoProcesado::getProcesadoEn).isEqualTo(AHORA);
    }

    @Test
    void eventosDistintosSeInsertanLosDos() {
        assertThat(repository.insertarSiNoExiste(UUID.randomUUID(), AHORA)).isEqualTo(1);
        assertThat(repository.insertarSiNoExiste(UUID.randomUUID(), AHORA)).isEqualTo(1);
    }

    /**
     * Varios consumidores reciben el mismo evento a la vez: solo uno puede ganarlo. Sin transacción de test
     * (cada hilo abre la suya), igual que en el test de concurrencia de reservas.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void conVariosHilosSobreElMismoEventoSoloUnoLoRegistra() throws Exception {
        UUID eventId = UUID.randomUUID();
        int hilos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        TransactionTemplate tx = new TransactionTemplate(transacciones);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                salida.await();
                return tx.execute(estado -> repository.insertarSiNoExiste(eventId, AHORA));
            }));
        }
        salida.countDown();

        int ganadores = 0;
        for (Future<Integer> f : resultados) {
            ganadores += f.get();
        }
        pool.shutdown();

        assertThat(ganadores).isEqualTo(1);
    }
}
