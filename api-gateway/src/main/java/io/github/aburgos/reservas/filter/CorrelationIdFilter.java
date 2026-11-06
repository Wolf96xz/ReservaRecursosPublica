package io.github.aburgos.reservas.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Garantiza que toda petición lleve un {@code X-Correlation-Id}: si el cliente trae uno válido lo conserva y
 * si no genera un UUID. Lo reenvía al backend y lo devuelve en la respuesta (incluidos los 401 y los 404,
 * porque este filtro va antes que la cadena de seguridad).
 *
 * <p>Un id entrante solo se acepta si es corto y de caracteres seguros: el valor acaba en los logs y en
 * cabeceras de otros servicios, y uno arbitrario permitiría inyectar líneas falsas en el registro.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibido = request.getHeader(HEADER);
        String id = recibido != null && VALIDO.matcher(recibido).matches() ? recibido : UUID.randomUUID().toString();

        response.setHeader(HEADER, id);
        chain.doFilter(new ConIdDeCorrelacion(request, id), response);
    }

    /** Las cabeceras de una petición son de solo lectura: se envuelve para que el id llegue al proxy. */
    private static final class ConIdDeCorrelacion extends HttpServletRequestWrapper {

        private final String id;

        ConIdDeCorrelacion(HttpServletRequest request, String id) {
            super(request);
            this.id = id;
        }

        @Override
        public String getHeader(String name) {
            return HEADER.equalsIgnoreCase(name) ? id : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HEADER.equalsIgnoreCase(name) ? Collections.enumeration(Set.of(id)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> nombres = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            nombres.removeIf(HEADER::equalsIgnoreCase);
            ArrayList<String> resultado = new ArrayList<>(nombres);
            resultado.add(HEADER);
            return Collections.enumeration(resultado);
        }
    }
}
