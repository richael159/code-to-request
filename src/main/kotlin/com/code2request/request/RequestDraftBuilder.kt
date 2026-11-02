package com.code2request.request

import com.code2request.model.*

/**
 * Builds an initial RequestDraft from an EndpointModel and environment config.
 */
object RequestDraftBuilder {

    fun buildDraft(endpoint: EndpointModel, env: EnvironmentConfig): RequestDraft {
        val url = buildUrl(env.baseUrl, endpoint)
        val formDataParams = endpoint.params.filter { it.source == ParamSource.FORM_DATA }
        val hasFormData = formDataParams.isNotEmpty()

        val headers = buildHeaders(endpoint, hasFormData)

        return RequestDraft(
            endpointId = endpoint.uniqueId,
            environment = env.name,
            url = url,
            httpMethod = endpoint.httpMethod.name,
            headers = headers,
            queryParams = buildQueryParams(endpoint),
            body = "",  // body is filled separately via BodySampleGenerator
            formData = buildFormData(formDataParams),
            filePaths = buildFilePaths(formDataParams),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun buildUrl(baseUrl: String, endpoint: EndpointModel): String {
        val base = baseUrl.trimEnd('/')
        var path = endpoint.fullPath.let { if (it.startsWith("/")) it else "/$it" }

        // Replace {pathVar} placeholders with sample values
        endpoint.params
            .filter { it.source == ParamSource.PATH }
            .forEach { param ->
                val sample = smartSampleValue(param.name, param.type)
                path = path.replace("{${param.name}}", sample)
            }

        return base + path
    }

    private fun buildQueryParams(endpoint: EndpointModel): Map<String, String> =
        endpoint.params
            .filter { it.source == ParamSource.QUERY }
            .associate { param -> param.name to (param.defaultValue ?: smartSampleValue(param.name, param.type)) }

    private fun buildHeaders(endpoint: EndpointModel, hasFormData: Boolean): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        if (hasFormData) {
            // Content-Type is set automatically by HttpClient for multipart
        } else if (endpoint.requestBodyClass != null) {
            headers["Content-Type"] = "application/json"
        }
        endpoint.params
            .filter { it.source == ParamSource.HEADER }
            .forEach { headers[it.name] = smartSampleValue(it.name, it.type) }
        return headers
    }

    private fun buildFormData(formDataParams: List<ParamModel>): Map<String, String> =
        formDataParams
            .filter { !it.isFile }
            .associate { param -> param.name to smartSampleValue(param.name, param.type) }

    private fun buildFilePaths(formDataParams: List<ParamModel>): Map<String, String> =
        formDataParams
            .filter { it.isFile }
            .associate { param -> param.name to getOrCreateSampleFile(param.name) }

    /**
     * Returns the path of a small sample text file for the given field name.
     * The file is created once under the system temp directory and reused across drafts.
     */
    private fun getOrCreateSampleFile(fieldName: String): String {
        val dir = java.io.File(System.getProperty("java.io.tmpdir"), "code2request-samples")
        if (!dir.exists()) dir.mkdirs()
        val sampleFile = java.io.File(dir, "sample_$fieldName.txt")
        if (!sampleFile.exists()) {
            sampleFile.writeText("Code2Request sample file for field: $fieldName")
        }
        return sampleFile.absolutePath
    }

    private fun smartSampleValue(name: String, type: String): String =
        SmartSampleValues.forNameAsString(name, type) ?: defaultSampleValue(type)

    private fun defaultSampleValue(type: String): String = when {
        "String" in type -> "test"
        "int" in type || "Integer" in type -> "1"
        "long" in type || "Long" in type -> "1"
        "bool" in type || "Boolean" in type -> "true"
        "BigDecimal" in type -> "0"
        "double" in type || "Double" in type -> "0.0"
        "float" in type || "Float" in type -> "0.0"
        "LocalDate" in type -> "2024-01-01"
        "LocalDateTime" in type -> "2024-01-01T00:00:00"
        "Date" in type -> "2024-01-01"
        else -> ""
    }
}
