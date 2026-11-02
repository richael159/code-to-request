package com.code2request.executor

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.http.HttpRequest

/**
 * Builds a multipart/form-data request body for file uploads.
 */
object MultipartBodyPublisher {

    data class Result(
        val contentType: String,
        val publisher: HttpRequest.BodyPublisher
    )

    fun build(textFields: Map<String, String>, fileFields: Map<String, String>): Result {
        val boundary = "----Code2RequestBoundary${System.currentTimeMillis()}"
        val CRLF = "\r\n"

        val out = ByteArrayOutputStream()

        fun write(text: String) = out.write(text.toByteArray(Charsets.UTF_8))

        textFields.forEach { (name, value) ->
            write("--$boundary$CRLF")
            write("Content-Disposition: form-data; name=\"$name\"$CRLF")
            write(CRLF)
            write(value)
            write(CRLF)
        }

        fileFields.forEach { (name, path) ->
            val file = File(path)
            if (!file.exists() || !file.isFile) return@forEach
            val mime = guessMimeType(file.name)
            write("--$boundary$CRLF")
            write("Content-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"$CRLF")
            write("Content-Type: $mime$CRLF")
            write(CRLF)
            out.write(file.readBytes())
            write(CRLF)
        }

        write("--$boundary--$CRLF")

        val bytes = out.toByteArray()
        return Result(
            contentType = "multipart/form-data; boundary=$boundary",
            publisher = HttpRequest.BodyPublishers.ofByteArray(bytes)
        )
    }

    private fun guessMimeType(fileName: String): String = when (
        fileName.substringAfterLast(".").lowercase()
    ) {
        "jpg", "jpeg" -> "image/jpeg"
        "png"         -> "image/png"
        "gif"         -> "image/gif"
        "webp"        -> "image/webp"
        "pdf"         -> "application/pdf"
        "txt"         -> "text/plain"
        "json"        -> "application/json"
        "xml"         -> "application/xml"
        "zip"         -> "application/zip"
        "tar", "gz"   -> "application/gzip"
        "csv"         -> "text/csv"
        "mp4"         -> "video/mp4"
        "mp3"         -> "audio/mpeg"
        else          -> "application/octet-stream"
    }
}
