module com.guicedee.kotlin.example {
    requires com.guicedee.kotlin;
    requires com.guicedee.guicedinjection;
    requires com.guicedee.rest;
    requires java.net.http;
    provides com.guicedee.vertx.web.spi.VertxHttpServerConfigurator
        with com.guicedee.kotlin.example.ServerCapture;
    opens com.guicedee.kotlin.example to com.google.guice, com.guicedee.rest, com.guicedee.vertx, tools.jackson.databind, tools.jackson.module.kotlin, kotlin.reflect;
}
