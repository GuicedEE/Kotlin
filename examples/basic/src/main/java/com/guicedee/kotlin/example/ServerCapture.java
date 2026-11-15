package com.guicedee.kotlin.example;

import com.guicedee.vertx.web.spi.VertxHttpServerConfigurator;
import io.vertx.core.http.HttpServer;

/** Lets the smoke test discover the ephemeral port selected by the real GuicedEE HTTP listener. */
public final class ServerCapture implements VertxHttpServerConfigurator {
    static volatile HttpServer server;
    @Override public HttpServer builder(HttpServer builder) {
        server = builder;
        return builder;
    }
}
