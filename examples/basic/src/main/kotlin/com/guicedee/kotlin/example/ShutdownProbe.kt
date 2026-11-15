package com.guicedee.kotlin.example

import com.guicedee.client.IGuiceContext
import com.guicedee.client.scopes.CallScoper
import com.guicedee.kotlin.GuicedCoroutines
import kotlinx.coroutines.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/** Pending work used to verify the real lifecycle hook drains cancellation cleanup before Vert.x closes. */
class ShutdownProbe(runtime: GuicedCoroutines) {
    val started = CompletableFuture<Void>()
    val cleaned = AtomicBoolean()
    val call = runtime.start<String> {
        try {
            started.complete(null)
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                delay(20)
                check(IGuiceContext.get(CallScoper::class.java).isStartedScope)
                cleaned.set(true)
            }
        }
    }
}
