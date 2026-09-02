package io.xireiki.sfa.compose.screen.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.xireiki.sfa.R
import io.xireiki.sfa.compose.graphical.FieldRenderer
import io.xireiki.sfa.compose.graphical.GraphicalSchemaNode
import io.xireiki.sfa.compose.topbar.LocalScaffoldPadding
import io.xireiki.sfa.compose.topbar.OverrideTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileGraphicalScreen(
    profileId: Long,
    onNavigateBack: () -> Unit,
    viewModel: EditProfileGraphicalViewModel = viewModel(
        factory = EditProfileGraphicalViewModel.Factory(profileId),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showUnsavedDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadConfiguration() }

    LaunchedEffect(uiState.showSaveSuccessMessage) {
        if (uiState.showSaveSuccessMessage) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.success_configuration_saved),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            viewModel.clearSaveSuccessMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    val handleBack = {
        if (uiState.hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onNavigateBack()
        }
    }
    BackHandler(enabled = uiState.hasUnsavedChanges) { showUnsavedDialog = true }

    // Register top bar with the global controller (same as the JSON editor),
    // so it renders in the correct location without duplicating the app bar.
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.graphical_editor)) },
            navigationIcon = {
                IconButton(onClick = handleBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_description_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
    }

    val scaffoldPadding = LocalScaffoldPadding.current

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val blockingError = uiState.blockingError
        if (blockingError != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.graphical_schema_missing),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = blockingError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (!uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(scaffoldPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                uiState.configurationError?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                val normalNodes = uiState.nodes.filterNot { it.isAdvanced }
                val advancedNodes = uiState.nodes.filter { it.isAdvanced }

                for (node in normalNodes) {
                    FieldRenderer(
                        node = node,
                        onValueChange = viewModel::onValueChange,
                        onAddArray = viewModel::onAddArray,
                        onRemoveArray = viewModel::onRemoveArray,
                        onSelectType = viewModel::onSelectType,
                    )
                }

                if (advancedNodes.isNotEmpty()) {
                    AdvancedOptionsCard(
                        nodes = advancedNodes,
                        onValueChange = viewModel::onValueChange,
                        onAddArray = viewModel::onAddArray,
                        onRemoveArray = viewModel::onRemoveArray,
                        onSelectType = viewModel::onSelectType,
                    )
                }

                Spacer(Modifier.height(80.dp))
            }

            AnimatedVisibility(
                visible = uiState.hasUnsavedChanges,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .padding(bottom = scaffoldPadding.calculateBottomPadding()),
                    ) {
                        Button(
                            onClick = { viewModel.saveConfiguration() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading,
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.save))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedOptionsCard(
    nodes: List<GraphicalSchemaNode>,
    onValueChange: (String, Any?) -> Unit,
    onAddArray: (String) -> Unit,
    onRemoveArray: (String, Int) -> Unit,
    onSelectType: (String, String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (expanded) 0.3f else 0.15f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.graphical_more_options),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.graphical_more_options_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (node in nodes) {
                    FieldRenderer(
                        node = node,
                        onValueChange = onValueChange,
                        onAddArray = onAddArray,
                        onRemoveArray = onRemoveArray,
                        onSelectType = onSelectType,
                    )
                }
            }
        }
    }
}
