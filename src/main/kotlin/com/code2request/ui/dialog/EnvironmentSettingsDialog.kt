package com.code2request.ui.dialog

import com.code2request.model.EnvironmentConfig
import com.code2request.storage.EnvironmentConfigService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Dialog for managing environments: name, base URL, and common headers.
 * Opened via the ⚙ button next to the environment selector.
 *
 * The table shows Name and Base URL. Headers live in a hidden third model column
 * and are edited in the text area below for the selected row.
 */
class EnvironmentSettingsDialog(private val project: Project) : DialogWrapper(project) {

    private val tableModel = object : DefaultTableModel(arrayOf("Name", "Base URL", "Headers"), 0) {
        override fun isCellEditable(row: Int, column: Int) = column != COL_HEADERS
    }
    private val table = JBTable(tableModel)

    private val headersArea = JBTextArea(5, 40).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        emptyText.text = "Authorization: Bearer <token>"
    }
    private val headersLabel = JLabel()

    /** Model row whose headers are currently shown in [headersArea], or -1. */
    private var editingRow = -1

    init {
        title = "Manage Environments"
        init()
        loadEnvironments()
    }

    private fun loadEnvironments() {
        tableModel.setRowCount(0)
        EnvironmentConfigService.getInstance(project).getEnvironments().forEach { env ->
            tableModel.addRow(arrayOf(env.name, env.baseUrl, formatHeaders(env.commonHeaders)))
        }
        if (tableModel.rowCount > 0) table.selectionModel.setSelectionInterval(0, 0)
        else showHeadersFor(-1)
    }

    override fun createCenterPanel(): JComponent {
        // Hide the headers column from the table view (it stays in the model)
        table.removeColumn(table.columnModel.getColumn(COL_HEADERS))
        table.preferredScrollableViewportSize = Dimension(450, 150)
        table.columnModel.getColumn(0).preferredWidth = 100
        table.columnModel.getColumn(1).preferredWidth = 350
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        table.selectionModel.addListSelectionListener { e ->
            if (e.valueIsAdjusting) return@addListSelectionListener
            commitHeaders()
            showHeadersFor(table.selectedRow)
        }

        val decorator = ToolbarDecorator.createDecorator(table)
            .setAddAction {
                detachHeadersEditor()
                tableModel.addRow(arrayOf("new-env", "http://", ""))
                val newRow = tableModel.rowCount - 1
                table.selectionModel.setSelectionInterval(newRow, newRow)
                table.editCellAt(newRow, 0)
            }
            .setRemoveAction {
                val row = table.selectedRow
                detachHeadersEditor()
                if (row >= 0) tableModel.removeRow(row)
                showHeadersFor(table.selectedRow)
            }
            .setMoveUpAction {
                val row = table.selectedRow
                if (row > 0) {
                    detachHeadersEditor()
                    tableModel.moveRow(row, row, row - 1)
                    table.selectionModel.setSelectionInterval(row - 1, row - 1)
                }
            }
            .setMoveDownAction {
                val row = table.selectedRow
                if (row >= 0 && row < tableModel.rowCount - 1) {
                    detachHeadersEditor()
                    tableModel.moveRow(row, row, row + 1)
                    table.selectionModel.setSelectionInterval(row + 1, row + 1)
                }
            }
            .createPanel()

        val headersPanel = JPanel(BorderLayout(0, 4)).apply {
            add(headersLabel, BorderLayout.NORTH)
            add(JBScrollPane(headersArea), BorderLayout.CENTER)
        }

        val hint = JLabel(
            "<html>New requests open in the environment you used last (the first one if none).<br/>" +
                "Common headers are added to every request in that environment, one per line as " +
                "<code>Name: Value</code>. A header set on the request itself takes priority.</html>"
        ).apply { foreground = java.awt.Color.GRAY; font = font.deriveFont(11f) }

        val panel = JPanel(BorderLayout(0, 8))
        panel.add(decorator, BorderLayout.NORTH)
        panel.add(headersPanel, BorderLayout.CENTER)
        panel.add(hint, BorderLayout.SOUTH)
        return panel
    }

    private fun showHeadersFor(row: Int) {
        editingRow = row
        if (row in 0 until tableModel.rowCount) {
            val envName = tableModel.getValueAt(row, 0)?.toString().orEmpty()
            headersLabel.text = "Common headers for \"$envName\":"
            headersArea.text = tableModel.getValueAt(row, COL_HEADERS)?.toString().orEmpty()
            headersArea.isEnabled = true
        } else {
            headersLabel.text = "Common headers:"
            headersArea.text = ""
            headersArea.isEnabled = false
        }
        headersArea.caretPosition = 0
    }

    /** Writes the text area back into the model row it belongs to. */
    private fun commitHeaders() {
        if (editingRow in 0 until tableModel.rowCount) {
            tableModel.setValueAt(headersArea.text, editingRow, COL_HEADERS)
        }
    }

    /** Commit, then forget the row index (used before rows are added, removed or moved). */
    private fun detachHeadersEditor() {
        if (table.isEditing) table.cellEditor.stopCellEditing()
        commitHeaders()
        editingRow = -1
    }

    override fun doOKAction() {
        detachHeadersEditor()

        val envs = (0 until tableModel.rowCount).mapNotNull { row ->
            val name = tableModel.getValueAt(row, 0)?.toString()?.trim() ?: return@mapNotNull null
            val url = tableModel.getValueAt(row, 1)?.toString()?.trim() ?: return@mapNotNull null
            if (name.isBlank()) return@mapNotNull null
            val headers = parseHeaders(tableModel.getValueAt(row, COL_HEADERS)?.toString().orEmpty())
            EnvironmentConfig(name, url, headers.ifEmpty { null })
        }
        EnvironmentConfigService.getInstance(project).saveEnvironments(envs)
        super.doOKAction()
    }

    companion object {
        private const val COL_HEADERS = 2

        fun formatHeaders(headers: Map<String, String>): String =
            headers.entries.joinToString("\n") { (k, v) -> "$k: $v" }

        /** Parses "Name: Value" lines; blank lines and lines without ':' are ignored. */
        fun parseHeaders(text: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            text.lineSequence().forEach { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) return@forEach
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (key.isNotEmpty()) result[key] = value
            }
            return result
        }
    }
}
