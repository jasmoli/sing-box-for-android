package io.nekohasekai.sfa.compose.graphical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R

@Composable
private fun CollapsibleHeader(
    title: String,
    description: String?,
    deprecated: Boolean,
    required: Boolean,
    initiallyExpanded: Boolean = false,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { androidx.compose.runtime.mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (expanded) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            },
        ),
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (deprecated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        textDecoration = if (deprecated) TextDecoration.LineThrough else null,
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (required) {
                    Text(
                        text = " *",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                trailingAction?.invoke()
                Icon(
                    imageVector = if (expanded) androidx.compose.material.icons.Icons.Default.ExpandLess else androidx.compose.material.icons.Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldRenderer(
    node: GraphicalSchemaNode,
    onValueChange: (path: String, value: Any?) -> Unit,
    onAddArray: (path: String) -> Unit = {},
    onRemoveArray: (path: String, index: Int) -> Unit = { _, _ -> },
    onSelectType: (path: String, discriminator: String, newType: String) -> Unit = { _, _, _ -> },
    onRemoveItem: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        when (node) {
            is GraphicalSchemaNode.StringField -> StringControl(node, onValueChange)
            is GraphicalSchemaNode.IntegerField -> IntegerControl(node, onValueChange)
            is GraphicalSchemaNode.NumberField -> NumberControl(node, onValueChange)
            is GraphicalSchemaNode.BooleanField -> BooleanControl(node, onValueChange)
            is GraphicalSchemaNode.ObjectField -> ObjectControl(node, onAddArray, onRemoveArray, onValueChange, onSelectType, onRemoveItem)
            is GraphicalSchemaNode.ArrayField -> ArrayControl(node, onAddArray, onRemoveArray, onValueChange, onSelectType)
            is GraphicalSchemaNode.DiscriminatedUnion -> DiscriminatedUnionControl(node, onAddArray, onRemoveArray, onValueChange, onSelectType, onRemoveItem)
        }
        node.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
        if (node.deprecated) {
            Text(
                text = stringResource(R.string.graphical_deprecated_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun FieldTitle(node: GraphicalSchemaNode) {
    val label = node.propertyName?.let { resolveLabel(it, node.title) } ?: node.title

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = if (node.deprecated) TextDecoration.LineThrough else null,
        )
        if (node.required) {
            Text(
                text = " *",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StringControl(node: GraphicalSchemaNode.StringField, onValueChange: (String, Any?) -> Unit) {
    if (node.enumValues.isNotEmpty()) {
        EnumDropdown(
            label = node.title,
            value = node.value,
            options = node.enumValues,
            required = node.required,
            onSelect = { onValueChange(node.path, it) },
        )
        return
    }
    OutlinedTextField(
        value = node.value,
        onValueChange = { onValueChange(node.path, it) },
        label = { FieldTitle(node) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (node.isSecret) PasswordVisualTransformation() else VisualTransformation.None,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntegerControl(node: GraphicalSchemaNode.IntegerField, onValueChange: (String, Any?) -> Unit) {
    val text = node.value?.toString() ?: ""
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val parsed = raw.toLongOrNull()
            onValueChange(node.path, parsed)
        },
        label = { FieldTitle(node) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberControl(node: GraphicalSchemaNode.NumberField, onValueChange: (String, Any?) -> Unit) {
    val text = node.value?.toString() ?: ""
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            onValueChange(node.path, raw.toDoubleOrNull())
        },
        label = { FieldTitle(node) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun BooleanControl(node: GraphicalSchemaNode.BooleanField, onValueChange: (String, Any?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FieldTitle(node)
        Switch(
            checked = node.value,
            onCheckedChange = { onValueChange(node.path, it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(
    label: String,
    value: String,
    options: List<String>,
    required: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label)
                    if (required) {
                        Text(
                            text = " *",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ObjectControl(
    node: GraphicalSchemaNode.ObjectField,
    onAddArray: (String) -> Unit,
    onRemoveArray: (String, Int) -> Unit,
    onValueChange: (String, Any?) -> Unit,
    onSelectType: (String, String, String) -> Unit,
    onRemoveItem: (() -> Unit)? = null,
) {
    val label = node.propertyName?.let { resolveLabel(it, node.title) } ?: node.title
    CollapsibleHeader(
        title = label,
        description = node.description,
        deprecated = node.deprecated,
        required = node.required,
        trailingAction = onRemoveItem?.let { rm ->
            { IconButton(onClick = rm) { Icon(Icons.Default.Close, stringResource(R.string.graphical_array_remove), tint = MaterialTheme.colorScheme.error) } }
        },
    ) {
        Column {
            for (child in node.children) {
                FieldRenderer(
                    node = child,
                    onValueChange = onValueChange,
                    onAddArray = onAddArray,
                    onRemoveArray = onRemoveArray,
                    onSelectType = onSelectType,
                )
            }
        }
    }
}

@Composable
private fun resolveLabel(propertyName: String, fallback: String): String {
    val ctx = LocalContext.current
    val resId = ctx.resources.getIdentifier("graphical_field_$propertyName", "string", ctx.packageName)
    return if (resId != 0) stringResource(resId) else fallback
}

@Composable
private fun ArrayControl(
    node: GraphicalSchemaNode.ArrayField,
    onAdd: (path: String) -> Unit,
    onRemove: (path: String, index: Int) -> Unit,
    onValueChange: (String, Any?) -> Unit,
    onSelectType: (String, String, String) -> Unit,
) {
    val label = node.propertyName?.let { resolveLabel(it, node.title) } ?: node.title
    CollapsibleHeader(
        title = label,
        description = node.description,
        deprecated = node.deprecated,
        required = node.required,
    ) {
        Column {
            // Add button always visible (even when array is empty)
            TextButton(
                onClick = { onAdd(node.path) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(stringResource(R.string.graphical_array_add))
            }
            if (node.elements.isEmpty()) {
                Text(
                    text = stringResource(R.string.graphical_array_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    for ((index, child) in node.elements.withIndex()) {
                        // The delete button lives in the element's own header row (trailingAction),
                        // so it never overlaps the collapse chevron.
                        FieldRenderer(
                            node = child,
                            onValueChange = onValueChange,
                            onAddArray = onAdd,
                            onRemoveArray = onRemove,
                            onSelectType = onSelectType,
                            onRemoveItem = { onRemove(node.path, index) },
                        )
                        if (index < node.elements.size - 1) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscriminatedUnionControl(
    node: GraphicalSchemaNode.DiscriminatedUnion,
    onAddArray: (String) -> Unit,
    onRemoveArray: (String, Int) -> Unit,
    onValueChange: (String, Any?) -> Unit,
    onSelectType: (path: String, discriminator: String, newType: String) -> Unit,
    onRemoveItem: (() -> Unit)? = null,
) {
    val label = node.propertyName?.let { resolveLabel(it, node.title) } ?: node.title
    CollapsibleHeader(
        title = label,
        description = node.description,
        deprecated = node.deprecated,
        required = node.required,
        trailingAction = onRemoveItem?.let { rm ->
            { IconButton(onClick = rm) { Icon(Icons.Default.Close, stringResource(R.string.graphical_array_remove), tint = MaterialTheme.colorScheme.error) } }
        },
    ) {
        Column {
            EnumDropdown(
                label = node.discriminator,
                value = node.currentType ?: node.options.first(),
                options = node.options,
                required = node.required,
                onSelect = { selected ->
                    onSelectType(node.path, node.discriminator, selected)
                },
            )
            for (child in node.currentChildren) {
                FieldRenderer(
                    node = child,
                    onValueChange = onValueChange,
                    onAddArray = onAddArray,
                    onRemoveArray = onRemoveArray,
                    onSelectType = onSelectType,
                )
            }
        }
    }
}
