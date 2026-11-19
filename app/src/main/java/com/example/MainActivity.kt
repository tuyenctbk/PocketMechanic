package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Garage
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.AiAccessRepository
import com.example.ai.MechanicAiService
import com.example.analytics.AppEventTracker
import com.example.data.FirestoreRepository
import com.example.data.local.DiagnosisHistoryRepository
import com.example.data.local.LocalEquipmentRepository
import com.example.data.local.PocketMechanicDatabase
import com.example.donation.BillingManager
import com.example.engagement.EngagementPreferencesRepository
import com.example.engagement.PlayEngagementManager
import com.example.firebase.FirebaseAvailability
import com.example.firebase.configureAppCheck
import com.example.hardware.AudioRecorderManager
import com.example.hardware.HandsFreeVoiceEngine
import com.example.hardware.TorchController
import com.example.model.DiagnosticMode
import com.example.model.DiagnosticSeverity
import com.example.ui.BillingViewModel
import com.example.ui.EngagementViewModel
import com.example.ui.MechanicViewModel
import com.example.ui.WorkshopEventHooks
import com.example.ui.auth.AuthScreen
import com.example.ui.components.ActiveSupporterBadge
import com.example.ui.components.AiEngineChoiceSheet
import com.example.ui.components.DonationModalSheet
import com.example.ui.components.SmartEngagementPopupDialog
import com.example.ui.components.TorchActionButton
import com.example.ui.components.WorkshopSplashOverlay
import com.example.ui.components.rememberIsTelevision
import com.example.ui.components.workshopFocusRing
import com.example.ui.screens.AudioCaptureScreen
import com.example.ui.screens.GarageScreen
import com.example.ui.screens.HandsFreeScreen
import com.example.ui.screens.RepairLogScreen
import com.example.ui.screens.WorkbenchScreen
import com.example.ui.theme.DiagnosticCyan
import com.example.ui.theme.PocketMechanicTheme
import com.example.ui.theme.SafetyAmber
import com.example.util.findActiveActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class WorkshopTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    AUDIO_PROBE("audio_probe", R.string.nav_audio, Icons.Rounded.GraphicEq, Icons.Outlined.GraphicEq),
    WORKBENCH("workbench", R.string.nav_workbench, Icons.Rounded.Build, Icons.Outlined.Build),
    HANDS_FREE("hands_free", R.string.nav_hands_free, Icons.Rounded.Mic, Icons.Outlined.Mic),
    REPAIR_LOG("logs", R.string.nav_logs, Icons.Rounded.Assignment, Icons.Outlined.Assignment),
    GARAGE("garage", R.string.nav_garage, Icons.Rounded.Garage, Icons.Outlined.Garage)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            PocketMechanicTheme(darkTheme = true) {
                AppNavigation()
            }
        }
    }
}

private const val SESSION_PREFS = "pocket_mechanic_session"
private const val KEY_GUEST_MODE = "guest_workshop_mode"
private const val LOCAL_WORKSHOP_UID = "local_workshop_user"

