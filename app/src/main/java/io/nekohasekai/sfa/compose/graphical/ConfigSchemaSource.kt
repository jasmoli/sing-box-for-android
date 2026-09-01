package io.nekohasekai.sfa.compose.graphical

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.ktx.unwrap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// Schema access for the graphical editor. Lives in the main source set so every
// flavor can use it (the code editor's ConfigSchema is minApi24 only).
object ConfigSchemaSource {
    private val lock = Any()

    @Volatile
    private var loaded = false
    private var root: JsonObject? = null
    private var failure: String? = null

    // Parser matching sing-box behaviour: configs may contain comments and
    // trailing commas, a strict parser would reject real user configs.
    val lenient: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowComments = true
        allowTrailingComma = true
    }

    fun load(): JsonObject? {
        if (!loaded) {
            synchronized(lock) {
                if (!loaded) {
                    runCatching {
                        lenient.parseToJsonElement(Libbox.generateConfigSchema().unwrap).jsonObject
                    }.onSuccess { root = it }
                        .onFailure { failure = it.message ?: it.javaClass.simpleName }
                    loaded = true
                }
            }
        }
        return root
    }

    // Reason the schema could not be loaded, for display in the UI.
    fun failureMessage(): String? = failure
}
