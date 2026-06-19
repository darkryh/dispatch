package com.ead.dispatch.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

class GithubReleaseUpdateProvider private constructor(
    private val owner: String,
    private val repo: String,
    private val tagPrefixToTrim: String?,
    private val client: HttpClient,
    private val ownsClient: Boolean,
) : UpdateProvider, AutoCloseable {

    /**
     * Default constructor: lazily owns a single CIO-backed [HttpClient] that is reused across
     * fetches and released by [close] (e.g. via a Koin `single { }` auto-close path).
     */
    constructor(
        owner: String,
        repo: String,
        tagPrefixToTrim: String? = "v",
    ) : this(owner, repo, tagPrefixToTrim, HttpClient(CIO), ownsClient = true)

    /**
     * Inject a specific engine (e.g. MockEngine for tests). The created client is owned and closed
     * by this provider.
     */
    constructor(
        owner: String,
        repo: String,
        engine: HttpClientEngine,
        tagPrefixToTrim: String? = "v",
    ) : this(owner, repo, tagPrefixToTrim, HttpClient(engine), ownsClient = true)

    /**
     * Inject a shared [HttpClient]. The caller retains ownership; [close] will not close it.
     */
    constructor(
        owner: String,
        repo: String,
        client: HttpClient,
        tagPrefixToTrim: String? = "v",
    ) : this(owner, repo, tagPrefixToTrim, client, ownsClient = false)

    override suspend fun latestVersion(): String? {
        val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val payload = fetch(apiUrl) ?: return null
        val tag = extractTag(payload) ?: return null
        return if (tagPrefixToTrim != null && tag.startsWith(tagPrefixToTrim)) {
            tag.removePrefix(tagPrefixToTrim)
        } else {
            tag
        }
    }

    private fun extractTag(payload: String): String? =
        TAG_REGEX.find(payload)?.groupValues?.getOrNull(1)

    private suspend fun fetch(url: String): String? =
        runCatching {
            val response = client.get(url) {
                headers {
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                    append(HttpHeaders.UserAgent, "dispatch-update")
                }
            }
            if (!response.status.isSuccess()) return null
            response.bodyAsText()
        }.getOrElse { error ->
            UpdateLog.debug("GitHub release fetch failed for $url", error)
            null
        }

    override fun close() {
        if (ownsClient) {
            client.close()
        }
    }

    companion object {
        private val TAG_REGEX = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
    }
}
