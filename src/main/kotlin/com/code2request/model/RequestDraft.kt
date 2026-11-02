package com.code2request.model

data class RequestDraft(
    val endpointId: String,
    val environment: String = "local",
    val url: String = "",
    val httpMethod: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val queryParams: Map<String, String> = emptyMap(),
    val body: String = "",
    /** Regular text form fields for multipart/form-data requests */
    val formData: Map<String, String> = emptyMap(),
    /** File fields for multipart/form-data: field name -> absolute local file path */
    val filePaths: Map<String, String> = emptyMap(),
    val remark: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isMultipart: Boolean get() = formData.isNotEmpty() || filePaths.isNotEmpty()
}
