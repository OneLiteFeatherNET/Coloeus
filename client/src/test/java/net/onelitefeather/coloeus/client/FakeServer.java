package net.onelitefeather.coloeus.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A fake of the Coloeus API and of the Entra token endpoint on the loopback interface, fresh for every test. Routes
 * answer per {@code METHOD /path}. A handler may block on {@link #awaitRelease()}, which returns once the test closes
 * the server, so a timeout test never waits on a wall clock.
 */
final class FakeServer implements AutoCloseable {

    /** One request as the server received it. */
    record Request(String method, String path, String authorization, String idempotencyKey, String body) {
    }

    /** The answer of a route. */
    record Reply(int status, String body) {
    }

    /** Answers one request. */
    @FunctionalInterface
    interface Route {
        Reply handle(Request request) throws InterruptedException;
    }

    private static final long ARRIVAL_TIMEOUT_SECONDS = 10;

    private final HttpServer server;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Map<String, Route> routes = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final BlockingQueue<Request> arrivals = new LinkedBlockingQueue<>();
    private final CountDownLatch released = new CountDownLatch(1);

    FakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(pool);
        server.createContext("/", this::dispatch);
        server.start();
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    void route(String method, String path, Route route) {
        routes.put(method + " " + path, route);
    }

    void reply(String method, String path, int status, String body) {
        route(method, path, request -> new Reply(status, body));
    }

    /** Blocks the calling handler until the test closes this server. */
    Reply awaitRelease() throws InterruptedException {
        released.await();
        return new Reply(200, "{}");
    }

    /**
     * @param path the path of the request, for example {@code /v1/transactions}
     * @return the next request on that path that the server received, in arrival order
     * @throws AssertionError when no such request arrives in time
     */
    Request nextArrival(String path) {
        try {
            while (true) {
                Request request = arrivals.poll(ARRIVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (request == null) {
                    throw new AssertionError("no request to " + path + " arrived at the fake server within "
                            + ARRIVAL_TIMEOUT_SECONDS + " seconds");
                }
                if (request.path().equals(path)) {
                    return request;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for a request", e);
        }
    }

    /** @return the requests of the server in arrival order, after every answer was sent */
    List<Request> requests() {
        return List.copyOf(requests);
    }

    /** @return the requests of the server whose path starts with the given prefix */
    List<Request> requestsTo(String method, String pathPrefix) {
        return requests.stream()
                .filter(request -> request.method().equals(method) && request.path().startsWith(pathPrefix))
                .toList();
    }

    @Override
    public void close() {
        released.countDown();
        server.stop(0);
        pool.shutdownNow();
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        Request request = new Request(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                header(exchange, "Authorization"),
                header(exchange, "Idempotency-Key"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        requests.add(request);
        arrivals.add(request);
        Route route = routes.get(request.method() + " " + request.path());
        Reply reply;
        try {
            reply = route == null
                    ? new Reply(404, "{\"code\":\"NOT_FOUND\",\"detail\":\"no route in the fake\"}")
                    : route.handle(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exchange.close();
            return;
        }
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }
}
