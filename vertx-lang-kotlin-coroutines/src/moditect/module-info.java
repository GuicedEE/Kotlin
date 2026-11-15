module vertx.lang.kotlin.coroutines {
    requires transitive io.vertx.core;
    requires transitive kotlin.stdlib;
    requires transitive kotlinx.coroutines.core;
    requires static io.vertx.web;
    exports io.vertx.kotlin.coroutines;
}
