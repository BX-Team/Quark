package org.bxteam.quark.update.provider;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves canned responses keyed by path and query.
 */
final class CannedServer implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();

    record Response(int status, String body) {
    }

    CannedServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            URI uri = exchange.getRequestURI();
            String key = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
            Response response = responses.getOrDefault(key, new Response(404, "not found: " + key));
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    CannedServer respond(String pathAndQuery, int status, String body) {
        responses.put(pathAndQuery, new Response(status, body));
        return this;
    }

    CannedServer respond(String pathAndQuery, String body) {
        return respond(pathAndQuery, 200, body);
    }

    URI uri() {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/");
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
