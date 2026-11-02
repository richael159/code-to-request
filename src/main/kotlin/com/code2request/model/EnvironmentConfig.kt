package com.code2request.model

data class EnvironmentConfig(
    val name: String,
    val baseUrl: String,
    /**
     * Headers sent with every request in this environment (e.g. Authorization).
     * Nullable on purpose: Gson leaves it null when reading configs saved by older versions.
     */
    val headers: Map<String, String>? = null,
) {
    val commonHeaders: Map<String, String>
        get() = headers ?: emptyMap()

    /**
     * Returns [requestHeaders] with this environment's common headers added.
     * A header set on the request itself wins (names compared case-insensitively).
     */
    fun mergeInto(requestHeaders: Map<String, String>): Map<String, String> {
        val own = requestHeaders.keys.map { it.lowercase() }.toSet()
        val result = LinkedHashMap<String, String>()
        commonHeaders.forEach { (k, v) -> if (k.lowercase() !in own) result[k] = v }
        result.putAll(requestHeaders)
        return result
    }
}

val DEFAULT_ENVIRONMENTS = listOf(
    EnvironmentConfig("local", "http://localhost:8080"),
    EnvironmentConfig("dev", "http://dev.example.com"),
    EnvironmentConfig("test", "http://test.example.com")
)
