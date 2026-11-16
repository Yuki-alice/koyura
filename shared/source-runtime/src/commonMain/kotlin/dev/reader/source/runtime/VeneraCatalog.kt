package dev.reader.source.runtime

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Milestone 1: repository index protocol.
 *
 * Mirrors `lib/features/comic_source/source_repositories.dart` (`parseCatalog`,
 * `normalizeUrl`, `entryFor`) and `readSourceText` transport rules:
 * - Reads UTF-8 JSON array via Ktor (plain text, followRedirects from [applyReaderDefaults]).
 * - Strips a leading BOM (`\uFEFF`), skips invalid entries with labels, prefers
 *   non-empty `url` over `fileName`, resolves relative targets against the final
 *   index URL after redirects.
 */
data class VeneraRepository(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
)

data class VeneraCatalogEntry(
    val key: String,
    val name: String,
    val version: String,
    val url: String,
    val description: String = "",
)

data class VeneraCatalog(
    val entries: List<VeneraCatalogEntry>,
    val skipped: List<String>,
)

data class VeneraOrigin(
    val kind: String,
    val repositoryId: String? = null,
    val repositoryName: String? = null,
    val url: String? = null,
)

sealed class VeneraCatalogError(message: String) : IllegalStateException(message) {
    class InvalidUrl(value: String) : VeneraCatalogError("Invalid repository URL: $value")
    class Unavailable(status: Int) : VeneraCatalogError("Repository unavailable: HTTP $status")
    class InvalidCatalog(cause: Throwable? = null) : VeneraCatalogError("Invalid repository catalog${cause?.let { ": ${it.message}" } ?: ""}")
    class EmptyCatalog : VeneraCatalogError("Repository catalog is empty")
    class MissingSource(key: String) : VeneraCatalogError("Source not in catalog: $key")
    class AmbiguousSource(key: String) : VeneraCatalogError("Multiple catalog variants for key: $key")
}

private val catalogKeyPattern = Regex("""^\w+$""")
private val catalogVersionPattern = Regex("""^\d+\.\d+\.\d+(?:[.\-].+)?$""")
private val catalogJson = Json { ignoreUnknownKeys = true; isLenient = false }

private val httpSchemePrefix = Regex("""(?i)^https?://.+""")

/** Normalizes to http/https with non-empty host and no fragment. Throws [VeneraCatalogError.InvalidUrl]. */
fun normalizeVeneraUrl(value: String): String {
    val trimmed = value.trim()
    // Ktor Url("") defaults to localhost; require explicit http(s):// like Dart Uri.
    if (!httpSchemePrefix.matches(trimmed)) throw VeneraCatalogError.InvalidUrl(value)
    val parsed = try {
        Url(trimmed)
    } catch (_: Exception) {
        throw VeneraCatalogError.InvalidUrl(value)
    }
    if (parsed.protocol.name != "http" && parsed.protocol.name != "https") {
        throw VeneraCatalogError.InvalidUrl(value)
    }
    if (parsed.host.isBlank()) throw VeneraCatalogError.InvalidUrl(value)
    val built = URLBuilder(parsed).apply { fragment = "" }.buildString()
    // Mirror Dart Uri.toString() which emits "/" for an empty path.
    if (parsed.encodedPath.isEmpty() && !built.endsWith("/")) return "$built/"
    return built
}

