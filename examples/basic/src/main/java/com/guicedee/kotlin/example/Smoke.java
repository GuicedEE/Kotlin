package com.guicedee.kotlin.example;

import com.guicedee.client.IGuiceContext;
import com.guicedee.kotlin.GuicedCoroutines;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Runs actual GuicedEE discovery, HTTP and event-bus dispatch, then lifecycle shutdown. */
public final class Smoke {
    public static void main(String[] args) throws Exception {
        System.setProperty("HTTP_HOST", "127.0.0.1");
        System.setProperty("HTTP_PORT", "0");
        System.setProperty("HTTPS_ENABLED", "false");
        System.setProperty("VERTX_CLUSTER_ENABLED", "false");
        System.setProperty("TELEMETRY_ENABLED", "false");
        var context = IGuiceContext.instance();
        boolean passed = false;
        ShutdownProbe probe = null;
        try {
            IGuiceContext.registerModule("com.guicedee.kotlin.example");
            context.getConfig().setClasspathScanning(true).setAnnotationScanning(true).setMethodInfo(true).setFieldInfo(true);
            context.inject();
            context.getLoadingFinished().toCompletionStage().toCompletableFuture().get(60, TimeUnit.SECONDS);
            var runtime = IGuiceContext.get(GuicedCoroutines.class);
            if (runtime == null) throw new AssertionError("Kotlin module was not discovered");
            try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                var base = "http://127.0.0.1:" + ServerCapture.server.actualPort();
                var first = http.sendAsync(request(base, "Ada"), HttpResponse.BodyHandlers.ofString());
                var second = http.sendAsync(request(base, "Grace"), HttpResponse.BodyHandlers.ofString());
                checkResponse(first.get(10, TimeUnit.SECONDS), "Ada");
                checkResponse(second.get(10, TimeUnit.SECONDS), "Grace");
            }
            var reply = IGuiceContext.get(Vertx.class).eventBus().request("kotlin.hello", "Linus")
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            Object body = reply.body();
            var message = body instanceof Greeting greeting ? greeting.getMessage()
                : body instanceof JsonObject json ? json.getString("message") : String.valueOf(body);
            if (!"Hello, Linus".equals(message)) throw new AssertionError("Unexpected event reply: " + body);
            probe = new ShutdownProbe(runtime);
            probe.getStarted().get(5, TimeUnit.SECONDS);
            passed = true;
        } finally {
            context.destroy();
            if (probe != null && (!probe.getCleaned().get() || !probe.getCall().getFuture().isComplete()))
                throw new AssertionError("Shutdown did not await coroutine cleanup");
        }
        if (passed) System.out.println("KOTLIN_SMOKE_OK: HTTP, event bus, discovery, JSON and shutdown");
    }

    private static HttpRequest request(String base, String name) {
        return HttpRequest.newBuilder(URI.create(base + "/kotlin/hello/" + name)).timeout(Duration.ofSeconds(5)).GET().build();
    }

    private static void checkResponse(HttpResponse<String> response, String name) {
        if (response.statusCode() != 200) throw new AssertionError("HTTP " + response.statusCode() + ": " + response.body());
        var body = new JsonObject(response.body());
        if (!("Hello, " + name).equals(body.getString("message")) || !"Kotlin".equals(body.getString("language")))
            throw new AssertionError("Unexpected HTTP body: " + response.body());
    }
}
