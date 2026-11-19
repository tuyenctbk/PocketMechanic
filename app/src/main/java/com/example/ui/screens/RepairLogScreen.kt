package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.DiagnosisHistoryEntity
import com.example.model.DiagnosticReport
import com.example.model.DiagnosticResult
import com.example.model.DiagnosticSeverity
import com.example.model.SafetyWarningLevel
import com.example.ui.MechanicViewModel
import com.example.ui.StreamUiState
import com.example.ui.components.DiagnosticResultCard
import com.example.ui.components.workshopFocusRing
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.StatusCriticalRed
import com.example.ui.theme.StatusOkGreen
import com.example.ui.theme.StatusWarnOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogFilter(val label: String) {
    ALL("All"),
    OPEN("Open"),
    RESOLVED("Fixed")
}

private enum class LogSourceTab(val label: String) {
    DEVICE_HISTORY("Device History"),
    CLOUD_LOG("Cloud Log")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairLogScreen(
    viewModel: MechanicViewModel,
    modifier: Modifier = Modifier,
    onShareReport: ((String) -> Unit)? = null
) {
    val diagnosticsState by viewModel.diagnosticsState.collectAsStateWithLifecycle()
    val localHistory by viewModel.localHistoryState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.historySearchQuery.collectAsStateWithLifecycle()

    var sourceTab by rememberSaveable { mutableStateOf(LogSourceTab.DEVICE_HISTORY) }
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var selectedCloudReport by remember { mutableStateOf<DiagnosticReport?>(null) }
    var selectedLocalEntry by remember { mutableStateOf<DiagnosisHistoryEntity?>(null) }
    var pendingDeleteLocal by remember { mutableStateOf<DiagnosisHistoryEntity?>(null) }
    var pendingDeleteCloud by remember { mutableStateOf<DiagnosticReport?>(null) }

    val cloudReports = (diagnosticsState as? StreamUiState.Success<List<DiagnosticReport>>)?.data.orEmpty()
    val summaryTotal = if (sourceTab == LogSourceTab.DEVICE_HISTORY) localHistory.size else cloudReports.size
    val summaryResolved = if (sourceTab == LogSourceTab.DEVICE_HISTORY) {
        localHistory.count { it.isResolved }
    } else {
        cloudReports.count { it.resolved }
    }
    val summaryOpen = (summaryTotal - summaryResolved).coerceAtLeast(0)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LogSourceTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = sourceTab == tab,
                        onClick = { sourceTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = LogSourceTab.entries.size
                        ),
                        icon = {
                            Icon(
                                imageVector = if (tab == LogSourceTab.DEVICE_HISTORY) {
                                    Icons.Rounded.History
                                } else {
                                    Icons.Rounded.CloudDone
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("repair_log_source_${tab.name.lowercase()}")
                    ) {
                        val badge = if (tab == LogSourceTab.DEVICE_HISTORY && localHistory.isNotEmpty()) {
                            "${tab.label} (${localHistory.size})"
                        } else {
                            tab.label
                        }
                        Text(badge, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            AnimatedVisibility(visible = sourceTab == LogSourceTab.DEVICE_HISTORY) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setHistorySearchQuery,
                    placeholder = { Text(stringResource(R.string.history_search_hint)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setHistorySearchQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )
            }

            if (summaryTotal > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RepairMetricPill(
                        label = "TOTAL",
                        value = summaryTotal.toString(),
                        accent = DiagnosticCyan,
                        modifier = Modifier.weight(1f)
                    )
                    RepairMetricPill(
                        label = "OPEN",
                        value = summaryOpen.toString(),
                        accent = SafetyAmber,
                        modifier = Modifier.weight(1f)
                    )
                    RepairMetricPill(
                        label = "FIXED",
                        value = summaryResolved.toString(),
                        accent = StatusOkGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LogFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            AnimatedContent(
                targetState = sourceTab,
                transitionSpec = {
                    fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
                },
                modifier = Modifier.weight(1f),
                label = "repair_log_source_transition"
            ) { activeTab ->
                when (activeTab) {
                    LogSourceTab.DEVICE_HISTORY -> {
                        val filteredLocal = localHistory.filter { entry ->
                            when (filter) {
                                LogFilter.ALL -> true
                                LogFilter.OPEN -> !entry.isResolved
                                LogFilter.RESOLVED -> entry.isResolved
                            }
                        }

                        if (filteredLocal.isEmpty()) {
                            EmptyHistoryStateCard(
                                title = if (searchQuery.isNotBlank()) "No Matching Diagnoses" else "No Local Diagnosis History",
                                subtitle = if (searchQuery.isNotBlank()) {
                                    "Try clearing your search filter or switch to another keyword."
                                } else {
                                    "Every audio, camera, or code diagnosis is automatically saved on-device so you can review mechanical insights and repair steps offline anytime."
                                }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredLocal, key = { it.id }) { entry ->
                                    val hasDanger = remember(entry.safetyWarnings, entry.severity) {
                                        entry.toDiagnosticResult().safetyWarnings.any {
                                            it.level == SafetyWarningLevel.DANGER
                                        }
                                    }
                                    HistoryItemCard(
                                        title = entry.title,
                                        modeLabel = entry.modeEnum().displayName,
                                        equipmentName = entry.equipmentName,
                                        primaryCulprit = entry.primaryCulprit,
                                        severity = entry.severityEnum(),
                                        dateMillis = entry.createdAtMillis,
                                        resolved = entry.isResolved,
                                        hasDangerWarning = hasDanger,
                                        isSyncedCloud = !entry.remoteReportId.isNullOrBlank(),
                                        testTag = "local_history_card_${entry.id}",
                                        modifier = Modifier.animateItem(),
                                        onClick = { selectedLocalEntry = entry },
                                        onToggleResolved = {
                                            viewModel.toggleLocalHistoryResolved(
                                                localId = entry.id,
                                                resolved = !entry.isResolved,
                                                remoteReportId = entry.remoteReportId
                                            )
                                        },
                                        onDelete = { pendingDeleteLocal = entry }
                                    )
                                }
                            }
                        }
                    }

                    LogSourceTab.CLOUD_LOG -> {
                        if (!viewModel.isCloudAccount) {
                            EmptyHistoryStateCard(
                                title = stringResource(R.string.cloud_log_local_mode_title),
                                subtitle = stringResource(R.string.cloud_log_local_mode_desc)
                            )
                        } else when (val state = diagnosticsState) {
                            is StreamUiState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = SafetyAmber)
                                }
                            }

                            is StreamUiState.Error -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }

                            is StreamUiState.Success -> {
                                val filtered = state.data.filter { report ->
                                    when (filter) {
                                        LogFilter.ALL -> true
                                        LogFilter.OPEN -> !report.resolved
                                        LogFilter.RESOLVED -> report.resolved
                                    }
                                }

                                if (filtered.isEmpty()) {
                                    EmptyHistoryStateCard(
                                        title = "No Cloud Repair Logs Yet",
                                        subtitle = "Tap Save to Repair Log after a diagnosis or promote any entry from Device History to sync it across devices."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(filtered, key = { it.id }) { report ->
                                            val hasDanger = remember(report.safetyWarnings, report.severity) {
                                                report.parsedSafetyWarnings().any {
                                                    it.level == SafetyWarningLevel.DANGER
                                                }
                                            }
                                            HistoryItemCard(
                                                title = report.title,
                                                modeLabel = report.modeEnum().displayName,
                                                equipmentName = report.equipmentName,
                                                primaryCulprit = report.primaryCulprit,
                                                severity = report.severityEnum(),
                                                dateMillis = report.createdAt?.toDate()?.time,
                                                resolved = report.resolved,
                                                hasDangerWarning = hasDanger,
                                                isSyncedCloud = true,
                                                testTag = "repair_log_card_${report.id}",
                                                modifier = Modifier.animateItem(),
                                                onClick = { selectedCloudReport = report },
                                                onToggleResolved = {
                                                    viewModel.toggleDiagnosticResolved(report.id, !report.resolved)
                                                },
                                                onDelete = { pendingDeleteCloud = report }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val activeCloud = selectedCloudReport
    if (activeCloud != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedCloudReport = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeCloud.equipmentName.ifBlank { activeCloud.categoryEnum().displayName },
                        style = MaterialTheme.typography.labelLarge,
                        color = DiagnosticCyan
                    )
                    IconButton(onClick = { selectedCloudReport = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close report details")
                    }
                }

                DiagnosticResultCard(
                    result = DiagnosticResult(
                        title = activeCloud.title,
                        primaryCulprit = activeCloud.primaryCulprit,
                        confidencePercent = activeCloud.confidencePercent.toInt(),
                        severity = activeCloud.severityEnum(),
                        summary = activeCloud.summary,
                        safetyWarnings = activeCloud.parsedSafetyWarnings(),
                        diySteps = activeCloud.diySteps,
                        partsTools = activeCloud.partsTools,
                        sources = activeCloud.parsedSources()
                    ),
                    isSaved = true,
                    onSaveClick = null,
                    onShareClick = onShareReport,
                    category = activeCloud.categoryEnum()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    val activeLocal = selectedLocalEntry
    if (activeLocal != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val liveEntry = localHistory.firstOrNull { it.id == activeLocal.id } ?: activeLocal
        val isSynced = !liveEntry.remoteReportId.isNullOrBlank()

        ModalBottomSheet(
            onDismissRequest = { selectedLocalEntry = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = liveEntry.equipmentName.ifBlank { liveEntry.categoryEnum().displayName },
                        style = MaterialTheme.typography.labelLarge,
                        color = DiagnosticCyan
                    )
                    IconButton(onClick = { selectedLocalEntry = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close history entry")
                    }
                }

                DiagnosticResultCard(
                    result = liveEntry.toDiagnosticResult(),
                    isSaved = isSynced,
                    onSaveClick = if (isSynced) null else ({ viewModel.syncLocalHistoryToCloud(liveEntry) }),
                    onShareClick = onShareReport,
                    category = liveEntry.categoryEnum()
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    val deleteLocalTarget = pendingDeleteLocal
    if (deleteLocalTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteLocal = null },
            title = { Text(stringResource(R.string.confirm_delete_log_title)) },
            text = { Text(stringResource(R.string.confirm_delete_log_msg, deleteLocalTarget.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedLocalEntry?.id == deleteLocalTarget.id) {
                            selectedLocalEntry = null
                        }
                        viewModel.deleteLocalHistoryEntry(deleteLocalTarget.id)
                        pendingDeleteLocal = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusCriticalRed,
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteLocal = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val deleteCloudTarget = pendingDeleteCloud
    if (deleteCloudTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteCloud = null },
            title = { Text(stringResource(R.string.confirm_delete_log_title)) },
            text = { Text(stringResource(R.string.confirm_delete_log_msg, deleteCloudTarget.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedCloudReport?.id == deleteCloudTarget.id) {
                            selectedCloudReport = null
                        }
                        viewModel.deleteDiagnostic(deleteCloudTarget.id)
                        pendingDeleteCloud = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusCriticalRed,
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCloud = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun RepairMetricPill(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(
            1.dp,
            accent.copy(alpha = 0.28f),
            RoundedCornerShape(14.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyHistoryStateCard(
    title: String,
    subtitle: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AssignmentTurnedIn,
                contentDescription = null,
                tint = SafetyAmber,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryItemCard(
    title: String,
    modeLabel: String,
    equipmentName: String,
    primaryCulprit: String,
    severity: DiagnosticSeverity,
    dateMillis: Long?,
    resolved: Boolean,
    hasDangerWarning: Boolean,
    isSyncedCloud: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggleResolved: () -> Unit,
    onDelete: () -> Unit
) {
    val severityColor = when (severity) {
        DiagnosticSeverity.LOW -> StatusOkGreen
        DiagnosticSeverity.MEDIUM -> SafetyAmber
        DiagnosticSeverity.HIGH -> StatusWarnOrange
        DiagnosticSeverity.CRITICAL -> StatusCriticalRed
    }
    val dateLabel = remember(dateMillis) {
        if (dateMillis != null) {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(dateMillis))
        } else {
            "Just now"
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .workshopFocusRing(
                shape = RoundedCornerShape(18.dp),
                focusColor = SafetyAmber,
                unfocusedColor = MaterialTheme.colorScheme.outlineVariant
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = severityColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = modeLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = severityColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (hasDangerWarning) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = "Contains critical safety warning",
                            tint = StatusCriticalRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (isSyncedCloud) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = "Synced to Cloud",
                            tint = DiagnosticCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleResolved) {
                        Icon(
                            imageVector = if (resolved) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.RadioButtonUnchecked
                            },
                            contentDescription = if (resolved) "Mark open" else "Mark fixed",
                            tint = if (resolved) StatusOkGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete repair entry",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (equipmentName.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Build,
                        contentDescription = null,
                        tint = DiagnosticCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = equipmentName,
                        style = MaterialTheme.typography.labelMedium,
                        color = DiagnosticCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = primaryCulprit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
