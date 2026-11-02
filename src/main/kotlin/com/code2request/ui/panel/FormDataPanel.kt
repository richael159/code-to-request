package com.code2request.ui.panel

import com.intellij.ui.components.JBScrollPane
import java.awt.*
import javax.swing.*

/**
 * Form Data tab for multipart/form-data requests.
 * Each row has: Name | Type (Text/File) | Value | [Browse] | [-]
 */
class FormDataPanel : JPanel(BorderLayout()) {

    private data class Entry(
        val nameField: JTextField = JTextField(8),
        val typeCombo: JComboBox<String> = JComboBox(arrayOf("Text", "File")),
        val valueField: JTextField = JTextField(10),
        val browseBtn: JButton = JButton("Browse..."),
        val removeBtn: JButton = JButton("-"),
        val row: JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
    )

    private val entries = mutableListOf<Entry>()
    private val rowsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    init {
        val scroll = JBScrollPane(rowsPanel)
        add(scroll, BorderLayout.CENTER)

        val addBtn = JButton("+ Add Field")
        val hint = JLabel("  Select Type=File to upload a file")
            .apply { foreground = Color.GRAY; font = font.deriveFont(Font.ITALIC, 11f) }
        val bottom = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(addBtn); add(hint)
        }
        add(bottom, BorderLayout.SOUTH)

        addBtn.addActionListener { addEntry("", "Text", "") }
        addEntry("", "Text", "")
    }

    private fun addEntry(name: String, type: String, value: String) {
        val e = Entry()
        e.nameField.text = name
        e.typeCombo.selectedItem = type
        e.valueField.text = value
        e.browseBtn.isVisible = (type == "File")

        e.row.apply {
            add(JLabel("Name:")); add(e.nameField)
            add(e.typeCombo)
            add(e.valueField)
            add(e.browseBtn)
            add(e.removeBtn)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        e.typeCombo.addActionListener {
            val isFile = e.typeCombo.selectedItem == "File"
            e.browseBtn.isVisible = isFile
            if (isFile && !e.valueField.text.contains(java.io.File.separator)) {
                e.valueField.text = ""
            }
        }

        e.browseBtn.addActionListener {
            val chooser = JFileChooser()
            chooser.dialogTitle = "Select file for \"${e.nameField.text.ifBlank { "field" }}\""
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                e.valueField.text = chooser.selectedFile.absolutePath
            }
        }

        e.removeBtn.addActionListener {
            entries.remove(e)
            rowsPanel.remove(e.row)
            rowsPanel.revalidate()
            rowsPanel.repaint()
        }

        entries.add(e)
        rowsPanel.add(e.row)
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    fun getTextFields(): Map<String, String> =
        entries.filter { it.typeCombo.selectedItem == "Text" && it.nameField.text.isNotBlank() }
            .associate { it.nameField.text.trim() to it.valueField.text.trim() }

    fun getFileFields(): Map<String, String> =
        entries.filter { it.typeCombo.selectedItem == "File" && it.nameField.text.isNotBlank() }
            .associate { it.nameField.text.trim() to it.valueField.text.trim() }

    fun load(textFields: Map<String, String>, fileFields: Map<String, String>) {
        entries.clear()
        rowsPanel.removeAll()
        textFields.forEach { (k, v) -> addEntry(k, "Text", v) }
        fileFields.forEach { (k, v) -> addEntry(k, "File", v) }
        if (entries.isEmpty()) addEntry("", "Text", "")
    }

    fun hasContent(): Boolean =
        entries.any { it.nameField.text.isNotBlank() }
}
