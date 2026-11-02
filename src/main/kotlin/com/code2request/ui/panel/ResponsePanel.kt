package com.code2request.ui.panel

import com.code2request.model.ResponseViewModel
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.ui.components.JBScrollPane
import java.awt.*
import javax.swing.*

/**
 * Displays HTTP response: status code, elapsed time, headers, and formatted body.
 */
class ResponsePanel : JPanel(BorderLayout()) {

    private val statusLabel = JLabel("—").apply {
        font = Font(Font.SANS_SERIF, Font.BOLD, 14)
        border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
    }

    private val bodyArea = JTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }

    private val headersArea = JTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        isEditable = false
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()

    init {
        val tabs = JTabbedPane()
        tabs.addTab("Body", JBScrollPane(bodyArea))
        tabs.addTab("Headers", JBScrollPane(headersArea))

        add(statusLabel, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)
    }

    fun showLoading() {
        statusLabel.text = "Sending request..."
        statusLabel.foreground = Color.GRAY
        bodyArea.text = ""
        headersArea.text = ""
    }

    fun showResponse(response: ResponseViewModel) {
        if (response.isError) {
            statusLabel.text = "Error: ${response.error}"
            statusLabel.foreground = Color(200, 30, 30)
            bodyArea.text = response.error ?: ""
            headersArea.text = ""
            return
        }

        val (color, label) = statusDisplay(response.statusCode)
        statusLabel.text = "${response.statusCode}  $label   ${response.elapsedMs} ms"
        statusLabel.foreground = color

        bodyArea.text = formatBody(response.body)
        bodyArea.caretPosition = 0

        headersArea.text = response.headers.entries.joinToString("\n") { (k, v) -> "$k: $v" }
    }

    private fun formatBody(body: String): String {
        if (body.isBlank()) return ""
        return try {
            gson.toJson(JsonParser.parseString(body))
        } catch (_: Exception) {
            body
        }
    }

    private fun statusDisplay(code: Int): Pair<Color, String> = when (code) {
        in 200..299 -> Color(0, 150, 60) to httpStatusText(code)
        in 300..399 -> Color(180, 140, 0) to httpStatusText(code)
        in 400..499 -> Color(210, 80, 0) to httpStatusText(code)
        else -> Color(200, 30, 30) to httpStatusText(code)
    }

    private fun httpStatusText(code: Int): String = when (code) {
        200 -> "OK"; 201 -> "Created"; 204 -> "No Content"; 206 -> "Partial Content"
        301 -> "Moved Permanently"; 302 -> "Found"; 304 -> "Not Modified"
        400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
        404 -> "Not Found"; 405 -> "Method Not Allowed"; 409 -> "Conflict"
        422 -> "Unprocessable Entity"; 429 -> "Too Many Requests"
        500 -> "Internal Server Error"; 502 -> "Bad Gateway"; 503 -> "Service Unavailable"
        else -> ""
    }
}
