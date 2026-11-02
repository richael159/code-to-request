package com.code2request.storage

import com.code2request.model.DEFAULT_ENVIRONMENTS
import com.code2request.model.EnvironmentConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@State(
    name = "Code2RequestEnvironments",
    storages = [Storage(".idea/code2request-environments.xml")]
)
@Service(Service.Level.PROJECT)
class EnvironmentConfigService : PersistentStateComponent<EnvironmentConfigService.State> {

    data class State(
        var environmentsJson: String = "",
        /** Environment the user last switched to or sent a request with; default for newly opened requests. */
        var lastEnvironment: String = "",
    )

    private var state = State()
    private val gson = Gson()

    override fun getState(): State = state
    override fun loadState(s: State) { state = s }

    fun getEnvironments(): List<EnvironmentConfig> {
        if (state.environmentsJson.isBlank()) return DEFAULT_ENVIRONMENTS
        return try {
            val type = object : TypeToken<List<EnvironmentConfig>>() {}.type
            gson.fromJson(state.environmentsJson, type) ?: DEFAULT_ENVIRONMENTS
        } catch (_: Exception) {
            DEFAULT_ENVIRONMENTS
        }
    }

    fun saveEnvironments(envs: List<EnvironmentConfig>) {
        state.environmentsJson = gson.toJson(envs)
    }

    fun getEnvironment(name: String): EnvironmentConfig? = getEnvironments().find { it.name == name }

    fun rememberEnvironment(name: String) {
        state.lastEnvironment = name
    }

    /** Last used environment if it still exists, otherwise the first one in the list. */
    fun defaultEnvironment(): EnvironmentConfig =
        getEnvironment(state.lastEnvironment)
            ?: getEnvironments().firstOrNull()
            ?: EnvironmentConfig("local", "http://localhost:8080")

    fun upsertEnvironment(config: EnvironmentConfig) {
        val envs = getEnvironments().toMutableList()
        val idx = envs.indexOfFirst { it.name == config.name }
        if (idx >= 0) envs[idx] = config else envs.add(config)
        saveEnvironments(envs)
    }

    companion object {
        fun getInstance(project: Project): EnvironmentConfigService =
            project.getService(EnvironmentConfigService::class.java)
    }
}
