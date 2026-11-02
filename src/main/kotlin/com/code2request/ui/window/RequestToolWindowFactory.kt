package com.code2request.ui.window

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class RequestToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = RequestToolWindowContent(project)
        val factory = ContentFactory.getInstance()
        toolWindow.contentManager.addContent(
            factory.createContent(content, "", false)
        )
    }
}
