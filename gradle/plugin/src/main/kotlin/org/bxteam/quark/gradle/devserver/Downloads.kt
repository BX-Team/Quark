package org.bxteam.quark.gradle.devserver

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * HTTP helpers for dev server tasks. Only ever called from task actions.
 */
internal object Downloads {
    private const val USER_AGENT = "BX-Team/Quark-Gradle (+https://github.com/BX-Team/Quark)"

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    /** GETs a URL and parses the body as JSON (maps, lists, strings, numbers, booleans). */
    fun json(url: String): Any? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw HttpStatusException(response.statusCode(), url, errorDetails(response.body()))
        }
        return JsonSlurper().parseText(response.body())
    }

    /** Downloads [url] to [target] through a temporary file, so an interrupted download leaves nothing behind. */
    fun file(url: String, target: File) {
        target.parentFile.mkdirs()
        val temporary = File(target.parentFile, target.name + ".part")
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(5))
            .header("User-Agent", USER_AGENT)
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary.toPath()))
        if (response.statusCode() !in 200..299) {
            temporary.delete()
            throw GradleException("HTTP ${response.statusCode()} downloading $url")
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Error messages of APIs answering `{"errors": [...]}` (MCJars) or `{"description": "..."}` (Modrinth). */
    private fun errorDetails(body: String): String? = try {
        val json = JsonSlurper().parseText(body) as? Map<*, *>
        (json?.get("errors") as? List<*>)?.joinToString("; ") ?: json?.get("description")?.toString()
    } catch (e: Exception) {
        null
    }

    fun encode(segment: String): String = URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
}

/** A non-2xx answer; [details] holds the API's error message if it sent one. */
internal class HttpStatusException(val status: Int, url: String, val details: String?) :
    GradleException("HTTP $status from $url" + (details?.let { ": $it" } ?: ""))

/** Typed access to the untyped JSON produced by [Downloads.json]. */
@Suppress("UNCHECKED_CAST")
internal fun Any?.obj(): Map<String, Any?> = this as? Map<String, Any?> ?: throw GradleException("Expected a JSON object, got $this")

@Suppress("UNCHECKED_CAST")
internal fun Any?.arr(): List<Any?> = this as? List<Any?> ?: throw GradleException("Expected a JSON array, got $this")