private data class ActiveSession(
    val userId: String,
    val isCloudAccount: Boolean
)

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val auth = remember(context) { FirebaseAvailability.safeAuth(context) }
    val authFlow = remember(auth) { auth?.authStateFlow() ?: flowOf(null) }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = auth?.currentUser)

    val sessionPrefs = remember(context) {
        context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
    }
    var isGuestWorkshop by remember {
        mutableStateOf(sessionPrefs.getBoolean(KEY_GUEST_MODE, false))
    }

    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(850L)
        showSplash = false
    }

    val activeSession = when {
        currentUser != null -> ActiveSession(userId = currentUser!!.uid, isCloudAccount = true)
        isGuestWorkshop -> ActiveSession(userId = LOCAL_WORKSHOP_UID, isCloudAccount = false)
        else -> null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = activeSession,
            animationSpec = tween(durationMillis = 300),
            label = "auth_crossfade"
        ) { session ->
            if (session == null) {
                AuthScreen(
                    onAuthSuccess = {
                        sessionPrefs.edit().putBoolean(KEY_GUEST_MODE, false).apply()
                        isGuestWorkshop = false
                    },
                    onContinueGuest = {
                        sessionPrefs.edit().putBoolean(KEY_GUEST_MODE, true).apply()
                        isGuestWorkshop = true
                    }
                )
            } else {
                key("${session.userId}_${session.isCloudAccount}") {
                    PocketMechanicShell(
                        currentUserId = session.userId,
                        isCloudAccount = session.isCloudAccount,
                        onExitSession = {
                            sessionPrefs.edit().putBoolean(KEY_GUEST_MODE, false).apply()
                            isGuestWorkshop = false
                        }
                    )
                }
            }
        }

        WorkshopSplashOverlay(visible = showSplash)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PocketMechanicShell(
    currentUserId: String,
    isCloudAccount: Boolean,
    onExitSession: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isTv = rememberIsTelevision()

    val lifecycleOwner = LocalLifecycleOwner.current
    val hostActivity = LocalActivity.current?.takeIf { !it.isFinishing && !it.isDestroyed }
        ?: context.findActiveActivity()

    val engagementViewModel: EngagementViewModel = viewModel(
        key = "engagement_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val localDb = PocketMechanicDatabase.getInstance(app)
                val prefsRepo = EngagementPreferencesRepository(app)
                val tracker = AppEventTracker(localDb.appEventDao())
                val playMgr = PlayEngagementManager(app)
                EngagementViewModel(
                    preferencesRepo = prefsRepo,
                    eventTracker = tracker,
                    playEngagementManager = playMgr,
                    userId = currentUserId
                )
            }
        }
    )

    val billingViewModel: BillingViewModel = viewModel(
        key = "billing_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val localDb = PocketMechanicDatabase.getInstance(app)
                val prefsRepo = EngagementPreferencesRepository(app)
                val tracker = AppEventTracker(localDb.appEventDao())
                val billingMgr = BillingManager(app)
                BillingViewModel(
                    billingManager = billingMgr,
                    preferencesRepo = prefsRepo,
                    eventTracker = tracker,
                    userId = currentUserId
                )
            }
        }
    )

    DisposableEffect(lifecycleOwner, billingViewModel) {
        lifecycleOwner.lifecycle.addObserver(billingViewModel)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(billingViewModel)
        }
    }

    val viewModel: MechanicViewModel = viewModel(
        key = "${currentUserId}_$isCloudAccount",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = if (isCloudAccount) FirebaseAvailability.safeFirestore(app, databaseId) else null
                val auth = if (isCloudAccount) FirebaseAvailability.safeAuth(app) else null
                val localDb = PocketMechanicDatabase.getInstance(app)
                val prefsRepo = EngagementPreferencesRepository(app)
                val aiAccessRepo = AiAccessRepository(app)
                MechanicViewModel(
                    repository = FirestoreRepository(db, auth),
                    historyRepository = DiagnosisHistoryRepository(localDb.diagnosisHistoryDao()),
                    aiService = MechanicAiService(),
                    currentUserId = currentUserId,
                    localEquipmentRepository = LocalEquipmentRepository(localDb.localEquipmentDao()),
                    isCloudAccount = isCloudAccount && db != null && auth?.currentUser != null,
                    aiAccessRepository = aiAccessRepo,
                    isActiveSupporterFlow = prefsRepo.stateFlow.map { it.isActiveSupporter },
                    eventHooks = object : WorkshopEventHooks {
                        override fun onDiagnosisStarted(mode: DiagnosticMode) =
                            engagementViewModel.onDiagnosisStarted(mode)

                        override fun onDiagnosisCompleted(
                            mode: DiagnosticMode,
                            severity: DiagnosticSeverity,
                            confidence: Int
                        ) = engagementViewModel.onDiagnosisCompleted(mode, severity, confidence)

                        override fun onDiagnosisFailed(reason: String) =
                            engagementViewModel.onDiagnosisFailed(reason)

                        override fun onReportSaved() =
                            engagementViewModel.onReportSaved()

                        override fun onRepairResolved() =
                            engagementViewModel.onRepairResolved()

                        override fun onFollowUpCompleted() =
                            engagementViewModel.onFollowUpCompleted()

                        override fun onHandsFreeTurnCompleted() =
                            engagementViewModel.onHandsFreeTurnCompleted()

                        override fun onEquipmentSaved(isNew: Boolean) =
                            engagementViewModel.onEquipmentSaved(isNew)
                    }
                )
            }
        }
    )

    val audioRecorder = remember { AudioRecorderManager() }
    val torchController = remember(context) { TorchController(context) }
    val voiceEngine = remember(context) { HandsFreeVoiceEngine(context) }

    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.release()
            torchController.setTorch(false)
            torchController.release()
            voiceEngine.release()
        }
    }

    val isTorchOn by torchController.isTorchOn.collectAsStateWithLifecycle()
    val isFlashAvailable by torchController.isFlashAvailable.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val engagementNotice by engagementViewModel.engagementNotice.collectAsStateWithLifecycle()
    val engagementPrefs by engagementViewModel.engagementState.collectAsStateWithLifecycle()
    val billingState by billingViewModel.billingState.collectAsStateWithLifecycle()
    val activePopup by engagementViewModel.activePopup.collectAsStateWithLifecycle()
    val isDonationSheetVisible by engagementViewModel.isDonationSheetVisible.collectAsStateWithLifecycle()
    val showInlineSupportBanner by engagementViewModel.showInlineSupportBanner.collectAsStateWithLifecycle()
    val aiAccessState by viewModel.aiAccessState.collectAsStateWithLifecycle()
    val byokVerification by viewModel.byokVerification.collectAsStateWithLifecycle()
    var showAiEngineSheet by remember { mutableStateOf(false) }

    LaunchedEffect(statusBanner) {
        val message = statusBanner
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearStatusBanner()
        }
    }

    LaunchedEffect(engagementNotice) {
        val message = engagementNotice
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            engagementViewModel.clearNotice()
        }
    }

    var activeTab by rememberSaveable { mutableStateOf(WorkshopTab.AUDIO_PROBE) }

    if (activeTab != WorkshopTab.AUDIO_PROBE) {
        BackHandler { activeTab = WorkshopTab.AUDIO_PROBE }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground_art),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAiEngineSheet = true },
                            modifier = Modifier.testTag("top_bar_ai_engine_button")
                        ) {
                            Icon(
                                imageVector = if (aiAccessState.hasByokKey) Icons.Rounded.Key else Icons.Rounded.AutoAwesome,
                                contentDescription = stringResource(R.string.ai_engine_settings_title),
                                tint = DiagnosticCyan
                            )
                        }

                        if (engagementPrefs.isActiveSupporter) {
                            ActiveSupporterBadge(
                                onClick = { engagementViewModel.openDonationSheet("top_bar_badge") },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        } else {
                            IconButton(
                                onClick = { engagementViewModel.openDonationSheet("top_bar_heart") },
                                modifier = Modifier.testTag("top_bar_support_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FavoriteBorder,
                                    contentDescription = stringResource(R.string.support_development_title),
                                    tint = SafetyAmber
                                )
                            }
                        }

                        if (isFlashAvailable) {
                            TorchActionButton(
                                isTorchOn = isTorchOn,
                                onToggle = {
                                    val ok = torchController.toggleTorch()
                                    if (ok) {
                                        engagementViewModel.onFlashlightToggled(torchController.isTorchOn.value)
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.torch_card_unavailable)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        WorkshopTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SafetyAmber,
                                    selectedTextColor = SafetyAmber,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = if (isTv) 16.dp else 0.dp)
                    ) {
                        WorkshopTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = SafetyAmber,
                                    selectedTextColor = SafetyAmber,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.workshopFocusRing(
                                    shape = RoundedCornerShape(14.dp),
                                    focusColor = DiagnosticCyan
                                )
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = if (isTv) 20.dp else 0.dp, bottom = if (isTv) 12.dp else 0.dp)
                ) {
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) +
                                slideInVertically(animationSpec = tween(220)) { it / 28 })
                                .togetherWith(fadeOut(animationSpec = tween(150)))
                        },
                        label = "workshop_tab_transition"
                    ) { tab ->
                        when (tab) {
                            WorkshopTab.AUDIO_PROBE -> AudioCaptureScreen(
                                viewModel = viewModel,
                                audioRecorder = audioRecorder,
                                torchController = torchController,
                                showInlineSupportBanner = showInlineSupportBanner,
                                onOpenSupportSheet = {
                                    engagementViewModel.openDonationSheet("post_audio_diagnosis_card")
                                },
                                onDismissSupportBanner = {
                                    engagementViewModel.dismissInlineSupportBanner()
                                },
                                onShareReport = { summary ->
                                    engagementViewModel.triggerManualShare(hostActivity, summary)
                                },
                                onTorchToggled = { enabled ->
                                    engagementViewModel.onFlashlightToggled(enabled)
                                },
                                onOpenAiSettings = { showAiEngineSheet = true }
                            )
                            WorkshopTab.WORKBENCH -> WorkbenchScreen(
                                viewModel = viewModel,
                                audioRecorder = audioRecorder,
                                torchController = torchController,
                                showInlineSupportBanner = showInlineSupportBanner,
                                onOpenSupportSheet = {
                                    engagementViewModel.openDonationSheet("post_workbench_diagnosis_card")
                                },
                                onDismissSupportBanner = {
                                    engagementViewModel.dismissInlineSupportBanner()
                                },
                                onShareReport = { summary ->
                                    engagementViewModel.triggerManualShare(hostActivity, summary)
                                },
                                onTorchToggled = { enabled ->
                                    engagementViewModel.onFlashlightToggled(enabled)
                                },
                                onOpenAiSettings = { showAiEngineSheet = true }
                            )
                            WorkshopTab.HANDS_FREE -> HandsFreeScreen(
                                viewModel = viewModel,
                                voiceEngine = voiceEngine,
                                audioRecorder = audioRecorder,
                                torchController = torchController,
                                onTorchToggled = { enabled ->
                                    engagementViewModel.onFlashlightToggled(enabled)
                                }
                            )
                            WorkshopTab.REPAIR_LOG -> RepairLogScreen(
                                viewModel = viewModel,
                                onShareReport = { summary ->
                                    engagementViewModel.triggerManualShare(hostActivity, summary)
                                }
                            )
                            WorkshopTab.GARAGE -> GarageScreen(
                                viewModel = viewModel,
                                onInspectEquipment = { eq ->
                                    viewModel.selectEquipment(eq)
                                    activeTab = WorkshopTab.WORKBENCH
                                },
                                engagementViewModel = engagementViewModel,
                                onOpenAiSettings = { showAiEngineSheet = true },
                                onSignOutComplete = onExitSession
                            )
                        }
                    }
                }
            }
        }

        val currentPopup = activePopup
        if (currentPopup != null && !isDonationSheetVisible && !showAiEngineSheet) {
            SmartEngagementPopupDialog(
                prompt = currentPopup,
                onPrimaryAction = { engagementViewModel.acceptActivePopup(hostActivity) },
                onSecondaryLater = { engagementViewModel.dismissActivePopup(optOut = false) },
                onOptOut = if (currentPopup.optOutActionLabel != null) {
                    { engagementViewModel.dismissActivePopup(optOut = true) }
                } else {
                    null
                }
            )
        }

        if (isDonationSheetVisible) {
            DonationModalSheet(
                billingState = billingState,
                isActiveSupporter = engagementPrefs.isActiveSupporter,
                supporterTierId = engagementPrefs.supporterTierId,
                onSelectChannel = { channel ->
                    engagementViewModel.onDonationChannelSelected(channel)
                },
                onPurchaseTier = { tier ->
                    billingViewModel.launchPurchase(hostActivity, tier)
                },
                onExternalFeedback = { msg ->
                    engagementViewModel.showExternalFeedback(msg)
                },
                onDismiss = {
                    billingViewModel.clearStatusMessage()
                    engagementViewModel.dismissDonationSheet()
                }
            )
        }

        if (showAiEngineSheet) {
            AiEngineChoiceSheet(
                accessState = aiAccessState,
                isActiveSupporter = engagementPrefs.isActiveSupporter,
                verificationState = byokVerification,
                onSelectMode = viewModel::setAiProviderMode,
                onTestAndSaveKey = viewModel::verifyAndSaveByokKey,
                onSaveKeyWithoutTest = viewModel::saveByokKeyWithoutTest,
                onRemoveKey = viewModel::removeByokKey,
                onOpenSupportSheet = {
                    engagementViewModel.openDonationSheet("ai_engine_sheet")
                },
                onDismiss = {
                    viewModel.clearByokVerification()
                    showAiEngineSheet = false
                }
            )
        }
    }
}
