package io.github.aburgos.reservas;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor HTTP mínimo (del JDK, sin librerías nuevas) que hace de {@code reservas-service} en los tests y
 * guarda lo que recibe, para comprobar qué reenvía realmente el gateway.
 */
final class BackendFalso implements AutoCloseable {

    record Peticion(String metodo, String rutaYQuery, String authorization, String idCorrelacion, String cuerpo) {
    }

    private final HttpServer servidor;
    private final List<Peticion> recibidas = new CopyOnWriteArrayList<>();

    BackendFalso() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        servidor.createContext("/", exchange -> {
            String cuerpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            recibidas.add(new Peticion(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("X-Correlation-Id"),
                    cuerpo));
            byte[] respuesta = "{\"origen\":\"backend\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, respuesta.length);
            exchange.getResponseBody().write(respuesta);
            exchange.close();
        });
        servidor.start();
    }

    String url() {
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    List<Peticion> recibidas() {
        return recibidas;
    }

    void limpiar() {
        recibidas.clear();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
