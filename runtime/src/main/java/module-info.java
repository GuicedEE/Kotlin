module com.guicedee.kotlin {
    requires transitive com.guicedee.vertx;
    requires transitive vertx.lang.kotlin.coroutines;
    requires transitive kotlin.stdlib;
    requires transitive kotlinx.coroutines.core;
    requires com.guicedee.jsonrepresentation;
    requires tools.jackson.module.kotlin;
    exports com.guicedee.kotlin;
    opens com.guicedee.kotlin to com.google.guice;
    opens com.guicedee.kotlin.lifecycle to com.google.guice;
    provides com.guicedee.client.services.lifecycle.IGuiceModule
        with com.guicedee.kotlin.lifecycle.GuicedKotlinModule;
    provides com.guicedee.client.services.lifecycle.IGuicePreDestroy
        with com.guicedee.kotlin.lifecycle.KotlinShutdown;
}
