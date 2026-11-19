package com.example.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.firebase.FirebaseAvailability
import com.example.model.Equipment
import com.example.model.EquipmentCategory
import com.example.ui.EngagementViewModel
import com.example.ui.MechanicViewModel
import com.example.ui.StreamUiState
import com.example.ui.auth.signOut
import com.example.ui.components.ActiveSupporterBadge
import com.example.ui.components.GarageAiEngineCard
import com.example.ui.components.SupportAboutAndTelemetrySection
import com.example.ui.components.workshopFocusRing
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.StatusCriticalRed
import com.example.ui.theme.StatusOkGreen
import com.example.util.findActiveActivity

@Composable
fun GarageScreen(
    viewModel: MechanicViewModel,
    onInspectEquipment: (Equipment) -> Unit,
    modifier: Modifier = Modifier,
    engagementViewModel: EngagementViewModel? = null,
    onOpenAiSettings: () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val user = remember(context, viewModel.isCloudAccount) {
        if (viewModel.isCloudAccount) FirebaseAvailability.safeAuth(context)?.currentUser else null
    }

    val equipmentState by viewModel.equipmentState.collectAsStateWithLifecycle()
    val localHistory by viewModel.localHistoryState.collectAsStateWithLifecycle()
    val aiAccessState by viewModel.aiAccessState.collectAsStateWithLifecycle()
    val isActiveSupporter by viewModel.isActiveSupporter.collectAsStateWithLifecycle()
    val engagementPrefs = engagementViewModel?.engagementState?.collectAsStateWithLifecycle()?.value
    val recentEvents = engagementViewModel?.recentEvents?.collectAsStateWithLifecycle()?.value.orEmpty()
    val totalEventCount = engagementViewModel?.totalEventCount?.collectAsStateWithLifecycle()?.value ?: 0
    val evaluationReason = engagementViewModel?.lastEvaluationReason?.collectAsStateWithLifecycle()?.value.orEmpty()

    val registeredUnits = (equipmentState as? StreamUiState.Success<List<Equipment>>)?.data?.size ?: 0
    val totalDiagnoses = localHistory.size
    val resolvedRepairs = localHistory.count { it.isResolved }

    var showEditor by remember { mutableStateOf(false) }
    var editingEquipment by remember { mutableStateOf<Equipment?>(null) }
    var pendingDeleteEquipment by remember { mutableStateOf<Equipment?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Account Header Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(18.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = SafetyAmber
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = user?.displayName?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.local_workshop_owner),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = user?.email?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.local_workshop_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (engagementPrefs?.isActiveSupporter == true) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    ActiveSupporterBadge(
                                        onClick = { engagementViewModel?.openDonationSheet("garage_badge") }
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                signOut(
                                    context = context,
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onSignOutComplete = onSignOutComplete
                                )
                            },
                            modifier = Modifier
                                .workshopFocusRing(RoundedCornerShape(12.dp), focusColor = SafetyAmber)
                                .testTag("sign_out_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = stringResource(R.string.sign_out),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.sign_out), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Compact Workshop Overview Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GarageStatTile(
                        value = registeredUnits.toString(),
                        label = stringResource(R.string.garage_stat_units),
                        accent = DiagnosticCyan,
                        modifier = Modifier.weight(1f)
                    )
                    GarageStatTile(
                        value = totalDiagnoses.toString(),
                        label = stringResource(R.string.garage_stat_diagnoses),
                        accent = SafetyAmber,
                        modifier = Modifier.weight(1f)
                    )
                    GarageStatTile(
                        value = resolvedRepairs.toString(),
                        label = stringResource(R.string.garage_stat_fixed),
                        accent = StatusOkGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // AI Engine, Quota & BYOK Key Card
            item {
                GarageAiEngineCard(
                    accessState = aiAccessState,
                    isActiveSupporter = isActiveSupporter || (engagementPrefs?.isActiveSupporter == true),
                    onOpenAiSettings = onOpenAiSettings
                )
            }

            when (val state = equipmentState) {
                is StreamUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = SafetyAmber)
                        }
                    }
                }

                is StreamUiState.Error -> {
                    item {
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
                }

                is StreamUiState.Success -> {
                    if (state.data.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(20.dp)
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_empty_garage),
                                        contentDescription = "PocketMechanic diagnostic toolkit illustration",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                    Text(
                                        text = stringResource(R.string.garage_empty_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.garage_empty_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = {
                                            editingEquipment = null
                                            showEditor = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("empty_add_equipment_button"),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.garage_add_first))
                                    }
                                }
                            }
                        }
                    } else {
                        items(state.data, key = { it.id }) { equipment ->
                            EquipmentCard(
                                equipment = equipment,
                                modifier = Modifier.animateItem(),
                                onInspect = { onInspectEquipment(equipment) },
                                onEdit = {
                                    editingEquipment = equipment
                                    showEditor = true
                                },
                                onDelete = { pendingDeleteEquipment = equipment }
                            )
                        }
                    }
                }
            }

            if (engagementViewModel != null && engagementPrefs != null) {
                item {
                    val activeActivity = LocalActivity.current?.takeIf { !it.isFinishing && !it.isDestroyed }
                        ?: context.findActiveActivity()
                    SupportAboutAndTelemetrySection(
                        engagementState = engagementPrefs,
                        recentEvents = recentEvents,
                        totalEventCount = totalEventCount,
                        lastEvaluationReason = evaluationReason,
                        onOpenDonationSheet = { engagementViewModel.openDonationSheet("garage_static_section") },
                        onRateApp = { engagementViewModel.triggerManualRate(activeActivity) },
                        onShareApp = { engagementViewModel.triggerManualShare(activeActivity) },
                        onCheckUpdates = { engagementViewModel.triggerManualUpdateCheck(activeActivity) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingEquipment = null
                showEditor = true
            },
            containerColor = SafetyAmber,
            contentColor = Color(0xFF281800),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_equipment_fab")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add equipment to garage")
        }
    }

    if (showEditor) {
        EquipmentEditorDialog(
            initial = editingEquipment,
            onDismiss = { showEditor = false },
            onSave = { name, category, makeModel, yearOrSerial, notes ->
                viewModel.saveEquipment(
                    editingId = editingEquipment?.id,
                    name = name,
                    category = category,
                    makeModel = makeModel,
                    yearOrSerial = yearOrSerial,
                    notes = notes
                )
                showEditor = false
            }
        )
    }

    val deleteTarget = pendingDeleteEquipment
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteEquipment = null },
            title = { Text(stringResource(R.string.confirm_delete_equipment_title)) },
            text = {
                Text(stringResource(R.string.confirm_delete_equipment_msg, deleteTarget.name))
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEquipment(deleteTarget.id)
                        pendingDeleteEquipment = null
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
                TextButton(onClick = { pendingDeleteEquipment = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun GarageStatTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
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
private fun EquipmentCard(
    equipment: Equipment,
    onInspect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = equipment.categoryEnum().displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = DiagnosticCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = equipment.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    val meta = listOf(equipment.yearOrSerial, equipment.makeModel)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Edit equipment",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete equipment",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (equipment.notes.isNotBlank()) {
                Text(
                    text = equipment.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onInspect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.garage_inspect_unit), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EquipmentEditorDialog(
    initial: Equipment?,
    onDismiss: () -> Unit,
    onSave: (String, EquipmentCategory, String, String, String) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.categoryEnum() ?: EquipmentCategory.VEHICLE) }
    var makeModel by remember(initial) { mutableStateOf(initial?.makeModel.orEmpty()) }
    var yearOrSerial by remember(initial) { mutableStateOf(initial?.yearOrSerial.orEmpty()) }
    var notes by remember(initial) { mutableStateOf(initial?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) {
                    stringResource(R.string.garage_add_equipment)
                } else {
                    stringResource(R.string.garage_edit_equipment)
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EquipmentCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text(stringResource(R.string.garage_field_nickname)) },
                    placeholder = { Text("e.g., Family Civic / Basement Washer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = makeModel,
                    onValueChange = { makeModel = it.take(100) },
                    label = { Text(stringResource(R.string.garage_field_make_model)) },
                    placeholder = { Text("e.g., LG WM3900HWA") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = yearOrSerial,
                    onValueChange = { yearOrSerial = it.take(60) },
                    label = { Text(stringResource(R.string.garage_field_year_serial)) },
                    placeholder = { Text("e.g., 2019 or SN-4421") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(300) },
                    label = { Text(stringResource(R.string.garage_field_notes)) },
                    placeholder = { Text("Oil filter size, recent repairs...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, category, makeModel, yearOrSerial, notes) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
