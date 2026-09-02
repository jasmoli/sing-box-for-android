package io.xireiki.sfa.compose.graphical

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// One renderable node in the schema tree. Built by GraphicalSchemaLoader from the
// raw JSON Schema object and the current config value.
sealed class GraphicalSchemaNode {
    abstract val path: String
    abstract val title: String
    abstract val description: String?
    abstract val required: Boolean
    abstract val deprecated: Boolean
    open val propertyName: String? = null
    open val isAdvanced: Boolean = false

    data class StringField(
        override val path: String,
        override val propertyName: String? = null,
        override val title: String,
        override val description: String? = null,
        override val required: Boolean = false,
        override val deprecated: Boolean = false,
        override val isAdvanced: Boolean = false,
        val value: String,
        val enumValues: List<String> = emptyList(),
        val isSecret: Boolean = false,
        val isDuration: Boolean = false,
        val isPort: Boolean = false,
    ) : GraphicalSchemaNode()

    data class IntegerField(
        override val path: String,
        override val propertyName: String? = null,
        override val title: String,
        override val description: String? = null,
        override val required: Boolean = false,
        override val deprecated: Boolean = false,
        override val isAdvanced: Boolean = false,
        val value: Long?,
        val minimum: Long? = null,
        val maximum: Long? = null,
    ) : GraphicalSchemaNode()

    data class NumberField(
        override val path: String,
        override val propertyName: String? = null,
        override val title: String,
        override val description: String? = null,
        override val required: Boolean = false,
        override val deprecated: Boolean = false,
        override val isAdvanced: Boolean = false,
        val value: Double?,
    ) : GraphicalSchemaNode()

    data class BooleanField(
        override val path: String,
        override val propertyName: String? = null,
        override val title: String,
        override val description: String? = null,
        override val required: Boolean = false,
        override val deprecated: Boolean = false,
        override val isAdvanced: Boolean = false,
        val value: Boolean,
    ) : GraphicalSchemaNode()

    data class ObjectField(
        override val path: String,
        override val propertyName: String? = null,
        override val title: String,
        override val description: String? = null,
        override val required: Boolean = false,
        override val deprecated: Boolean = false,
        override val isAdvanced: Boolean = false,
        val present: Boolean,
        val children: List<GraphicalSchemaNode>,
    ) : GraphicalSchemaNode()

    data class ArrayField(
        override val path: String,
        override val propertyName: String? = null,
        override val title: String,
        override val description: String? = null,
        override val required: Boolean = false,
        override val deprecated: Boolean = false,
        override val isAdvanced: Boolean = false,
        val elementTitle: String,
        val elements: List<GraphicalSchemaNode>,
        val elementSchema: JsonObject,
        val isPrimitiveElement: Boolean,
    ) : GraphicalSchemaNode()

    data class DiscriminatedUnion(
        override val path: String,
        override val propertyName: String? = null,
        override val title: String,
        override val description: String? = null,
        override val required: Boolean = false,
        override val deprecated: Boolean = false,
        override val isAdvanced: Boolean = false,
        val discriminator: String,
        val options: List<String>,
        val currentType: String?,
        val currentChildren: List<GraphicalSchemaNode>,
    ) : GraphicalSchemaNode()
}

// Path supports both object keys and array indices, e.g.
// "outbounds[0].tag" or "tls.certificates[1]".
internal object JsonPath {
    private sealed class Segment {
        data class Key(val name: String) : Segment()
        data class Index(val position: Int) : Segment()
    }

    private fun parse(path: String): List<Segment> {
        if (path.isEmpty()) return emptyList()
        val result = mutableListOf<Segment>()
        val buf = StringBuilder()
        var i = 0
        while (i < path.length) {
            val c = path[i]
            when {
                c == '[' -> {
                    if (buf.isNotEmpty()) {
                        result += Segment.Key(buf.toString())
                        buf.clear()
                    }
                    val close = path.indexOf(']', i)
                    if (close < 0) return result
                    val num = path.substring(i + 1, close).toIntOrNull() ?: 0
                    result += Segment.Index(num)
                    i = close + 1
                }
                c == '.' -> {
                    if (buf.isNotEmpty()) {
                        result += Segment.Key(buf.toString())
                        buf.clear()
                    }
                    i++
                }
                else -> {
                    buf.append(c)
                    i++
                }
            }
        }
        if (buf.isNotEmpty()) result += Segment.Key(buf.toString())
        return result
    }

