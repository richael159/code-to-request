package com.code2request.ui.panel

import com.code2request.model.RequestDraft
import com.code2request.storage.DraftStorageService
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Lists all locally saved request drafts for the current project.
 * Double-click or "Open" button loads the draft into the request panel.
 */
class DraftListPanel(
    private val project: Project,
    private val onOpen: (RequestDraft) -> Unit
) : JPanel(BorderLayout()) {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm")

    private val tableModel = object : DefaultTableModel(
        arrayOf("Method", "Path", "Env", "Remark", "Updated"), 0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val table = JBTable(tableModel)
    private var drafts: List<RequestDraft> = emptyList()

    init {
        setupUI()
        refresh()
    }

    private fun setupUI() {
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        table.columnModel.getColumn(0).minWidth = 40   // Method
        table.columnModel.getColumn(0).maxWidth = 55
        table.columnModel.getColumn(1).minWidth = 60   // Path
        table.columnModel.getColumn(2).minWidth = 30   // Env
        table.columnModel.getColumn(2).maxWidth = 50
        table.columnModel.getColumn(3).minWidth = 40   // Remark
        table.columnModel.getColumn(4).minWidth = 70   // Updated
        table.columnModel.getColumn(4).maxWidth = 85

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) getSelectedDraft()?.let { onOpen(it) }
            }
        })

        val openBtn = JButton("Open")
        val deleteBtn = JButton("Delete")
        val refreshBtn = JButton("Refresh")

        openBtn.addActionListener { getSelectedDraft()?.let { onOpen(it) } }

        deleteBtn.addActionListener {
            val draft = getSelectedDraft() ?: return@addActionListener
            val ok = JOptionPane.showConfirmDialog(
                this, "Delete draft for:\n${draft.endpointId}?",
                "Code2Request", JOptionPane.YES_NO_OPTION
            )
            if (ok == JOptionPane.YES_OPTION) {
                DraftStorageService.getInstance(project).deleteDraft(draft.endpointId)
                refresh()
            }
        }

        refreshBtn.addActionListener { refresh() }

        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(openBtn); add(deleteBtn); add(refreshBtn)
        }

        add(JBScrollPane(table), BorderLayout.CENTER)
        add(btnRow, BorderLayout.SOUTH)
    }

    fun refresh() {
        drafts = DraftStorageService.getInstance(project).getAllDrafts()
        tableModel.setRowCount(0)
        drafts.forEach { draft ->
            val path = draft.endpointId.split(":").lastOrNull() ?: draft.url
            tableModel.addRow(arrayOf(
                draft.httpMethod,
                path,
                draft.environment,
                draft.remark,
                dateFormat.format(Date(draft.updatedAt))
            ))
        }
    }

    private fun getSelectedDraft(): RequestDraft? {
        val row = table.selectedRow
        return if (row in drafts.indices) drafts[row] else null
    }
}
