package io.github.aburgos.reservas.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Registra una línea por petición: id de correlación, método, ruta, estado y duración. A propósito no
 * registra la query, las cabeceras (el {@code Authorization} lleva el token) ni el cuerpo (puede llevar el
 * email, dato personal). Va justo después de {@link CorrelationIdFilter}, que le deja el id en la cabecera.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long inicio = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long milisegundos = (System.nanoTime() - inicio) / 1_000_000;
            log.info("[{}] {} {} -> {} ({} ms)", request.getHeader(CorrelationIdFilter.HEADER),
                    request.getMethod(), request.getRequestURI(), response.getStatus(), milisegundos);
        }
    }
}
