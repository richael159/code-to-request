package com.code2request.model

data class EndpointModel(
    val module: String,
    val controllerClass: String,
    val methodName: String,
    val httpMethod: HttpMethod,
    val classPath: String,
    val methodPath: String,
    val params: List<ParamModel>,
    val requestBodyClass: String? = null
) {
    val fullPath: String
        get() = normalizePath(classPath) + normalizePath(methodPath)

    val uniqueId: String
        get() = "$module:$controllerClass:$methodName:${httpMethod.name}:$fullPath"

    private fun normalizePath(path: String): String {
        if (path.isEmpty()) return ""
        return if (path.startsWith("/")) path else "/$path"
    }
}

enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH
}
