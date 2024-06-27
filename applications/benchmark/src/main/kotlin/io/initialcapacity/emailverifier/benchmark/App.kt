package io.initialcapacity.emailverifier.benchmark

import io.initialcapacity.emailverifier.fakesendgridendpoints.fakeSendgridRoutes
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import java.util.*

data class Confirmation(
    val email: String,
    val code: UUID,
)

fun main(): Unit = runBlocking {
    val port = getEnvInt("PORT", 9090)

    val benchmark = Benchmark(
        registrationUrl = System.getenv("REGISTRATION_URL") ?: "http://localhost:8081",
        registrationCount = getEnvInt("REGISTRATION_COUNT", 5_000),
        requestWorkerCount = getEnvInt("REQUEST_WORKER_COUNT", 4),
        registrationWorkerCount = getEnvInt("REGISTRATION_WORKER_COUNT", 4),
        client = HttpClient(Java) {
            expectSuccess = false
        }
    )

    val fakeEmailServer = fakeEmailServer(port, benchmark).apply { start() }
    val duration = benchmark.start(this)
    fakeEmailServer.stop()

    if (!benchmark.checkBusinessRequirement(duration)) {
        println("Benchmark failed to meet the business requirement of 250 registrations per second.")
        exitProcess(1) // Fail the build
    }

    println("Benchmark completed in $duration")
}

private fun getEnvInt(name: String, default: Int): Int = System.getenv(name)?.toInt() ?: default

private fun fakeEmailServer(
    port: Int,
    benchmark: Benchmark
) = embeddedServer(
    factory = Jetty,
    port = port,
    module = { fakeSendgridRoutes("super-secret") { benchmark.processConfirmation(it) } }
)
