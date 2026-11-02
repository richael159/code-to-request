package com.code2request.model

data class ResponseViewModel(
    val statusCode: Int,
    val elapsedMs: Long,
    val headers: Map<String, String>,
    val body: String,
    val error: String? = null
) {
    val isSuccess: Boolean get() = statusCode in 200..299
    val isError: Boolean get() = error != null
}
