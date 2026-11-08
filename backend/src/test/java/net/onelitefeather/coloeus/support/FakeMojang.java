package net.onelitefeather.coloeus.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * A local stand-in for the Mojang profile API on a loopback port. Each test scripts the answer per name; a name can
 * also be scripted to never answer, so that the client's read timeout fires. One instance per test class.
 */
public final class FakeMojang implements AutoCloseable {

    private static final String PATH = "/users/profiles/minecraft/";

    private final HttpServer server;
    private final String baseUrl;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final CountDownLatch released = new CountDownLatch(1);

    private FakeMojang(HttpServer server) {
        this.server = server;
        this.baseUrl = "http://" + server.getAddress().getAddress().getHostAddress() + ":"
                + server.getAddress().getPort();
    }

    public static FakeMojang start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            FakeMojang fake = new FakeMojang(server);
            server.createContext(PATH, fake::handle);
            // A cached pool, so a scripted hang does not block the other requests.
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return fake;
        } catch (IOException e) {
            throw new IllegalStateException("cannot start the fake Mojang API", e);
        }
    }

    /** The base URL to configure as {@code coloeus.mojang.url}. */
    public String baseUrl() {
        return baseUrl;
    }

    /** Answers the lookup of {@code name} with {@code status} and {@code body} (an empty body for 204). */
    public void answer(String name, int status, String body) {
        answers.put(name.toLowerCase(Locale.ROOT), new Answer(status, body, false));
    }

    /** Lets the lookup of {@code name} hang until {@link #close()}, so the client's read timeout fires. */
    public void neverAnswer(String name) {
        answers.put(name.toLowerCase(Locale.ROOT), new Answer(0, "", true));
    }

    /** How many lookups of {@code name} reached this fake, case-insensitively. */
    public int requestsFor(String name) {
        return (int) requests.stream().filter(name::equalsIgnoreCase).count();
    }

    /** Forgets the scripted answers and the recorded requests. */
    public void reset() {
        answers.clear();
        requests.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String name = exchange.getRequestURI().getPath().substring(PATH.length());
        requests.add(name);
        Answer answer = answers.getOrDefault(name.toLowerCase(Locale.ROOT), new Answer(404, "", false));
        if (answer.hang()) {
            awaitRelease();
        }
        byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (answer.status() == 204 || body.length == 0) {
            exchange.sendResponseHeaders(answer.status(), -1);
        } else {
            exchange.sendResponseHeaders(answer.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }

    private void awaitRelease() {
        try {
            released.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        released.countDown();
        server.stop(0);
    }

    private record Answer(int status, String body, boolean hang) {
    }
}
