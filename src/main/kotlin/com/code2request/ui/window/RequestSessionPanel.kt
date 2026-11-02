package com.code2request.ui.window

import com.code2request.executor.HttpRequestExecutor
import com.code2request.model.RequestDraft
import com.code2request.storage.DraftStorageService
import com.code2request.storage.EnvironmentConfigService
import com.code2request.ui.panel.RequestPanel
import com.code2request.ui.panel.ResponsePanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTabbedPane
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import java.awt.BorderLayout

/**
 * A self-contained panel for one request session (one endpoint).
 * Contains Request and Response sub-tabs and runs its own HTTP call independently.
 */
class RequestSessionPanel(
    private val project: Project,
    private val onDraftSaved: () -> Unit
) : JPanel(BorderLayout()) {

    val requestPanel = RequestPanel(project)
    val responsePanel = ResponsePanel()

    /** The endpoint unique ID this session is bound to (used for deduplication). */
    var endpointId: String = ""

    /** The original code-generated draft — restored when user clicks Reset. */
    private var originalDraft: RequestDraft? = null

    private val innerTabs = JBTabbedPane()

    init {
        innerTabs.addTab("Request", requestPanel)
        innerTabs.addTab("Response", responsePanel)
        add(innerTabs, BorderLayout.CENTER)

        requestPanel.onRun = { draft -> runDraft(draft) }

        requestPanel.onSave = save@{ draft ->
            val remark = JOptionPane.showInputDialog(
                this, "Remark (optional):", "Save Draft",
                JOptionPane.PLAIN_MESSAGE, null, null,
                draft.remark
            ) as? String ?: return@save
            val draftWithRemark = draft.copy(remark = remark.trim())
            DraftStorageService.getInstance(project).saveDraft(draftWithRemark)
            onDraftSaved()
            JOptionPane.showMessageDialog(
                this, "Draft saved!", "Code2Request", JOptionPane.INFORMATION_MESSAGE
            )
        }

        requestPanel.onReset = {
            originalDraft?.let { requestPanel.loadDraft(it) }
        }
    }

    fun loadDraft(draft: RequestDraft) {
        if (originalDraft == null) {
            originalDraft = draft   // snapshot the code-generated draft on first load
        }
        endpointId = draft.endpointId
        requestPanel.refreshEnvironments()
        requestPanel.loadDraft(draft)
        innerTabs.selectedIndex = 0
    }

    private fun runDraft(draft: RequestDraft) {
        innerTabs.selectedIndex = 1
        responsePanel.showLoading()

        // Add the environment's common headers (e.g. Authorization) at send time,
        // so changing a token in the environment applies to every saved draft.
        val envService = EnvironmentConfigService.getInstance(project)
        envService.rememberEnvironment(draft.environment)
        val env = envService.getEnvironment(draft.environment)
        val toSend = if (env != null) draft.copy(headers = env.mergeInto(draft.headers)) else draft

        ApplicationManager.getApplication().executeOnPooledThread {
            val response = HttpRequestExecutor.execute(toSend)
            SwingUtilities.invokeLater {
                responsePanel.showResponse(response)
            }
        }
    }
}
