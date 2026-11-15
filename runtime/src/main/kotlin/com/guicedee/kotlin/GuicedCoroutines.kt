package com.guicedee.kotlin

import com.google.inject.Inject
import com.google.inject.Singleton
import com.guicedee.client.Environment
import com.guicedee.client.scopes.CallScoper
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.internal.ContextInternal
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.*
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs suspending application work on the injected Vert.x runtime and exposes ordinary Futures.
 * An active caller scope is retained synchronously. Calls without one get a fresh duplicated
 * Vert.x context and call scope. Scoped objects must remain on that context: Dispatchers.IO/Default
 * do not carry GuicedEE scope. Use Vert.x awaitBlocking for isolated blocking work.
 */
@Singleton
class GuicedCoroutines @Inject constructor(private val vertx: Vertx, private val scoper: CallScoper) : AutoCloseable {
    private val supervisor = SupervisorJob()
    private val lock = Any()
    private val calls = mutableSetOf<Job>()
    private val stopped = Promise.promise<Void>()
    private var closing = false

    /** Starts work immediately. Use [start] when the caller needs explicit cancellation. */
    fun <T> future(block: suspend CoroutineScope.() -> T): Future<T> = start(block).future

    /** Cancellation is cooperative and does not promise to abort an underlying network operation. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> start(block: suspend CoroutineScope.() -> T): CoroutineCall<T> = synchronized(lock) {
        check(!closing) { "GuicedEE coroutine runtime is shutting down" }
        val current = Vertx.currentContext()
        require(current == null || current.owner() === vertx) { "The current context belongs to another Vert.x runtime" }
        val retained = current != null && scoper.isStartedScope
        val context = if (retained) current as ContextInternal
            else ((current ?: vertx.getOrCreateContext()) as ContextInternal).duplicate()
        val leased = AtomicBoolean(false)
        if (retained) {
            scoper.enter()
            leased.set(true)
        }
        val promise: Promise<T> = context.promise()
        val task = CoroutineScope(supervisor + context.dispatcher()).async(start = CoroutineStart.LAZY) {
            if (!retained) {
                scoper.enter()
                leased.set(true)
            }
            // Await children before completing the Future and releasing its call scope.
            coroutineScope(block)
        }
        calls.add(task)
        task.invokeOnCompletion { failure ->
            // Completion can run on a cancelling caller's thread, including before the task starts.
            context.runOnContext {
                try {
                    if (failure == null) promise.tryComplete(task.getCompleted()) else promise.tryFail(failure)
                } finally {
                    try {
                        if (leased.compareAndSet(true, false)) scoper.exit()
                    } finally {
                        synchronized(lock) {
                            calls.remove(task)
                            if (closing && calls.isEmpty()) stopped.tryComplete()
                        }
                    }
                }
            }
        }
        task.start()
        CoroutineCall(promise.future(), task)
    }

    /** Rejects new work, cancels owned jobs, and completes after their context-bound scope cleanup. */
    fun shutdown(): Future<Void> {
        synchronized(lock) {
            if (!closing) {
                closing = true
                supervisor.cancel(CancellationException("GuicedEE is shutting down"))
                if (calls.isEmpty()) stopped.tryComplete()
            }
        }
        return stopped.future()
    }

    /** Lifecycle shutdown must run outside an event loop. Async callers should await [shutdown]. */
    override fun close() {
        check(!io.vertx.core.Context.isOnEventLoopThread()) { "Use shutdown() on an event loop" }
        val timeout = Environment.getSystemPropertyOrEnvironment("GUICEDEE_KOTLIN_SHUTDOWN_TIMEOUT_SECONDS", "10").toLong()
        require(timeout > 0) { "GUICEDEE_KOTLIN_SHUTDOWN_TIMEOUT_SECONDS must be positive" }
        shutdown().toCompletionStage().toCompletableFuture().get(timeout, TimeUnit.SECONDS)
    }
}