    fun get(root: JsonElement, path: String): JsonElement? {
        if (path.isEmpty()) return root
        var current: JsonElement? = root
        for (seg in parse(path)) {
            current = when (seg) {
                is Segment.Key -> (current as? JsonObject)?.get(seg.name)
                is Segment.Index -> (current as? JsonArray)?.getOrNull(seg.position)
            } ?: return null
        }
        return current
    }

    fun asObject(root: JsonElement, path: String): JsonObject? = get(root, path) as? JsonObject
    fun asArray(root: JsonElement, path: String): JsonArray? = get(root, path) as? JsonArray

    fun set(root: JsonElement, path: String, value: JsonElement?): JsonElement {
        if (path.isEmpty()) return value ?: root
        val segments = parse(path)
        if (segments.isEmpty()) return root
        return applySet(root, segments, 0, value)
    }

    private fun applySet(current: JsonElement?, segments: List<Segment>, idx: Int, value: JsonElement?): JsonElement {
        if (idx == segments.lastIndex) {
            val seg = segments[idx]
            return when (seg) {
                is Segment.Key -> {
                    val obj = LinkedHashMap<String, JsonElement>((current as? JsonObject) ?: JsonObject(emptyMap()))
                    if (value == null) obj.remove(seg.name) else obj[seg.name] = value
                    JsonObject(obj)
                }
                is Segment.Index -> {
                    val arr = (current as? JsonArray)?.toMutableList() ?: mutableListOf()
                    while (arr.size <= seg.position) arr.add(JsonObject(emptyMap()))
                    if (value == null) {
                        if (seg.position < arr.size) arr.removeAt(seg.position)
                    } else {
                        arr[seg.position] = value
                    }
                    JsonArray(arr)
                }
            }
        }
        val seg = segments[idx]
        val childCurrent = when (seg) {
            is Segment.Key -> (current as? JsonObject)?.get(seg.name)
            is Segment.Index -> (current as? JsonArray)?.getOrNull(seg.position)
        }
        val next = applySet(childCurrent, segments, idx + 1, value)
        return when (seg) {
            is Segment.Key -> {
                val obj = LinkedHashMap<String, JsonElement>((current as? JsonObject) ?: JsonObject(emptyMap()))
                obj[seg.name] = next
                JsonObject(obj)
            }
            is Segment.Index -> {
                val arr = (current as? JsonArray)?.toMutableList() ?: mutableListOf()
                while (arr.size <= seg.position) arr.add(JsonObject(emptyMap()))
                arr[seg.position] = next
                JsonArray(arr)
            }
        }
    }

    fun removeAt(root: JsonElement, arrayPath: String, index: Int): JsonElement {
        val arr = (get(root, arrayPath) as? JsonArray)?.toMutableList() ?: return root
        if (index < 0 || index >= arr.size) return root
        arr.removeAt(index)
        return set(root, arrayPath, JsonArray(arr))
    }

    fun appendAt(root: JsonElement, arrayPath: String, newElement: JsonElement): JsonElement {
        val arr = (get(root, arrayPath) as? JsonArray)?.toMutableList() ?: mutableListOf()
        arr.add(newElement)
        return set(root, arrayPath, JsonArray(arr))
    }

    fun asString(value: JsonElement?): String? = (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    fun asBoolean(value: JsonElement?): Boolean? = (value as? JsonPrimitive)?.booleanOrNull
    fun asLong(value: JsonElement?): Long? = (value as? JsonPrimitive)?.longOrNull
    fun asDouble(value: JsonElement?): Double? = (value as? JsonPrimitive)?.doubleOrNull
}
