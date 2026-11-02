package com.code2request.ui.panel

import com.code2request.model.RequestDraft
import com.code2request.storage.EnvironmentConfigService
import com.code2request.ui.dialog.EnvironmentSettingsDialog
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * The main request editing panel.
 * Contains: environment selector, method, URL, headers table, query params table, body editor.
 */
class RequestPanel(private val project: Project) : JPanel(BorderLayout()) {

    var onRun: ((RequestDraft) -> Unit)? = null
    var onSave: ((RequestDraft) -> Unit)? = null
    var onReset: (() -> Unit)? = null

    private val envCombo = JComboBox<String>()
    private val methodCombo = JComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH"))
    private val urlField = JTextField()

    private val headersModel = EditableTableModel(arrayOf("Name", "Value"))
    private val headersTable = JBTable(headersModel)

    private val queryModel = EditableTableModel(arrayOf("Name", "Value"))
    private val queryTable = JBTable(queryModel)

    private val bodyArea = JTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        tabSize = 2
    }

    private val formDataPanel = FormDataPanel()

    private var currentEndpointId: String = ""
    private var currentRemark: String = ""
    private lateinit var paramTabs: JTabbedPane

    /** Environment currently applied to the URL; used to swap the base URL on switch. */
    private var appliedEnvName: String? = null
    /** True while the combo is filled programmatically, so its events are ignored. */
    private var suppressEnvEvents = false

    init {
        setupUI()
        refreshEnvironments()
        envCombo.addActionListener { if (!suppressEnvEvents) onEnvironmentSwitched() }
    }

    private fun setupUI() {
        // ---- top section (vertical layout for right-side panel) ----
        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        }

        // Single row: Env ⚙ | Method | URL | Run Save Reset
        val row = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply { insets = Insets(2, 1, 2, 1); fill = GridBagConstraints.NONE; gridy = 0 }

        envCombo.preferredSize = Dimension(70, 28)
        envCombo.maximumSize = Dimension(70, 28)
        val envSettingsBtn = JButton("⚙").apply {
            preferredSize = Dimension(24, 28)
            maximumSize = Dimension(24, 28)
            minimumSize = Dimension(24, 28)
            toolTipText = "Manage Environments"
            margin = Insets(0, 2, 0, 2)
        }

        gbc.gridx = 0; row.add(envCombo, gbc)
        gbc.gridx = 1; row.add(envSettingsBtn, gbc)

        methodCombo.preferredSize = Dimension(72, 28)
        gbc.gridx = 2; row.add(methodCombo, gbc)

        gbc.gridx = 3; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL
        row.add(urlField, gbc)

        val runBtn = JButton("▶").apply { background = Color(76, 175, 80); foreground = Color.WHITE; toolTipText = "Run" }
        val saveBtn = JButton("💾").apply { toolTipText = "Save Draft" }
        val resetBtn = JButton("↺").apply { toolTipText = "Reset" }

        gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE
        gbc.gridx = 4; row.add(runBtn, gbc)
        gbc.gridx = 5; row.add(saveBtn, gbc)
        gbc.gridx = 6; row.add(resetBtn, gbc)

        row.maximumSize = Dimension(Int.MAX_VALUE, 34)
        topPanel.add(row)

        runBtn.addActionListener { onRun?.invoke(buildDraft()) }
        saveBtn.addActionListener { onSave?.invoke(buildDraft()) }
        resetBtn.addActionListener { onReset?.invoke() }

        envSettingsBtn.addActionListener {
            val dialog = EnvironmentSettingsDialog(project)
            if (dialog.showAndGet()) {
                refreshEnvironments()
            }
        }

        // ---- param tabs ----
        paramTabs = JTabbedPane()
        paramTabs.addTab("Headers", buildTablePanel(headersTable))
        paramTabs.addTab("Query", buildTablePanel(queryTable))
        paramTabs.addTab("Body", JBScrollPane(bodyArea))
        paramTabs.addTab("Form", formDataPanel)

        add(topPanel, BorderLayout.NORTH)
        add(paramTabs, BorderLayout.CENTER)
    }

    private fun buildTablePanel(table: JBTable): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JBScrollPane(table), BorderLayout.CENTER)

        val addBtn = JButton("+")
        val removeBtn = JButton("-")
        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(addBtn); add(removeBtn)
        }
        panel.add(btnRow, BorderLayout.SOUTH)

        addBtn.addActionListener { (table.model as DefaultTableModel).addRow(arrayOf("", "")) }
        removeBtn.addActionListener {
            val row = table.selectedRow
            if (row >= 0) (table.model as DefaultTableModel).removeRow(row)
        }
        return panel
    }

    fun refreshEnvironments() {
        val envs = EnvironmentConfigService.getInstance(project).getEnvironments()
        val current = envCombo.selectedItem as? String
        suppressEnvEvents = true
        try {
            envCombo.removeAllItems()
            envs.forEach { envCombo.addItem(it.name) }
            if (current != null) envCombo.selectedItem = current
        } finally {
            suppressEnvEvents = false
        }
    }

    /**
     * Replaces the old environment's base URL at the start of the URL with the new one,
     * e.g. http://localhost:8080/api/users -> http://dev.example.com/api/users.
     * If the user has typed a URL that doesn't start with the old base URL, it is left alone.
     */
    private fun onEnvironmentSwitched() {
        val newName = envCombo.selectedItem as? String ?: return
        val service = EnvironmentConfigService.getInstance(project)
        service.rememberEnvironment(newName)

        val oldName = appliedEnvName
        appliedEnvName = newName
        if (oldName == null || oldName == newName) return

        val oldBase = service.getEnvironment(oldName)?.baseUrl?.trimEnd('/') ?: return
        val newBase = service.getEnvironment(newName)?.baseUrl?.trimEnd('/') ?: return
        val url = urlField.text.trim()
        if (oldBase.isNotEmpty() && url.startsWith(oldBase)) {
            urlField.text = newBase + url.removePrefix(oldBase)
        }
    }

    fun loadDraft(draft: RequestDraft) {
        currentEndpointId = draft.endpointId
        currentRemark = draft.remark
        suppressEnvEvents = true
        try {
            envCombo.selectedItem = draft.environment
        } finally {
            suppressEnvEvents = false
        }
        appliedEnvName = draft.environment
        methodCombo.selectedItem = draft.httpMethod
        urlField.text = draft.url

        headersModel.setRowCount(0)
        draft.headers.forEach { (k, v) -> headersModel.addRow(arrayOf(k, v)) }
        headersModel.addRow(arrayOf("", ""))

        queryModel.setRowCount(0)
        draft.queryParams.forEach { (k, v) -> queryModel.addRow(arrayOf(k, v)) }
        queryModel.addRow(arrayOf("", ""))

        bodyArea.text = draft.body
        bodyArea.caretPosition = 0

        formDataPanel.load(draft.formData, draft.filePaths)

        // Open the tab that holds the interesting part of the request:
        // form data > JSON body > query params > headers
        paramTabs.selectedIndex = when {
            draft.filePaths.isNotEmpty() || draft.formData.isNotEmpty() -> TAB_FORM_DATA
            draft.body.isNotBlank() -> TAB_BODY
            draft.queryParams.isNotEmpty() -> TAB_QUERY
            else -> TAB_HEADERS
        }
    }

    fun buildDraft(): RequestDraft {
        fun tableToMap(model: DefaultTableModel): Map<String, String> =
            (0 until model.rowCount)
                .mapNotNull { row ->
                    val k = model.getValueAt(row, 0)?.toString()?.trim() ?: return@mapNotNull null
                    val v = model.getValueAt(row, 1)?.toString()?.trim() ?: ""
                    if (k.isNotEmpty()) k to v else null
                }.toMap()

        return RequestDraft(
            endpointId = currentEndpointId,
            environment = envCombo.selectedItem?.toString() ?: "local",
            url = urlField.text.trim(),
            httpMethod = methodCombo.selectedItem?.toString() ?: "GET",
            headers = tableToMap(headersModel),
            queryParams = tableToMap(queryModel),
            body = bodyArea.text,
            formData = formDataPanel.getTextFields(),
            filePaths = formDataPanel.getFileFields(),
            remark = currentRemark
        )
    }

    companion object {
        // Must match the order of paramTabs.addTab(...) in setupUI()
        private const val TAB_HEADERS = 0
        private const val TAB_QUERY = 1
        private const val TAB_BODY = 2
        private const val TAB_FORM_DATA = 3
    }
}

class EditableTableModel(columns: Array<String>) : DefaultTableModel(columns, 1) {
    override fun isCellEditable(row: Int, column: Int): Boolean = true
}
