package com.code2request.ui.gutter

import com.code2request.parser.SpringControllerParser
import com.code2request.ui.window.RequestToolWindowManager
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import icons.Code2RequestIcons
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getUParentForIdentifier

/**
 * Adds a green "run" gutter icon next to every Spring Controller method.
 * Clicking the icon opens the request panel pre-populated with a draft.
 *
 * Works for Java and Kotlin: the method-name identifier is mapped to a UAST [UMethod],
 * whose [UMethod.javaPsi] is a regular (or light) [PsiMethod] the parser understands.
 */
class RunRequestLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val method = methodForNameIdentifier(element) ?: return null
        if (!SpringControllerParser.isControllerMethod(method)) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            Code2RequestIcons.RUN_REQUEST,
            { "Run Request / Open Draft" },
            { _, elt -> methodForNameIdentifier(elt)?.let { RequestToolWindowManager.openForMethod(it) } },
            GutterIconRenderer.Alignment.LEFT,
            { "Run Request" }
        )
    }

    companion object {
        /**
         * Returns the method if [element] is the name identifier of a method declaration.
         * Line markers must be registered on leaf elements, so only identifiers are considered.
         */
        fun methodForNameIdentifier(element: PsiElement): PsiMethod? {
            // Fast path for Java
            if (element is PsiIdentifier) {
                val parent = element.parent as? PsiMethod ?: return null
                return if (parent.nameIdentifier == element) parent else null
            }
            // Kotlin (and other UAST languages): only leaf identifier tokens
            if (element.firstChild != null) return null
            if (element.node?.elementType?.toString() != "IDENTIFIER") return null
            val uMethod = getUParentForIdentifier(element) as? UMethod ?: return null
            return uMethod.javaPsi
        }
    }
}
