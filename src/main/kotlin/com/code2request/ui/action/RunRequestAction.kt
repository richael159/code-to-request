package com.code2request.ui.action

import com.code2request.parser.SpringControllerParser
import com.code2request.ui.window.RequestToolWindowManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

/**
 * Right-click context menu action: "Run Request".
 * Visible only when the caret is inside a Spring Controller method (Java or Kotlin).
 */
class RunRequestAction : AnAction() {

    // update() reads PSI, so run it on a background thread (required since 2022.3)
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val method = resolveMethod(e) ?: return
        RequestToolWindowManager.openForMethod(method)
    }

    override fun update(e: AnActionEvent) {
        val method = resolveMethod(e)
        e.presentation.isEnabledAndVisible = method != null &&
            SpringControllerParser.isControllerMethod(method)
    }

    private fun resolveMethod(e: AnActionEvent): PsiMethod? {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        return enclosingMethod(element)
    }

    /** Walks up from the caret to the nearest method declaration, via UAST so Kotlin works too. */
    private fun enclosingMethod(start: PsiElement): PsiMethod? {
        var current: PsiElement? = start
        while (current != null && current !is PsiFile) {
            if (current is PsiMethod) return current
            val uMethod = current.toUElementOfType<UMethod>()
            if (uMethod != null) return uMethod.javaPsi
            current = current.parent
        }
        return null
    }
}
