package com.code2request.ui.window

import com.code2request.parser.SpringControllerParser
import com.code2request.request.BodySampleGenerator
import com.code2request.request.RequestDraftBuilder
import com.code2request.storage.DraftStorageService
import com.code2request.storage.EnvironmentConfigService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiMethod

/**
 * Orchestrates opening the Code2Request tool window for a given controller method.
 * Always regenerates the draft from code so that parameter changes are reflected.
 */
object RequestToolWindowManager {

    fun openForMethod(psiMethod: PsiMethod) {
        val project = psiMethod.project

        ApplicationManager.getApplication().runReadAction {
            val endpoint = try {
                SpringControllerParser.parseMethod(psiMethod)
            } catch (e: Exception) {
                notify(project, "Parse failed: ${e.message}")
                null
            }
            if (endpoint == null) {
                notify(project, "Not a valid Spring controller method")
                return@runReadAction
            }

            val storage = DraftStorageService.getInstance(project)
            val envService = EnvironmentConfigService.getInstance(project)
            val existingDraft = storage.getDraft(endpoint.uniqueId)

            // Always open in the environment the user last worked in, so testing against
            // "dev" doesn't require switching back from "local" on every endpoint.
            val env = envService.defaultEnvironment()

            val baseDraft = RequestDraftBuilder.buildDraft(endpoint, env)

            val bodyJson = try {
                if (endpoint.requestBodyClass != null) {
                    val bodyParam = SpringControllerParser.findRequestBodyParameter(psiMethod)
                    if (bodyParam != null) {
                        BodySampleGenerator.generateSampleFromPsiType(bodyParam.type, project)
                    } else {
                        BodySampleGenerator.generateSampleByTypeName(endpoint.requestBodyClass, project)
                    }
                } else ""
            } catch (e: Exception) {
                notify(project, "Body generation failed: ${e.message}")
                ""
            }

            val draft = if (existingDraft != null) {
                baseDraft.copy(body = existingDraft.body.ifEmpty { bodyJson })
            } else {
                baseDraft.copy(body = bodyJson)
            }

            val tabTitle = "${endpoint.httpMethod.name} ${endpoint.fullPath}".let {
                if (it.length > 38) it.take(35) + "…" else it
            }

            ApplicationManager.getApplication().invokeLater {
                val toolWindow = ToolWindowManager.getInstance(project)
                    .getToolWindow("Code2Request") ?: return@invokeLater

                toolWindow.show()

                val content = toolWindow.contentManager
                    .getContent(0)?.component as? RequestToolWindowContent
                    ?: return@invokeLater

                content.openSession(draft, tabTitle)
            }
        }
    }

    private fun notify(project: com.intellij.openapi.project.Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            try {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Code2Request")
                    .createNotification(message, NotificationType.INFORMATION)
                    .notify(project)
            } catch (_: Exception) {
                // Notification group not registered, use balloon fallback
                com.intellij.openapi.ui.Messages.showInfoMessage(project, message, "Code2Request")
            }
        }
    }
}
