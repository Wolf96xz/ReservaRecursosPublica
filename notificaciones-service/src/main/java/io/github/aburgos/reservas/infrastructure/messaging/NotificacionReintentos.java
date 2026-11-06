package io.github.aburgos.reservas.infrastructure.messaging;

import io.github.aburgos.reservas.application.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reintenta periódicamente las notificaciones pendientes o fallidas. Se puede desactivar con
 * {@code notificaciones.reintentos.enabled=false}. No usa bloqueo distribuido: con varias instancias podría
 * repetirse algún correo, un riesgo aceptado frente a perderlo (ver ADR 0007).
 */
@Component
@ConditionalOnProperty(name = "notificaciones.reintentos.enabled", havingValue = "true", matchIfMissing = true)
public class NotificacionReintentos {

    private static final Logger log = LoggerFactory.getLogger(NotificacionReintentos.class);

    private final NotificacionService service;

    public NotificacionReintentos(NotificacionService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${notificaciones.reintentos.intervalo-ms:60000}")
    public void reintentar() {
        int tocadas = service.reintentarPendientes();
        if (tocadas > 0) {
            log.info("Reintentadas {} notificaciones pendientes o fallidas", tocadas);
        }
    }
}
