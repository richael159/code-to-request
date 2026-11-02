package com.code2request.storage

import com.code2request.model.RequestDraft
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@State(
    name = "Code2RequestDrafts",
    storages = [Storage(".idea/code2request-drafts.xml")]
)
@Service(Service.Level.PROJECT)
class DraftStorageService : PersistentStateComponent<DraftStorageService.State> {

    data class State(var draftsJson: String = "{}")

    private var state = State()
    private val gson = Gson()

    override fun getState(): State = state
    override fun loadState(s: State) { state = s }

    private fun readAll(): MutableMap<String, RequestDraft> = try {
        val type = object : TypeToken<MutableMap<String, RequestDraft>>() {}.type
        gson.fromJson<MutableMap<String, RequestDraft>>(state.draftsJson, type) ?: mutableMapOf()
    } catch (_: Exception) {
        mutableMapOf()
    }

    private fun writeAll(drafts: Map<String, RequestDraft>) {
        state.draftsJson = gson.toJson(drafts)
    }

    fun saveDraft(draft: RequestDraft) {
        val drafts = readAll()
        drafts[draft.endpointId] = draft.copy(updatedAt = System.currentTimeMillis())
        writeAll(drafts)
    }

    fun getDraft(endpointId: String): RequestDraft? = readAll()[endpointId]

    fun getAllDrafts(): List<RequestDraft> = readAll().values.sortedByDescending { it.updatedAt }

    fun deleteDraft(endpointId: String) {
        val drafts = readAll()
        drafts.remove(endpointId)
        writeAll(drafts)
    }

    companion object {
        fun getInstance(project: Project): DraftStorageService =
            project.getService(DraftStorageService::class.java)
    }
}
