package com.guicedee.kotlin.test

import com.google.inject.*
import com.guicedee.client.IGuiceContext
import com.guicedee.client.implementations.GuicedEEClientModule
import com.guicedee.client.scopes.CallScope
import com.guicedee.client.scopes.CallScoper
import com.guicedee.client.services.lifecycle.IGuiceModule
import com.guicedee.client.services.lifecycle.IGuicePreDestroy
import com.guicedee.kotlin.GuicedCoroutines
import com.guicedee.modules.services.jsonrepresentation.IJsonRepresentation
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.internal.ContextInternal
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.ServiceLoader
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@CallScope
class RequestIdentity { val id: String = UUID.randomUUID().toString() }
data class Payload(val name: String, val language: String = "Kotlin")

class GuicedCoroutinesTest {
    private lateinit var vertx: Vertx
    private lateinit var runtime: GuicedCoroutines
    private lateinit var scoper: CallScoper
    private lateinit var injector: Injector

    @BeforeEach
    fun setup() {
        // These isolated Guice fixtures exercise the real scope without starting the full application.
        // The executable example separately validates application SPI discovery and lifecycle.
        IGuiceContext.loaderClasses["com.guicedee.client.services.lifecycle.IOnCallScopeEnter"] = emptySet()
        IGuiceContext.loaderClasses["com.guicedee.client.services.lifecycle.IOnCallScopeExit"] = emptySet()
        vertx = Vertx.vertx()
        val module = ServiceLoader.load(IGuiceModule::class.java).first { it.javaClass.name == "com.guicedee.kotlin.lifecycle.GuicedKotlinModule" }
        injector = Guice.createInjector(GuicedEEClientModule(), module, object : AbstractModule() {
            override fun configure() { bind(Vertx::class.java).toInstance(vertx) }
        })
        runtime = injector.getInstance(GuicedCoroutines::class.java)
        scoper = injector.getInstance(CallScoper::class.java)
    }

    @AfterEach
    fun teardown() {
        try { runtime.close() } finally {
            await(vertx.close())
            IGuiceContext.loaderClasses.clear()
        }
    }

    @Test
    fun `upstream await resumes on owning context and preserves injected scope`() {
        val result = runtime.future {
            val before = Vertx.currentContext()
            val identity = injector.getInstance(RequestIdentity::class.java)
            val response = Promise.promise<String>()
            vertx.setTimer(15) { response.complete("ready") }
            assertEquals("ready", response.future().coAwait())
            assertSame(before, Vertx.currentContext())
            assertSame(identity, injector.getInstance(RequestIdentity::class.java))
            assertTrue(scoper.isStartedScope)
            "done"
        }
        assertEquals("done", await(result))
    }

    @Test
    fun `overlapping independent calls have isolated scoped objects`() {
        val bothStarted = CompletableDeferred<Unit>()
        var count = 0
        val guard = Any()
        fun call() = runtime.future {
            val identity = injector.getInstance(RequestIdentity::class.java)
            synchronized(guard) { count++; if (count == 2) bothStarted.complete(Unit) }
            bothStarted.await()
            delay(10)
            assertSame(identity, injector.getInstance(RequestIdentity::class.java))
            identity.id
        }
        val first = call()
        val second = call()
        assertNotEquals(await(first), await(second))
    }

    @Test
    fun `caller scope remains alive after Future boundary returns and is then released`() {
        val context = (vertx.getOrCreateContext() as ContextInternal).duplicate()
        val result = Promise.promise<Future<String>>()
        context.runOnContext {
            scoper.enter()
            scoper.seed(String::class.java, "request-a")
            val future = runtime.future {
                delay(20)
                scoper.values[Key.get(String::class.java)] as String
            }
            scoper.exit()
            result.complete(future)
        }
        assertEquals("request-a", await(await(result.future())))
        val released = Promise.promise<Boolean>()
        context.runOnContext { released.complete(!scoper.isStartedScope) }
        assertTrue(await(released.future()))
    }

    @Test
    fun `child coroutines finish before the Future and scope cleanup`() {
        val childFinished = AtomicBoolean()
        val result = runtime.future {
            launch { delay(25); assertTrue(scoper.isStartedScope); childFinished.set(true) }
            "parent"
        }
        assertEquals("parent", await(result))
        assertTrue(childFinished.get())
    }

    @Test
    fun `failure after suspension does not cancel unrelated calls`() {
        val failed = runtime.future<String> { delay(10); error("expected failure") }
        val healthy = runtime.future { delay(25); "healthy" }
        val failure = assertThrows(ExecutionException::class.java) { await(failed) }
        assertEquals("expected failure", failure.cause?.message)
        assertEquals("healthy", await(healthy))
    }

    @Test
    fun `explicit cancellation completes Future and runs suspended finally`() {
        val started = CompletableFuture<Void>()
        val finalized = AtomicBoolean()
        val call = runtime.start<String> {
            try { started.complete(null); awaitCancellation() }
            finally { assertTrue(scoper.isStartedScope); finalized.set(true) }
        }
        started.get(5, TimeUnit.SECONDS)
        call.cancel()
        assertCancelled(call.future)
        assertTrue(finalized.get())
    }

    @Test
    fun `immediate cancellation never leaves an incomplete Future`() {
        repeat(20) {
            val call = runtime.start<String> { awaitCancellation() }
            call.cancel()
            assertCancelled(call.future)
            assertTrue(call.future.isComplete)
        }
    }

    @Test
    fun `shutdown waits for cleanup rejects new calls and leaves injected Vertx open`() {
        val started = CompletableFuture<Void>()
        val finalized = AtomicBoolean()
        val pending = runtime.future<String> {
            try { started.complete(null); awaitCancellation() }
            finally { withContext(NonCancellable) { delay(20); finalized.set(true) } }
        }
        started.get(5, TimeUnit.SECONDS)
        await(runtime.shutdown())
        assertTrue(finalized.get())
        assertTrue(pending.isComplete)
        assertThrows(IllegalStateException::class.java) { runtime.future { "late" } }
        val stillRunning = Promise.promise<Boolean>()
        vertx.setTimer(1) { stillRunning.complete(true) }
        assertTrue(await(stillRunning.future()))
    }

    @Test
    fun `Kotlin data classes use shared Jackson 3 constructor defaults and null checks`() {
        val mapper = IJsonRepresentation.getObjectMapper()
        val payload = mapper.readValue("""{"name":"Ada"}""", Payload::class.java)
        assertEquals(Payload("Ada"), payload)
        assertEquals(payload, mapper.readValue(mapper.writeValueAsString(payload), Payload::class.java))
        assertThrows(Exception::class.java) { mapper.readValue("""{"name":null}""", Payload::class.java) }
    }

    @Test
    fun `shutdown provider is discoverable and precedes Vertx teardown`() {
        val hook = ServiceLoader.load(IGuicePreDestroy::class.java).first { it.javaClass.name == "com.guicedee.kotlin.lifecycle.KotlinShutdown" }
        assertTrue(hook.shutdownSortOrder() < Integer.MAX_VALUE - 200)
    }

    private fun <T> await(future: Future<T>): T = future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS)

    private fun assertCancelled(future: Future<*>) {
        val failure = assertThrows(Exception::class.java) { await(future) }
        assertTrue(failure is CancellationException || failure.cause is CancellationException, failure.toString())
        assertTrue(future.isComplete)
    }
}
