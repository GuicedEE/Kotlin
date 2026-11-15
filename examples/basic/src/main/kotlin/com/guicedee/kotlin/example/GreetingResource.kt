package com.guicedee.kotlin.example

import com.google.inject.Inject
import com.google.inject.Provider
import com.guicedee.client.scopes.CallScope
import com.guicedee.kotlin.GuicedCoroutines
import com.guicedee.vertx.VertxEventDefinition
import io.vertx.core.Future
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import kotlinx.coroutines.delay

data class Greeting(val message: String, val language: String = "Kotlin")

@CallScope
class GreetingCall { var name: String = "" }

class GreetingService @Inject constructor(private val calls: Provider<GreetingCall>) {
    suspend fun greet(name: String): Greeting {
        val call = calls.get()
        call.name = name
        delay(10)
        check(call === calls.get() && call.name == name) { "Call scope was lost or shared with another request" }
        return Greeting("Hello, $name")
    }
}

@Path("/kotlin/hello")
@Produces(MediaType.APPLICATION_JSON)
class GreetingResource @Inject constructor(private val coroutines: GuicedCoroutines, private val service: GreetingService) {
    @GET
    @Path("/{name}")
    fun hello(@PathParam("name") name: String): Future<Greeting> = coroutines.future { service.greet(name) }
}

class GreetingConsumer @Inject constructor(private val coroutines: GuicedCoroutines, private val service: GreetingService) {
    @VertxEventDefinition("kotlin.hello")
    fun hello(name: String): Future<String> = coroutines.future { service.greet(name).message }
}
