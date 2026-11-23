package com.gudo.rms.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A tiny in-process HTTP server that pretends to be Supabase. Test-only. */
public final class FakeSupabase implements AutoCloseable {
    public record Request(String method, String pathAndQuery, Map<String, String> headers, byte[] body) {
        public String path() { int q = pathAndQuery.indexOf('?'); return q < 0 ? pathAndQuery : pathAndQuery.substring(0, q); }
        public String bodyString() { return new String(body, StandardCharsets.UTF_8); }
    }

    private record Response(int status, byte[] body, String contentType) { }

    private final HttpServer server;
    private final Map<String, Deque<Response>> routes = new LinkedHashMap<>();
    private final List<Request> requests = new ArrayList<>();

    public FakeSupabase() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) { throw new IllegalStateException(e); }
        server.createContext("/", ex -> {
            byte[] in = ex.getRequestBody().readAllBytes();
            Map<String, String> h = new LinkedHashMap<>();
            ex.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(Locale.ROOT), v.get(0)));
            String pq = ex.getRequestURI().getRawPath() + (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery());
            Request r = new Request(ex.getRequestMethod(), pq, h, in);
            Response resp;
            synchronized (this) {
                requests.add(r);
                resp = lookup(r);
            }
            ex.getResponseHeaders().add("Content-Type", resp.contentType());
            ex.sendResponseHeaders(resp.status(), resp.body().length == 0 ? -1 : resp.body().length);
            if (resp.body().length > 0) ex.getResponseBody().write(resp.body());
            ex.close();
        });
        server.start();
    }

    private Response lookup(Request r) {
        String key = r.method() + " " + r.path();
        Deque<Response> q = routes.get(key);
        if (q == null) {
            for (var e : routes.entrySet()) if (key.startsWith(e.getKey())) { q = e.getValue(); break; }
        }
        if (q == null) return new Response(404, ("{\"message\":\"no fake for " + key + "\"}").getBytes(StandardCharsets.UTF_8), "application/json");
        return q.size() > 1 ? q.poll() : q.peek();
    }

    /** Registers a JSON response for "METHOD path-prefix". Several calls for one key are served in order; the last repeats. */
    public FakeSupabase on(String method, String pathPrefix, int status, String jsonBody) {
        return onBytes(method, pathPrefix, status, jsonBody.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    public synchronized FakeSupabase onBytes(String method, String pathPrefix, int status, byte[] body, String contentType) {
        routes.computeIfAbsent(method + " " + pathPrefix, k -> new ArrayDeque<>()).add(new Response(status, body, contentType));
        return this;
    }

    public String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    public synchronized List<Request> requests() { return List.copyOf(requests); }

    public synchronized List<Request> requests(String method, String pathPrefix) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().startsWith(pathPrefix)).toList();
    }

    @Override public void close() { server.stop(0); }
}
