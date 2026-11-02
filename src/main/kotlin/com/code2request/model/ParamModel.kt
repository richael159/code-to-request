package com.code2request.model

data class ParamModel(
    val name: String,
    val type: String,
    val required: Boolean = false,
    val source: ParamSource,
    val defaultValue: String? = null,
    val isFile: Boolean = false   // true when type is MultipartFile
)

enum class ParamSource {
    PATH, QUERY, HEADER, BODY_FIELD, FORM_DATA
}
