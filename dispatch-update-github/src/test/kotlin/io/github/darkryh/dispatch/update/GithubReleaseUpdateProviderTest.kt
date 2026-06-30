package io.github.darkryh.dispatch.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GithubReleaseUpdateProviderTest {
    private fun jsonEngine(body: String, status: HttpStatusCode = HttpStatusCode.OK): MockEngine =
        MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    @Test
    fun `parses tag name and trims v prefix`() {
        val engine = jsonEngine("""{"tag_name":"v2.3.4","name":"release"}""")
        val provider = GithubReleaseUpdateProvider("owner", "repo", engine)

        val version = runBlocking { provider.latestVersion() }

        assertEquals("2.3.4", version)
    }

    @Test
    fun `keeps tag when no prefix to trim`() {
        val engine = jsonEngine("""{"tag_name":"2.3.4"}""")
        val provider = GithubReleaseUpdateProvider("owner", "repo", engine, tagPrefixToTrim = null)

        val version = runBlocking { provider.latestVersion() }

        assertEquals("2.3.4", version)
    }

    @Test
    fun `returns null on non success status`() {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
        val provider = GithubReleaseUpdateProvider("owner", "repo", engine)

        val version = runBlocking { provider.latestVersion() }

        assertNull(version)
    }

    @Test
    fun `returns null on malformed json`() {
        val engine = jsonEngine("""{"not_a_tag":"oops"}""")
        val provider = GithubReleaseUpdateProvider("owner", "repo", engine)

        val version = runBlocking { provider.latestVersion() }

        assertNull(version)
    }

    @Test
    fun `close closes an owned client`() {
        // A provider created from an engine owns its client; close() must release it. After close,
        // the underlying client/engine is closed and a further request fails.
        val engine = jsonEngine("""{"tag_name":"v1.0.0"}""")
        val provider = GithubReleaseUpdateProvider("owner", "repo", engine)

        val first = runBlocking { provider.latestVersion() }
        assertEquals("1.0.0", first)

        provider.close()

        // A second probe after close fails internally and is swallowed -> null (client closed).
        val afterClose = runBlocking { provider.latestVersion() }
        assertNull(afterClose)
    }

    @Test
    fun `close does not close an injected shared client`() {
        val engine = jsonEngine("""{"tag_name":"v1.0.0"}""")
        val sharedClient = HttpClient(engine)
        val provider = GithubReleaseUpdateProvider("owner", "repo", sharedClient)

        runBlocking { provider.latestVersion() }
        provider.close()

        // The shared client is still usable because the provider does not own it.
        val stillWorks = runBlocking { provider.latestVersion() }
        assertEquals("1.0.0", stillWorks)
        sharedClient.close()
    }
}
