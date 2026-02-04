package com.ead.dispatch.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

class GithubReleaseUpdateProvider(
    private val owner: String,
    private val repo: String,
    private val tagPrefixToTrim: String? = "v",
) : UpdateProvider {
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

    private fun extractTag(payload: String): String? {
        val regex = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return regex.find(payload)?.groupValues?.getOrNull(1)
    }

    private suspend fun fetch(url: String): String? {
        return runCatching {
            HttpClient(CIO).use { client ->
                val response = client.get(url) {
                    headers {
                        append(HttpHeaders.Accept, "application/vnd.github+json")
                        append(HttpHeaders.UserAgent, "dispatch-update")
                    }
                }
                if (!response.status.isSuccess()) return@use null
                response.bodyAsText()
            }
        }.getOrNull()
    }
}
