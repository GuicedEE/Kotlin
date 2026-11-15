# GuicedEE Kotlin

Thin Kotlin/JVM integration over Vert.x's `vertx-lang-kotlin-coroutines`. Guice owns injection,
GuicedEE owns startup and shutdown, and annotated HTTP/event-bus methods return the existing
Vert.x `Future<T>` type. Suspending services can run inside those boundaries.

This is initial development against **GuicedEE 2.6.0**. These new artifacts have not been
published to Maven Central. The current platform baseline must be installed locally until
its release is available; cloning this repository alone does not supply that baseline.

## Using it

```xml
<dependency>
    <groupId>com.guicedee</groupId>
    <artifactId>kotlin</artifactId>
    <version>2.6.0</version>
</dependency>
```

For HTTP endpoints also add `com.guicedee:rest`. Continue using the normal GuicedEE bootstrap.
The Kotlin module registers its Guice bindings and shutdown hook through JPMS and
`META-INF/services`. It adds Jackson 3's Kotlin module to the shared JSON mappers.

```kotlin
import com.google.inject.Inject
import com.guicedee.kotlin.GuicedCoroutines
import io.vertx.core.Future
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import kotlinx.coroutines.delay

data class Greeting(val message: String)

@Path("/hello")
@Produces(MediaType.APPLICATION_JSON)
class HelloResource @Inject constructor(private val coroutines: GuicedCoroutines) {
    @GET
    @Path("/{name}")
    fun hello(@PathParam("name") name: String): Future<Greeting> =
        coroutines.future {
            delay(10)
            Greeting("Hello, $name")
        }
}
```

Use upstream `io.vertx.kotlin.coroutines.coAwait` to await a Vert.x Future. The returned
Future preserves the normal GuicedEE response, failure, and event-bus reply paths.
The [complete example](examples/basic) includes HTTP and event-bus consumers.

## Execution and ownership

`GuicedCoroutines` is an injected singleton using the application's injected `Vertx`.
It uses the upstream **context dispatcher**, Kotlin structured concurrency, and a small
Future completion bridge. It owns jobs so failures, cancellation before execution, and
shutdown always resolve the corresponding Future.

* `future { ... }` starts work immediately and returns `Future<T>`.
* `start { ... }` returns `CoroutineCall<T>` with `future` and `cancel()`.
* `shutdown()` rejects new calls, cancels owned jobs, and returns a Future that completes
  after their call-scope cleanup. It leaves the injected Vert.x runtime open.
* The GuicedEE shutdown hook awaits cleanup before Vert.x teardown. The default limit is
  10 seconds, configured with `GUICEDEE_KOTLIN_SHUTDOWN_TIMEOUT_SECONDS` through `Environment`.
  `close()` is for non-event-loop lifecycle callers; use `shutdown()` asynchronously on an event loop.

An active caller's `CallScope` is joined synchronously and retained until work and its
structured children finish. Work without an active scope gets its own duplicated Vert.x
context and scope. Nested calls in the same active scope intentionally share its objects.
The caller remains responsible for establishing isolated incoming request contexts; this
module does not replace the HTTP or event-bus dispatchers.

Scoped work must remain on the owning Vert.x context. `Dispatchers.IO` and `Dispatchers.Default`
do not carry GuicedEE scope. Use upstream `awaitBlocking` for isolated blocking operations;
do not use a context-bound Hibernate Reactive session concurrently or move it to another dispatcher.
Coroutine cancellation is cooperative and does not guarantee cancellation of an underlying I/O operation.

A Vert.x Future has no cancellation method. Returning one from a REST endpoint does **not**
automatically cancel its coroutine when a client disconnects. Callers needing cancellation
must retain the `CoroutineCall`, cancel it explicitly, or use a coroutine timeout. Application
shutdown cancels all remaining owned work.

## JPMS

```java
module my.application {
    requires com.guicedee.kotlin;
    requires com.guicedee.rest;
    opens my.application to com.google.guice, com.guicedee.rest, com.guicedee.vertx;
    opens my.application.dto to tools.jackson.databind, tools.jackson.module.kotlin, kotlin.reflect;
}
```

Kotlin DTO constructors and default arguments need access for **both Jackson and Kotlin reflection**.
The upstream coroutine JAR is an automatic module. The accompanying
`com.guicedee.modules.services:vertx-lang-kotlin-coroutines` wrapper adds an explicit descriptor,
retaining its existing module name `vertx.lang.kotlin.coroutines`, classes and Kotlin metadata.
Do not add the upstream JAR alongside the wrapper: they contain the same packages.

## Build and verify

Prerequisites: JDK 25+, Python 3 for the runtime check, and the GuicedEE 2.6.0 platform artifacts.
The wrapper pins Maven 4.0.0-rc-7. Kotlin compiler/stdlib/reflection are aligned at 2.4.21;
coroutines use 1.11.0. Vert.x follows the GuicedEE BOM (5.2.3 in the initial baseline).
Existing official Maven plugin versions are inherited.

```shell
./mvnw -B verify
python scripts/verify-runtime.py --jlink
```

On Windows use `mvnw.cmd`. The reactor contains:

| Directory | Artifact |
| --- | --- |
| `vertx-lang-kotlin-coroutines` | `com.guicedee.modules.services:vertx-lang-kotlin-coroutines` |
| `runtime` | `com.guicedee:kotlin` |
| `examples/basic` | Executable HTTP/event-bus example; excluded from deployment |

Runtime tests cover suspension/context identity, concurrent scope isolation, retained caller
scopes, structured children, failures, cancellation, shutdown and Jackson data classes.
The runtime script boots the actual GuicedEE example, checks concurrent HTTP requests and an
event-bus reply, then shuts it down. With `--jlink`, it repeats the smoke test using a linked
image built from the complete generated application module path. Logs and results are saved
under `target/runtime-verification-*`.

## Initial scope

Annotated entry methods are ordinary Kotlin functions returning `Future<T>`.
Automatic `@GET suspend fun` or `@VertxEventDefinition suspend fun` dispatch, Kotlin-specific
OpenAPI inference, Flow endpoint adaptation, and coroutine-aware interceptors on suspending
service methods are future work. Do not assume `@Trace`, retry, or timeout annotations understand
the completion of a directly intercepted `suspend` method. Existing Future-returning boundaries
retain their normal Java contracts.

Upstream `CoroutineVerticle`, `coroutineRouter`, `coRespond`, and `coConsumer` APIs remain available.
Calling them directly uses their own scope/lifecycle rules; it does not automatically enroll
their jobs in `GuicedCoroutines`.

See the [Vert.x coroutine guide](https://vertx.io/docs/vertx-lang-kotlin-coroutines/kotlin/).

Licensed under Apache-2.0. The wrapper retains upstream Vert.x code under its original license.
