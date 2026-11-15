package com.guicedee.kotlin.mutiny.test

import com.google.inject.*
import com.guicedee.client.IGuiceContext
import com.guicedee.client.implementations.GuicedEEClientModule
import com.guicedee.client.scopes.CallScope
import com.guicedee.client.scopes.CallScoper
import com.guicedee.kotlin.GuicedCoroutines
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.*
import io.vertx.core.Future
import io.vertx.core.Vertx
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@CallScope
class CallIdentity

class MutinyCoroutinesTest {
    private lateinit var vertx: Vertx
    private lateinit var runtime: GuicedCoroutines
    private lateinit var injector: Injector

    @BeforeEach fun setup() {
        IGuiceContext.loaderClasses["com.guicedee.client.services.lifecycle.IOnCallScopeEnter"] = emptySet()
        IGuiceContext.loaderClasses["com.guicedee.client.services.lifecycle.IOnCallScopeExit"] = emptySet()
        // Vert.x fixes the context-local registry when the runtime is created.
        // The normal GuicedEE bootstrap registers CallScoper first; mirror that here.
        CallScoper()
        vertx = Vertx.vertx()
        injector = Guice.createInjector(GuicedEEClientModule(), object : AbstractModule() {
            override fun configure() { bind(Vertx::class.java).toInstance(vertx) }
        })
        runtime = injector.getInstance(GuicedCoroutines::class.java)
    }

    @AfterEach fun teardown() {
        try { runtime.close() } finally { await(vertx.close()); IGuiceContext.loaderClasses.clear() }
    }

    @Test fun `Uni is lazy and awaiting resumes on the owning context with the same scope`() {
        val subscriptions = AtomicInteger()
        val source = Uni.createFrom().emitter<String> { emitter ->
            subscriptions.incrementAndGet()
            Thread.ofVirtual().start { emitter.complete("ready") }
        }
        assertEquals(0, subscriptions.get())
        assertEquals("ready", await(runtime.future {
            val context = Vertx.currentContext()
            val identity = injector.getInstance(CallIdentity::class.java)
            val value = source.awaitSuspending()
            assertSame(context, Vertx.currentContext())
            assertSame(identity, injector.getInstance(CallIdentity::class.java))
            value
        }))
        assertEquals(1, subscriptions.get())
    }

    @Test fun `failure and null items preserve Uni semantics`() {
        val expected = IllegalArgumentException("upstream failure")
        val failure = assertThrows(ExecutionException::class.java) {
            await(runtime.future { Uni.createFrom().failure<String>(expected).awaitSuspending() })
        }
        // Coroutine stack-trace recovery may copy exceptions while preserving their type and cause.
        assertInstanceOf(IllegalArgumentException::class.java, failure.cause)
        assertEquals(expected.message, failure.cause?.message)
        assertNull(await(runtime.future { Uni.createFrom().nullItem<String?>().awaitSuspending() }))
    }

    @Test fun `cancellation cancels the Uni subscription and awaits coroutine finally`() {
        val subscribed = CompletableFuture<Void>()
        val cancelled = AtomicBoolean()
        val cleaned = AtomicBoolean()
        val source = Uni.createFrom().emitter<String> { subscribed.complete(null) }
            .onCancellation().invoke { cancelled.set(true) }
        val call = runtime.start {
            try { source.awaitSuspending() }
            finally { withContext(NonCancellable) {
                delay(20)
                assertTrue(injector.getInstance(CallScoper::class.java).isStartedScope)
                cleaned.set(true)
            } }
        }
        call.future.onFailure { subscribed.completeExceptionally(it) }
        subscribed.get(5, TimeUnit.SECONDS)
        call.cancel()
        val failure = assertThrows(Exception::class.java) { await(call.future) }
        assertTrue(failure is CancellationException || failure.cause is CancellationException)
        assertTrue(cancelled.get())
        assertTrue(cleaned.get())
    }

    @Test fun `shutdown cancels Uni subscriptions before completing`() {
        val subscribed = CompletableFuture<Void>()
        val cancelled = AtomicBoolean()
        val call = runtime.future {
            Uni.createFrom().emitter<String> { subscribed.complete(null) }
                .onCancellation().invoke { cancelled.set(true) }.awaitSuspending()
        }
        subscribed.get(5, TimeUnit.SECONDS)
        await(runtime.shutdown())
        assertTrue(cancelled.get())
        assertTrue(call.failed())
    }

    @Test fun `Multi Flow and Deferred adapters remain usable on the module path`() {
        assertEquals(listOf(1, 2, 3), await(runtime.future {
            Multi.createFrom().items(1, 2, 3).asFlow().toList()
        }))
        assertEquals(listOf(4, 5), await(runtime.future {
            flowOf(4, 5).asMulti().awaitList()
        }))
        assertEquals("child", await(runtime.future {
            async { delay(5); "child" }.asUni().awaitSuspending()
        }))
    }

    private fun <T> await(value: Future<T>): T = value.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS)
}
