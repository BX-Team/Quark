package org.bxteam.quark.testing;

import com.sun.net.httpserver.HttpServer;
import org.bxteam.quark.dependency.Dependency;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * A Maven repository served over HTTP from a temporary directory, so resolver tests never touch the network.
 */
public final class FakeMavenRepository implements AutoCloseable {
    private final Path root;
    private final HttpServer server;
    private final List<String> requests = new ArrayList<>();

    public FakeMavenRepository(Path root) {
        this.root = root;
        try {
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath().substring(1);
            synchronized (requests) {
                requests.add(path);
            }

            Path file = root.resolve(path).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            byte[] body = Files.readAllBytes(file);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    public String url() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/";
    }

    /** Publishes a POM and a JAR under the dependency's coordinates. */
    public FakeMavenRepository publish(Dependency dependency, String pom, byte[] jar) {
        try {
            Path pomPath = dependency.getPomPath(root);
            Files.createDirectories(pomPath.getParent());
            Files.writeString(pomPath, pom, StandardCharsets.UTF_8);
            if (jar != null) {
                Files.write(dependency.getJarPath(root), jar);
            }
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Publishes an artifact with a generated POM and an empty JAR. */
    public FakeMavenRepository publish(String coordinates, PomBuilder pom) {
        Dependency dependency = Dependency.fromCoordinates(coordinates);
        return publish(dependency, pom.build(dependency), jar(Map.of()));
    }

    /** Publishes an artifact without dependencies. */
    public FakeMavenRepository publish(String coordinates) {
        return publish(coordinates, new PomBuilder());
    }

    public List<String> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    /** Builds a JAR with the given entries. */
    public static byte[] jar(Map<String, byte[]> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jar.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