private val absoluteUrlPattern = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]*:""")

/**
 * RFC 3986-style resolution of [target] against [base] (the final index URL).
 * Handles absolute URLs, scheme-relative `//host/...`, root-relative `/...`,
 * and relative paths with `.`/`..`, preserving query strings.
 */
internal fun resolveAgainstBase(base: Url, target: String): String {
    val trimmed = target.trim()
    if (absoluteUrlPattern.containsMatchIn(trimmed)) return trimmed
    val authority = buildString {
        append(base.protocol.name)
        append("://")
        append(base.host)
        val defaultPort = if (base.protocol.name == "https") 443 else 80
        if (base.port != defaultPort) append(":${base.port}")
    }
    if (trimmed.startsWith("//")) return "${base.protocol.name}:$trimmed"
    if (trimmed.startsWith("/")) return authority + trimmed
    val pathAndQuery = trimmed.split("?", limit = 2)
    val relPath = pathAndQuery[0]
    val query = if (pathAndQuery.size > 1) "?${pathAndQuery[1]}" else ""
    val basePath = base.encodedPath
    val baseDir = if (basePath.endsWith("/")) basePath else basePath.substringBeforeLast("/", "") + "/"
    val merged = baseDir + relPath
    val normalized = normalizePathSegments(merged)
    return authority + normalized + query
}

private fun normalizePathSegments(path: String): String {
    val absolute = path.startsWith("/")
    val out = ArrayDeque<String>()
    for (segment in path.split("/")) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (out.isNotEmpty()) out.removeLast()
            else -> out.add(segment)
        }
    }
    val joined = out.joinToString("/")
    return if (absolute) "/$joined" else joined
}

/**
 * Parses a catalog JSON array. [baseUrl] must be the final index URL after
 * redirects; relative `url`/`fileName` targets resolve against it.
 */
fun parseVeneraCatalog(contents: String, baseUrl: String? = null): VeneraCatalog {
    val base = baseUrl?.let {
        try {
            Url(normalizeVeneraUrl(it))
        } catch (e: VeneraCatalogError.InvalidUrl) {
            throw VeneraCatalogError.InvalidCatalog(e)
        }
    }
    val withoutBom = contents.replaceFirst("\uFEFF", "")
    val json: JsonElement = try {
        catalogJson.parseToJsonElement(withoutBom)
    } catch (e: Exception) {
        throw VeneraCatalogError.InvalidCatalog(e)
    }
    if (json !is JsonArray) throw VeneraCatalogError.InvalidCatalog()
    val entries = mutableListOf<VeneraCatalogEntry>()
    val skipped = mutableListOf<String>()
    for ((index, item) in json.withIndex()) {
        val record = item
        val obj = try { record.jsonObject } catch (_: Exception) { null }
        val rawKey = obj?.get("key")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        val label = if (!rawKey.isNullOrBlank()) rawKey.trim() else "#${index + 1}"
        val name = obj?.get("name")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        val version = obj?.get("version")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        if (obj == null || rawKey == null || name == null || version == null ||
            !catalogKeyPattern.matches(rawKey) || !catalogVersionPattern.matches(version)
        ) {
            skipped.add(label)
            continue
        }
        val key = rawKey
        fun stringField(field: String): String? =
            obj[field]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                ?.trim()?.takeIf { it.isNotEmpty() }
        // Non-empty url wins over fileName (upstream priority).
        val target = stringField("url") ?: stringField("fileName")
        if (target.isNullOrEmpty()) {
            skipped.add(label)
            continue
        }
        try {
            val resolved = if (base == null) target else resolveAgainstBase(base, target)
            val normalized = normalizeVeneraUrl(resolved)
            val descElement = obj["description"]
            val descText = (descElement as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeIf { it.isString }?.content
                ?: descElement?.toString() ?: ""
            entries.add(VeneraCatalogEntry(key, name, version, normalized, descText))
        } catch (_: Exception) {
            skipped.add(label)
        }
    }
    if (entries.isEmpty() && skipped.isNotEmpty()) throw VeneraCatalogError.EmptyCatalog()
    return VeneraCatalog(entries.toList(), skipped.toList())
}

/**
 * Matches same-key variants: exact previous URL wins, single candidate wins,
 * otherwise the caller must disambiguate.
 */
fun selectCatalogEntry(key: String, entries: List<VeneraCatalogEntry>, previousUrl: String?): VeneraCatalogEntry {
    val candidates = entries.filter { it.key == key }
    candidates.firstOrNull { it.url == previousUrl }?.let { return it }
    if (candidates.size == 1) return candidates.single()
    if (candidates.isEmpty()) throw VeneraCatalogError.MissingSource(key)
    throw VeneraCatalogError.AmbiguousSource(key)
}

/** Reuses [VeneraVersion] rules (including hotfix): true when [candidate] > [current]. */
fun isVeneraUpdateAvailable(candidate: String, current: String): Boolean {
    val parsedCandidate = try {
        VeneraVersion.parse(candidate)
    } catch (_: Exception) {
        return false
    }
    val parsedCurrent = try {
        VeneraVersion.parse(current)
    } catch (_: Exception) {
        return true
    }
    return parsedCandidate > parsedCurrent
}

/**
 * Loads a catalog via Ktor and parses with the final URL as base.
 * Throws [VeneraCatalogError.Unavailable] on non-200.
 */
suspend fun loadVeneraCatalog(client: HttpClient, repositoryUrl: String): VeneraCatalog {
    val normalized = normalizeVeneraUrl(repositoryUrl)
    val response = client.get(normalized)
    if (response.status.value != 200) throw VeneraCatalogError.Unavailable(response.status.value)
    val finalUrl = response.call.request.url.toString()
    val body = response.bodyAsText()
    return parseVeneraCatalog(body, baseUrl = finalUrl)
}
