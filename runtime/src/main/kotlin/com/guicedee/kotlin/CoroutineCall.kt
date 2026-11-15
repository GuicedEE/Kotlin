package com.guicedee.kotlin

import io.vertx.core.Future
import kotlinx.coroutines.Job
import java.util.concurrent.CancellationException

/** A Future boundary with an explicit cancellation handle. A Vert.x Future itself is not cancellable. */
class CoroutineCall<T> internal constructor(val future: Future<T>, private val job: Job) {
    @JvmOverloads
    fun cancel(reason: String = "GuicedEE coroutine call cancelled") {
        job.cancel(CancellationException(reason))
    }
}
