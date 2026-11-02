package com.code2request.executor

import com.code2request.model.RequestDraft
import com.code2request.model.ResponseViewModel
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Executes HTTP requests using java.net.http.HttpClient (JDK 11+).
 * Must be called from a background thread (not EDT).
 */
object HttpRequestExecutor {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun execute(draft: RequestDraft): ResponseViewModel {
        return try {
            val url = buildUrlWithParams(draft.url.trim(), draft.queryParams)
            val startMs = System.currentTimeMillis()

            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))

            draft.headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            val bodyPublisher: HttpRequest.BodyPublisher
            if (draft.isMultipart) {
                val mp = MultipartBodyPublisher.build(draft.formData, draft.filePaths)
                requestBuilder.header("Content-Type", mp.contentType)
                bodyPublisher = mp.publisher
            } else {
                bodyPublisher = if (draft.body.isNotEmpty())
                    HttpRequest.BodyPublishers.ofString(draft.body)
                else
                    HttpRequest.BodyPublishers.noBody()
            }

            when (draft.httpMethod.uppercase()) {
                "GET"    -> requestBuilder.GET()
                "POST"   -> requestBuilder.POST(bodyPublisher)
                "PUT"    -> requestBuilder.PUT(bodyPublisher)
                "DELETE" -> requestBuilder.DELETE()
                "PATCH"  -> requestBuilder.method("PATCH", bodyPublisher)
                else     -> requestBuilder.GET()
            }

            val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
            val elapsed = System.currentTimeMillis() - startMs

            val headers = response.headers().map().mapValues { it.value.joinToString(", ") }
            ResponseViewModel(
                statusCode = response.statusCode(),
                elapsedMs = elapsed,
                headers = headers,
                body = response.body() ?: ""
            )
        } catch (e: Exception) {
            ResponseViewModel(
                statusCode = 0,
                elapsedMs = 0,
                headers = emptyMap(),
                body = "",
                error = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    private fun buildUrlWithParams(url: String, params: Map<String, String>): String {
        val nonEmpty = params.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        if (nonEmpty.isEmpty()) return url
        val query = nonEmpty.entries.joinToString("&") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }
        return if ("?" in url) "$url&$query" else "$url?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
