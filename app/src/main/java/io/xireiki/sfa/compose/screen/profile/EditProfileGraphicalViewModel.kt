package io.xireiki.sfa.compose.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.xireiki.sfa.compose.graphical.ConfigSchemaSource
import io.xireiki.sfa.compose.graphical.GraphicalSchemaLoader
import io.xireiki.sfa.compose.graphical.GraphicalSchemaNode
import io.xireiki.sfa.compose.graphical.JsonPath
import io.xireiki.sfa.database.Profile
import io.xireiki.sfa.database.ProfileManager
import io.xireiki.sfa.ktx.unwrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

data class EditProfileGraphicalUiState(
    val isLoading: Boolean = true,
    val profileName: String = "",
    val nodes: List<GraphicalSchemaNode> = emptyList(),
    val errorMessage: String? = null,
    val configurationError: String? = null,
    val showSaveSuccessMessage: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val originalJson: String = "",
    // Non-null when the form cannot be shown at all (schema or config unusable).
    val blockingError: String? = null,
)

class EditProfileGraphicalViewModel(private val profileId: Long) : ViewModel() {
    private val _uiState = MutableStateFlow(EditProfileGraphicalUiState())
    val uiState: StateFlow<EditProfileGraphicalUiState> = _uiState.asStateFlow()

    private val prettyJson = Json { prettyPrint = true }

    private var profile: Profile? = null
    private var workingRoot: JsonElement = JsonObject(emptyMap())
    private var schemaRoot: JsonObject? = null
    private var configCheckJob: Job? = null

    fun loadConfiguration() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, blockingError = null) }
            try {
                val loadedProfile = ProfileManager.get(profileId)
                    ?: error("Profile not found")
                profile = loadedProfile

                val content = File(loadedProfile.typed.path).readText()
                // sing-box accepts JSONC (comments, trailing commas); a strict
                // parser would silently blank out real configs.
                val parsed = runCatching {
                    ConfigSchemaSource.lenient.parseToJsonElement(content.ifBlank { "{}" })
                }
                val configElement = parsed.getOrNull()
                if (configElement !is JsonObject) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            profileName = loadedProfile.name,
                            blockingError = parsed.exceptionOrNull()?.message
                                ?: "Configuration is not a JSON object",
                        )
                    }
                    return@launch
                }
                workingRoot = configElement

                val schema = ConfigSchemaSource.load()
                val properties = schema?.get("properties") as? JsonObject
                if (schema == null || properties == null || properties.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            profileName = loadedProfile.name,
                            blockingError = ConfigSchemaSource.failureMessage()
                                ?: "Configuration schema unavailable",
                        )
                    }
                    return@launch
                }
                schemaRoot = schema

                val nodes = GraphicalSchemaLoader(schema).load(configElement)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        profileName = loadedProfile.name,
                        nodes = nodes,
                        originalJson = content,
                        hasUnsavedChanges = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        blockingError = e.message ?: "Failed to load configuration",
                    )
                }
            }
        }
    }

    fun onValueChange(path: String, value: Any?) {
        workingRoot = JsonPath.set(workingRoot, path, toJsonElement(value))
        publishEdit()
    }

    fun onAddArray(path: String) {
        val node = findNode(path) as? GraphicalSchemaNode.ArrayField
        val newElement: JsonElement = if (node?.isPrimitiveElement == true) {
            JsonPrimitive("")
        } else {
            JsonObject(emptyMap())
        }
        workingRoot = JsonPath.appendAt(workingRoot, path, newElement)
        publishEdit()
    }

    fun onRemoveArray(path: String, index: Int) {
        workingRoot = JsonPath.removeAt(workingRoot, path, index)
        publishEdit()
    }

    fun onSelectType(path: String, discriminator: String, newType: String) {
        // Switching the discriminator invalidates every sibling field of the old
        // variant, so keep only the discriminator itself.
        workingRoot = JsonPath.set(
            workingRoot,
            path,
            JsonObject(mapOf(discriminator to JsonPrimitive(newType))),
        )
        publishEdit()
    }

    private fun publishEdit() {
        val nodes = rebuildNodes()
        val current = prettyJson.encodeToString(JsonElement.serializer(), workingRoot)
        _uiState.update {
            it.copy(nodes = nodes, hasUnsavedChanges = current != it.originalJson)
        }
        scheduleConfigurationCheck()
    }

    private fun rebuildNodes(): List<GraphicalSchemaNode> {
        val schema = schemaRoot ?: return _uiState.value.nodes
        val config = workingRoot as? JsonObject ?: return _uiState.value.nodes
        return GraphicalSchemaLoader(schema).load(config)
    }

    fun saveConfiguration() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val encoded = prettyJson.encodeToString(JsonElement.serializer(), workingRoot)
                val formatted = runCatching { Libbox.formatConfig(encoded).unwrap }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: encoded
                Libbox.checkConfig(formatted)
                profile?.let { p -> File(p.typed.path).writeText(formatted) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        originalJson = formatted,
                        hasUnsavedChanges = false,
                        showSaveSuccessMessage = true,
                        configurationError = null,
                    )
                }
                delay(2000)
                _uiState.update { it.copy(showSaveSuccessMessage = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Save failed")
                }
            }
        }
    }

    fun dismissConfigurationError() {
        _uiState.update { it.copy(configurationError = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSaveSuccessMessage() {
        _uiState.update { it.copy(showSaveSuccessMessage = false) }
    }

    private fun scheduleConfigurationCheck() {
        configCheckJob?.cancel()
        configCheckJob = viewModelScope.launch {
            delay(2000)
            val text = prettyJson.encodeToString(JsonElement.serializer(), workingRoot)
            withContext(Dispatchers.IO) {
                try {
                    Libbox.checkConfig(text)
                    _uiState.update { it.copy(configurationError = null) }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(configurationError = e.message ?: "Invalid configuration")
                    }
                }
            }
        }
    }

    private fun findNode(path: String): GraphicalSchemaNode? {
        fun search(nodes: List<GraphicalSchemaNode>): GraphicalSchemaNode? {
            for (n in nodes) {
                if (n.path == path) return n
                val children = when (n) {
                    is GraphicalSchemaNode.ObjectField -> n.children
                    is GraphicalSchemaNode.ArrayField -> n.elements
                    is GraphicalSchemaNode.DiscriminatedUnion -> n.currentChildren
                    else -> emptyList()
                }
                search(children)?.let { return it }
            }
            return null
        }
        return search(_uiState.value.nodes)
    }

    private fun toJsonElement(value: Any?): JsonElement? = when (value) {
        null -> null
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value.toLong())
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value.toDouble())
        is Map<*, *> -> {
            val map = LinkedHashMap<String, JsonElement>()
            for ((k, v) in value) {
                val key = k as? String ?: continue
                toJsonElement(v)?.let { map[key] = it }
            }
            JsonObject(map)
        }
        else -> null
    }

    class Factory(private val profileId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EditProfileGraphicalViewModel::class.java)) {
                return EditProfileGraphicalViewModel(profileId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
