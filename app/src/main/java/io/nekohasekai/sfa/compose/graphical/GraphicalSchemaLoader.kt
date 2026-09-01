package io.nekohasekai.sfa.compose.graphical

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Turns a raw JSON Schema (root + $defs) and a current config value tree into
// a flat list of renderable [GraphicalSchemaNode]s. Pure function, no Compose
// or Android dependency.
class GraphicalSchemaLoader(

    private val schemaRoot: JsonObject,
    private val labeler: SchemaLabeler = SchemaLabeler(),
) {
    private val definitions: JsonObject? = schemaRoot["\$defs"] as? JsonObject

    companion object {
        // Top-level keys considered "advanced" and hidden behind a "more options" toggle.
        val advancedTopLevelKeys: Set<String> = setOf(
            "certificate",
            "certificate_providers",
            "ntp",
            "services",
            "network_namespaces",
            "experimental",
            "endpoints",
        )
    }

    fun load(config: JsonObject, pathPrefix: String = ""): List<GraphicalSchemaNode> {
        val properties = schemaRoot["properties"] as? JsonObject ?: return emptyList()
        val requiredSet = (schemaRoot["required"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.toSet()
            ?: emptySet()
        // Skip built-in keys that the user should never edit (e.g. $schema).
        val skipKeys = setOf("\$schema")
        return properties.entries
            .filter { (key, _) -> key !in skipKeys }
            .sortedBy { it.key }
            .map { (key, schema) ->
                val value = config[key]
                val node = buildNode(schema, value, key, key, requiredSet.contains(key))
                if (key in advancedTopLevelKeys) markAdvanced(node) else node
            }
    }

    private fun buildNode(
        schemaNode: JsonElement?,
        configValue: JsonElement?,
        path: String,
        propertyName: String?,
        required: Boolean,
    ): GraphicalSchemaNode {
        val resolved = resolve(schemaNode)
        val deprecated = (resolved["deprecated"] as? JsonPrimitive)?.let { it.content == "true" } == true
        val description = (resolved["description"] as? JsonPrimitive)?.contentOrNull
        val title = labeler.titleFor(path, propertyName, resolved)

        // Unwrap oneOf/anyOf when only one branch is structurally relevant.
        val (main, variants) = splitVariants(resolved)

        // Discriminated union: every branch is an object with a const/enum on the
        // same discriminator field (e.g. type=direct, type=vmess, action=route).
        val discriminator = detectDiscriminator(variants)
        if (discriminator != null && main == null) {
            return buildDiscriminatedUnion(
                variants = variants,
                configValue = configValue,
                path = path,
                propertyName = propertyName,
                title = title,
                description = description,
                required = required,
                deprecated = deprecated,
                discriminator = discriminator,
            )
        }

        // Pick the variant that matches current config (e.g. port=443 selected tls).
        val effective = main ?: pickVariantForConfig(variants, configValue)

        return when (effective["type"]) {
            is JsonPrimitive -> buildScalar(effective, configValue, path, propertyName, title, description, required, deprecated)
            else -> buildObjectOrScalar(effective, configValue, path, propertyName, title, description, required, deprecated)
        }
    }

    private fun buildScalar(
        schemaNode: JsonObject,
        configValue: JsonElement?,
        path: String,
        propertyName: String?,
        title: String,
        description: String?,
        required: Boolean,
        deprecated: Boolean,
    ): GraphicalSchemaNode {
        val type = (schemaNode["type"] as? JsonPrimitive)?.contentOrNull
        val enumValues = (schemaNode["enum"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val format = (schemaNode["format"] as? JsonPrimitive)?.contentOrNull

        if (enumValues.isNotEmpty()) {
            // enum-typed field is rendered as a string with a dropdown.
            return GraphicalSchemaNode.StringField(
                path = path,
                propertyName = propertyName,
                title = title,
                description = description,
                required = required,
                deprecated = deprecated,
                value = JsonPath.asString(configValue) ?: enumValues.first(),
                enumValues = enumValues,
            )
        }
        return when (type) {
            "string" -> GraphicalSchemaNode.StringField(
                path = path,
                propertyName = propertyName,
                title = title,
                description = description,
                required = required,
                deprecated = deprecated,
                value = JsonPath.asString(configValue) ?: "",
                isSecret = isSecret(propertyName),
                isDuration = format == "duration",
            )
            "integer" -> {
                val min = (schemaNode["minimum"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                val max = (schemaNode["maximum"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                GraphicalSchemaNode.IntegerField(
                    path = path,
                    propertyName = propertyName,
                    title = title,
                    description = description,
                    required = required,
                    deprecated = deprecated,
                    value = JsonPath.asLong(configValue),
                    minimum = min,
                    maximum = max,
                )
            }
            "number" -> GraphicalSchemaNode.NumberField(
                path = path,
                propertyName = propertyName,
                title = title,
                required = required,
                deprecated = deprecated,
                value = JsonPath.asDouble(configValue),
            )
            "boolean" -> GraphicalSchemaNode.BooleanField(
                path = path,
                propertyName = propertyName,
                title = title,
                description = description,
                required = required,
                deprecated = deprecated,
                value = JsonPath.asBoolean(configValue) ?: false,
            )
            "array" -> buildArray(schemaNode, configValue, path, propertyName, title, description, required, deprecated)
            "object" -> buildObject(schemaNode, configValue, path, propertyName, title, description, required, deprecated, mainProperties = null)
            "null" -> GraphicalSchemaNode.BooleanField(
                path = path,
                propertyName = propertyName,
                title = title,
                description = description,
                required = required,
                deprecated = deprecated,
                value = false,
            )
            else -> buildObject(schemaNode, configValue, path, propertyName, title, description, required, deprecated, mainProperties = null)
        }
    }

    private fun buildObjectOrScalar(
        schemaNode: JsonObject,
        configValue: JsonElement?,
        path: String,
        propertyName: String?,
        title: String,
        description: String?,
        required: Boolean,
        deprecated: Boolean,
    ): GraphicalSchemaNode = when (val type = schemaNode["type"]) {
        is JsonPrimitive -> buildScalar(schemaNode, configValue, path, propertyName, title, description, required, deprecated)
        is JsonArray -> {
            val isArray = type.any { (it as? JsonPrimitive)?.contentOrNull == "array" }
            val isObject = type.any { (it as? JsonPrimitive)?.contentOrNull == "object" }
            when {
                isArray -> buildArray(schemaNode, configValue, path, propertyName, title, description, required, deprecated)
                isObject -> buildObject(schemaNode, configValue, path, propertyName, title, description, required, deprecated, mainProperties = null)
                else -> buildScalar(schemaNode, configValue, path, propertyName, title, description, required, deprecated)
            }
        }
        else -> buildObject(schemaNode, configValue, path, propertyName, title, description, required, deprecated, mainProperties = null)
    }

    private fun buildObject(
        schemaNode: JsonObject,
        configValue: JsonElement?,
        path: String,
        propertyName: String?,
        title: String,
        description: String?,
        required: Boolean,
        deprecated: Boolean,
        mainProperties: Set<String>?,
    ): GraphicalSchemaNode {
        val configObj = configValue as? JsonObject
        val properties = schemaNode["properties"] as? JsonObject ?: JsonObject(emptyMap())
        val requiredFields = (schemaNode["required"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.toSet() ?: emptySet()
        val children = mutableListOf<GraphicalSchemaNode>()
        for ((name, childSchema) in properties) {
            if (mainProperties != null && name in mainProperties) continue
            val childPath = if (path.isEmpty()) name else "$path.$name"
            val childValue = configObj?.get(name)
            children += buildNode(
                schemaNode = childSchema,
                configValue = childValue,
                path = childPath,
                propertyName = name,
                required = name in requiredFields,
            )
        }
        return GraphicalSchemaNode.ObjectField(
            path = path,
            propertyName = propertyName,
            title = title,
            description = description,
            required = required,
            deprecated = deprecated,
            present = configObj != null,
            children = children,
        )
    }

    private fun buildArray(
        schemaNode: JsonObject,
        configValue: JsonElement?,
        path: String,
        propertyName: String?,
        title: String,
        description: String?,
        required: Boolean,
        deprecated: Boolean,
    ): GraphicalSchemaNode {
        val configArr = configValue as? JsonArray
        val itemSchema = schemaNode["items"] as? JsonObject ?: JsonObject(emptyMap())
        val isPrimitiveElement = itemSchema["type"] is JsonPrimitive
        val children = mutableListOf<GraphicalSchemaNode>()
        configArr?.forEachIndexed { index, element ->
            val itemPath = if (path.isEmpty()) "[$index]" else "$path[$index]"
            children += buildNode(
                schemaNode = itemSchema,
                configValue = element,
                path = itemPath,
                propertyName = null,
                required = false,
            )
        }
        return GraphicalSchemaNode.ArrayField(
            path = path,
            propertyName = propertyName,
            title = title,
            description = description,
            required = required,
            deprecated = deprecated,
            elementTitle = "Item",
            elements = children,
            elementSchema = itemSchema,
            isPrimitiveElement = isPrimitiveElement,
        )
    }

    private fun buildDiscriminatedUnion(
        variants: List<JsonObject>,
        configValue: JsonElement?,
        path: String,
        propertyName: String?,
        title: String,
        description: String?,
        required: Boolean,
        deprecated: Boolean,
        discriminator: String,
    ): GraphicalSchemaNode {
        val options = variants.map { variant ->
            val propertySchema = (variant["properties"] as? JsonObject)?.get(discriminator) as? JsonObject
            val resolvedProperty = resolve(propertySchema)
            (resolvedProperty["const"] as? JsonPrimitive)?.contentOrNull
                ?: (resolvedProperty["enum"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull }
                ?: "?"
        }
        val currentType = (configValue as? JsonObject)?.get(discriminator)
            ?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: options.firstOrNull()
        val matchedVariant = variants.firstOrNull { variant ->
            val propertySchema = (variant["properties"] as? JsonObject)?.get(discriminator) as? JsonObject
            val resolvedProperty = resolve(propertySchema)
            (resolvedProperty["const"] as? JsonPrimitive)?.contentOrNull == currentType
        } ?: variants.first()
        val childSchema = matchedVariant["properties"] as? JsonObject ?: JsonObject(emptyMap())
        val children = mutableListOf<GraphicalSchemaNode>()
        for ((name, childS) in childSchema) {
            if (name == discriminator) continue
            val childPath = if (path.isEmpty()) name else "$path.$name"
            val childValue = (configValue as? JsonObject)?.get(name)
            children += buildNode(
                schemaNode = childS,
                configValue = childValue,
                path = childPath,
                propertyName = name,
                required = false,
            )
        }
        return GraphicalSchemaNode.DiscriminatedUnion(
            path = path,
            propertyName = propertyName,
            title = title,
            description = description,
            required = required,
            deprecated = deprecated,
            discriminator = discriminator,
            options = options,
            currentType = currentType,
            currentChildren = children,
        )
    }

    // --- schema resolution helpers -----------------------------------------

    private fun markAdvanced(node: GraphicalSchemaNode): GraphicalSchemaNode = when (node) {
        is GraphicalSchemaNode.StringField -> node.copy(isAdvanced = true)
        is GraphicalSchemaNode.IntegerField -> node.copy(isAdvanced = true)
        is GraphicalSchemaNode.NumberField -> node.copy(isAdvanced = true)
        is GraphicalSchemaNode.BooleanField -> node.copy(isAdvanced = true)
        is GraphicalSchemaNode.ObjectField -> node.copy(isAdvanced = true)
        is GraphicalSchemaNode.ArrayField -> node.copy(isAdvanced = true)
        is GraphicalSchemaNode.DiscriminatedUnion -> node.copy(isAdvanced = true)
    }

    private fun resolve(node: JsonElement?): JsonObject {
        var current: JsonElement? = node
        var depth = 0
        while (depth < 8) {
            val obj = current as? JsonObject ?: return JsonObject(emptyMap())
            val ref = (obj["\$ref"] as? JsonPrimitive)?.contentOrNull ?: return obj
            current = definitions?.get(ref.substringAfterLast('/'))
            depth++
        }
        return JsonObject(emptyMap())
    }

    private data class SplitVariants(val main: JsonObject?, val variants: List<JsonObject>)

    private fun splitVariants(node: JsonObject): SplitVariants {
        val variants = (node["oneOf"] ?: node["anyOf"]) as? JsonArray
        val filtered = variants?.filterIsInstance<JsonObject>().orEmpty()
        if (filtered.isEmpty()) return SplitVariants(node, emptyList())
        val hasMainProperties = node["properties"] is JsonObject
        return if (hasMainProperties) {
            SplitVariants(node, filtered)
        } else {
            SplitVariants(null, flattenLeaves(filtered, 0))
        }
    }

    private fun flattenLeaves(variants: List<JsonObject>, depth: Int): List<JsonObject> {
        if (depth > 4) return variants
        val result = mutableListOf<JsonObject>()
        for (v in variants) {
            val resolved = resolve(v)
            val nested = (resolved["oneOf"] ?: resolved["anyOf"]) as? JsonArray
            if (nested != null && resolved["properties"] !is JsonObject) {
                result += flattenLeaves(nested.filterIsInstance<JsonObject>(), depth + 1)
            } else {
                result += resolved
            }
        }
        return result
    }

    private fun detectDiscriminator(variants: List<JsonObject>): String? {
        if (variants.size < 2) return null
        val candidates = listOf("type", "action", "provider", "mode", "version")
        for (name in candidates) {
            if (variants.all { v ->
                    val resolved = resolve(v)
                    val prop = (resolved["properties"] as? JsonObject)?.get(name) as? JsonObject
                    val r = resolve(prop)
                    r["const"] is JsonPrimitive || r["enum"] is JsonArray
                }
            ) {
                return name
            }
        }
        return null
    }

    private fun pickVariantForConfig(variants: List<JsonObject>, configValue: JsonElement?): JsonObject {
        if (configValue !is JsonObject) return variants.first()
        for (v in variants) {
            val props = (v["properties"] as? JsonObject) ?: continue
            var matches = true
            for ((name, schema) in props) {
                val resolved = resolve(schema)
                val const = (resolved["const"] as? JsonPrimitive)?.contentOrNull
                if (const != null && (configValue[name] as? JsonPrimitive)?.contentOrNull != const) {
                    matches = false
                    break
                }
                val enumValues = (resolved["enum"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                if (!enumValues.isNullOrEmpty()) {
                    val cv = (configValue[name] as? JsonPrimitive)?.contentOrNull
                    if (cv != null && cv !in enumValues) {
                        matches = false
                        break
                    }
                }
            }
            if (matches) return v
        }
        return variants.first()
    }

    private fun isSecret(propertyName: String?): Boolean {
        if (propertyName == null) return false
        val n = propertyName.lowercase()
        return n.contains("password") || n.contains("secret") ||
            n.contains("token") || n == "private_key" || n.contains("private_key") ||
            n.contains("apikey") || n.contains("api_key")
    }
}
