package io.nekohasekai.sfa.compose.graphical

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Turns a schema field path + raw JSON Schema node into a human-readable label.
// e.g. "inbounds[0].tls_settings" → "Tls Settings"
class SchemaLabeler {
    fun titleFor(path: String, propertyName: String?, schema: JsonObject): String {
        // Prefer explicit title field.
        (schema["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        // Fall back to property name, CamelCase → Title Case.
        val raw = propertyName ?: path.substringAfterLast('.')
        if (raw.isEmpty() || raw.startsWith('[')) return path
        return toTitleCase(raw)
    }

    private fun toTitleCase(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder()
        var prevLower = false
        for (c in input) {
            when {
                c == '_' || c == '-' -> {
                    sb.append(' ')
                    prevLower = false
                }
                c.isUpperCase() && prevLower -> {
                    sb.append(' ')
                    sb.append(c)
                    prevLower = false
                }
                else -> {
                    sb.append(if (sb.isEmpty()) c.uppercaseChar() else c.lowercaseChar())
                    prevLower = c.isLowerCase()
                }
            }
        }
        return sb.toString()
    }
}
