package com.code2request.ui.window

import com.code2request.model.RequestDraft
import com.code2request.ui.panel.DraftListPanel
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTabbedPane
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

/**
 * Root component of the Code2Request tool window.
 *
 * Layout:
 *   [GET /users ×] [POST /users ×] [GET /users/{id} ×] … [Drafts]
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │  [Request] [Response]  (inner tabs, per session)             │
 *   └──────────────────────────────────────────────────────────────┘
 *
 * - Each endpoint opens in its own session tab.
 * - If the same endpoint is already open, focus that tab instead of duplicating.
 * - Requests run independently in background threads — multiple can be in-flight at once.
 * - "Drafts" is a permanent tab at the end.
 */
class RequestToolWindowContent(private val project: Project) : JPanel(BorderLayout()) {

    private val outerTabs = JBTabbedPane()

    private val draftListPanel = DraftListPanel(project) { draft ->
        val title = tabTitle(draft.httpMethod, draft.endpointId.split(":").lastOrNull() ?: draft.url)
        openSession(draft, title)
    }

    init {
        outerTabs.addTab("Drafts", draftListPanel)
        add(outerTabs, BorderLayout.CENTER)
    }

    /**
     * Opens a session tab for the given draft.
     * Reuses an existing tab if one with the same endpointId is already open.
     */
    fun openSession(draft: RequestDraft, title: String) {
        // Check for an existing tab with the same endpoint
        for (i in 0 until outerTabs.tabCount) {
            val comp = outerTabs.getComponentAt(i)
            if (comp is RequestSessionPanel && comp.endpointId == draft.endpointId) {
                comp.loadDraft(draft)
                outerTabs.selectedIndex = i
                return
            }
        }

        // Create a new session tab before the Drafts tab
        val session = RequestSessionPanel(project) { draftListPanel.refresh() }
        session.loadDraft(draft)

        val insertAt = outerTabs.tabCount - 1   // just before Drafts
        outerTabs.insertTab(title, null, session, draft.endpointId, insertAt)
        outerTabs.setTabComponentAt(insertAt, CloseableTabTitle(title) {
            outerTabs.remove(session)
        })
        outerTabs.selectedIndex = insertAt
    }

    private fun tabTitle(method: String, path: String): String {
        val full = "$method $path"
        return if (full.length > 38) full.take(35) + "…" else full
    }
}

/**
 * Tab header component: label + × close button.
 */
private class CloseableTabTitle(
    title: String,
    onClose: () -> Unit
) : JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)) {

    init {
        isOpaque = false

        val label = JLabel(title)
        val closeBtn = JButton("×").apply {
            preferredSize = java.awt.Dimension(16, 16)
            border = BorderFactory.createEmptyBorder()
            isContentAreaFilled = false
            isFocusable = false
            toolTipText = "Close tab"
            addActionListener { onClose() }
        }

        add(label)
        add(closeBtn)
    }
}
