package com.evyr.rads.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.evyr.rads.data.local.DEFAULT_CALORIE_TARGET
import com.evyr.rads.data.local.DEFAULT_FAT_WARN_GRAMS
import com.evyr.rads.health.HealthConnectManager
import com.evyr.rads.ui.TodayViewModel
import com.evyr.rads.ui.components.*
import com.evyr.rads.ui.theme.NavInactive
import java.io.ByteArrayOutputStream

private val MEAL_SLOTS = listOf("breakfast", "lunch", "dinner", "snack")

private enum class AppTab(val label: String, val icon: AppIcon) {
    LOG("Log", AppIcon.LOG),
    STATS("Stats", AppIcon.STATS),
    SYNC("Sync", AppIcon.SYNC),
    SETUP("Setup", AppIcon.SETUP)
}

@Composable
fun TodayScreen() {
    val vm: TodayViewModel = viewModel()
    val context = LocalContext.current

    val profile by vm.profile.collectAsState()
    val profileLoaded by vm.profileLoaded.collectAsState()
    val entries by vm.entries.collectAsState()
    val health by vm.healthToday.collectAsState()
    val selectedId by vm.selectedEntryId.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val scanState by vm.scanState.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val searchMessage by vm.searchMessage.collectAsState()
    val pendingPortion by vm.pendingPortion.collectAsState()
    val portionOptions by vm.portionOptions.collectAsState()
    val viewDate by vm.viewDate.collectAsState()
    val isToday by vm.isViewingToday.collectAsState()
    val history by vm.last30Days.collectAsState()
    val frequentFoods by vm.frequentFoods.collectAsState()
    val safeFoods by vm.safeFoods.collectAsState()
    val safeKeys by vm.safeKeys.collectAsState()

    // Crossing midnight while the app sits in the background must roll the day.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshDate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val healthManager = remember { HealthConnectManager(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = healthManager.permissionContract()
    ) { vm.sync() }

    if (!profileLoaded) return

    if (profile?.onboarded != true) {
        OnboardingScreen(onComplete = { vm.completeOnboarding(it) })
        return
    }

    var activeTab by remember { mutableStateOf(AppTab.LOG) }
    // Default to the meal that matches the clock, not whatever comes first.
    var activeMeal by remember { mutableStateOf(vm.mealSlotForNow()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    // True between asking for a gallery photo for the menu and it coming back (or being cancelled).
    var menuPending by remember { mutableStateOf(false) }
    // Where the camera app is saving the full-size menu photo right now.
    var menuPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val menuState by vm.menuState.collectAsState()
    val swapState by vm.swapState.collectAsState()

    // Keep the ViewModel aware of where scans should land.
    vm.activeMealSlot = activeMeal

    // Gallery pick -> bytes -> vision
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            readBytes(context, uri)?.let {
                if (menuPending) vm.addMenuPage(shrinkForUpload(it)) else vm.onPhotoCaptured(it)
            }
        }
        menuPending = false
    }

    // Camera preview capture -> bitmap -> jpeg bytes -> vision
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val out = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
            vm.onPhotoCaptured(out.toByteArray())
        }
    }

    // Menu photos need the full-size picture (the small preview is too blurry to
    // read menu text), so the camera app saves into our cache and we read it back.
    val menuCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = menuPhotoUri
        if (saved && uri != null) {
            readBytes(context, uri)?.let { vm.addMenuPage(shrinkForUpload(it)) }
        }
        menuPhotoUri = null
    }

    val listState = rememberLazyListState()
    val statsScroll = rememberScrollState()
    val syncScroll = rememberScrollState()
    val setupScroll = rememberScrollState()

    val mealEntries = entries.filter { it.mealSlot == activeMeal }
    val selectedEntry = entries.firstOrNull { it.id == selectedId }
    val fatLimit = profile?.fatWarnGramsPerMeal ?: DEFAULT_FAT_WARN_GRAMS

    fun launchBarcode() {
        val options = GmsBarcodeScannerOptions.Builder()
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.let { vm.onBarcodeScanned(it) }
            }
            .addOnFailureListener {
                vm.clearScan()
            }
    }

    // No FAB: the approved mockup logs food from each meal card's own
    // "+ Add to" button, which opens the same sheet the FAB used to.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.quickLogged.collect { entry ->
            val result = snackbar.showSnackbar(
                message = "Logged ${entry.name} to ${entry.mealSlot.replaceFirstChar { it.uppercase() }}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.deleteEntryQuietly(entry)
        }
    }
    LaunchedEffect(Unit) {
        vm.removed.collect { entry ->
            val result = snackbar.showSnackbar(
                message = "Removed ${entry.name}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.restoreEntry(entry)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                Hairline()
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    AppTab.values().forEach { tab ->
                        val selected = activeTab == tab
                        val tint = if (selected) MaterialTheme.colorScheme.primary else NavInactive
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (tab == AppTab.SYNC) vm.sync()
                                activeTab = tab
                            },
                            icon = { AppIconView(tab.icon, tint, iconSize = 24.dp) },
                            label = {
                                Text(
                                    tab.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = NavInactive,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        // Log runs edge to edge so its hero can go full-bleed; the other
        // tabs keep the usual side padding.
        val body = if (activeTab == AppTab.LOG) {
            Modifier.fillMaxSize().padding(padding)
        } else {
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)
        }
        Column(body) {
            when (activeTab) {
                AppTab.LOG -> TabLogView(
                    viewDate = viewDate,
                    isToday = isToday,
                    onPreviousDay = { vm.previousDay() },
                    onNextDay = { vm.nextDay() },
                    onJumpToToday = { vm.jumpToToday() },
                    mealSlots = MEAL_SLOTS,
                    activeMeal = activeMeal,
                    dayEntries = entries,
                    calorieTarget = profile?.calorieTarget() ?: DEFAULT_CALORIE_TARGET,
                    entries = mealEntries,
                    selectedEntry = selectedEntry,
                    fatWarnGrams = fatLimit,
                    conditions = profile?.conditionSet() ?: emptySet(),
                    limits = profile?.limits() ?: com.evyr.rads.data.Limits(),
                    allergens = profile?.allergenSet() ?: emptySet(),
                    customAllergens = com.evyr.rads.data.CustomAllergen.parseCsv(profile?.customAllergens),
                    safeKeys = safeKeys,
                    onToggleSafe = { vm.toggleSafe(it) },
                    listState = listState,
                    onSelectMeal = { activeMeal = it },
                    onSelectEntry = { vm.selectEntry(it.id) },
                    onDeleteEntry = { vm.deleteEntry(it) },
                    onSuggestSwaps = { vm.suggestSwapsForEntry(it) },
                    onAddToMeal = { slot ->
                        activeMeal = slot
                        vm.resetSearch()
                        showSearch = true
                    },
                    onOpenProfile = { activeTab = AppTab.SETUP },
                    layout = (profile ?: com.evyr.rads.data.local.UserProfile()).homeLayout(),
                    onCheckFood = {
                        activeMeal = vm.mealSlotForNow()
                        vm.resetSearch()
                        showSearch = true
                    },
                    onCheckMenu = {
                        if (!vm.aiEnabled()) {
                            android.widget.Toast.makeText(
                                context, "Menu check needs a Gemini key. Setup tab > AI food detection has steps to get one.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } else vm.openMenuMode()
                    }
                )
                AppTab.STATS -> TabStatsView(
                    scrollState = statsScroll,
                    isToday = isToday,
                    entries = entries,
                    profile = profile,
                    health = health,
                    mealSlots = MEAL_SLOTS,
                    history = history
                )
                AppTab.SYNC -> TabSyncView(
                    scrollState = syncScroll,
                    status = syncStatus,
                    availability = healthManager.availability(),
                    health = health,
                    imperial = profile?.useImperial ?: true,
                    onSync = { vm.sync() },
                    onRequestPermission = {
                        permissionLauncher.launch(healthManager.permissions)
                    },
                    onOpenSettings = {
                        runCatching { context.startActivity(healthManager.settingsIntent()) }
                    },
                    onInstall = {
                        runCatching { context.startActivity(healthManager.installIntent()) }
                    }
                )
                AppTab.SETUP -> TabSetupView(
                    scrollState = setupScroll,
                    profile = profile,
                    onUpdate = { vm.saveProfile(it) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddEntryDialog(
            mealSlot = activeMeal,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, cal, fat, protein, carbs ->
                vm.addEntry(name, activeMeal, cal, fat, protein, carbs)
                showAddDialog = false
            }
        )
    }

    if (swapState != TodayViewModel.SwapState.Idle) {
        SwapDialog(state = swapState, onDismiss = { vm.clearSwap() })
    }

    if (menuState != TodayViewModel.MenuState.Idle) {
        MenuModeDialog(
            state = menuState,
            hasConditions = !(profile?.conditionSet().isNullOrEmpty()) || !(profile?.allergenSet().isNullOrEmpty()),
            onTakePhoto = {
                runCatching {
                    val dir = java.io.File(context.cacheDir, "menu").apply { mkdirs() }
                    val file = java.io.File(dir, "menu_${System.nanoTime()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    menuPhotoUri = uri
                    menuCameraLauncher.launch(uri)
                }.onFailure {
                    menuPhotoUri = null
                    android.widget.Toast.makeText(
                        context, "Couldn't open a camera app on this phone.", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onPickPhoto = {
                menuPending = true
                galleryLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onAnalyze = { vm.analyzeMenu(it) },
            onPickDish = { vm.assess(it.food, activeMeal) },
            onBackToPhotos = { vm.backToMenuPhotos() },
            onRestart = { vm.openMenuMode() },
            onDismiss = { vm.clearMenu() }
        )
    }

    if (showSearch) {
        FoodSearchDialog(
            mealSlot = activeMeal,
            query = searchQuery,
            results = searchResults,
            searching = searching,
            message = searchMessage,
            aiEnabled = vm.aiEnabled(),
            safeFoods = safeFoods.map { vm.safeAsScanned(it) },
            quickFoods = frequentFoods,
            onRemoveSafe = { vm.removeSafe(it) },
            onQuickLog = { food ->
                vm.quickLog(food, activeMeal)
                showSearch = false
                vm.resetSearch()
            },
            onQueryChange = { vm.setSearchQuery(it) },
            onSearch = { vm.runSearch() },
            onPick = { vm.choosePortion(it) },
            onScanBarcode = { launchBarcode() },
            onTakePhoto = {
                if (!vm.aiEnabled()) {
                    android.widget.Toast.makeText(
                        context, "Photo logging needs a Gemini key. Setup tab > AI food detection has steps to get one.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@FoodSearchDialog
                }
                // No camera app on the device throws ActivityNotFoundException.
                runCatching { cameraLauncher.launch(null) }.onFailure {
                    android.widget.Toast.makeText(
                        context, "Couldn't open a camera app on this phone.", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onPickPhoto = {
                if (!vm.aiEnabled()) {
                    android.widget.Toast.makeText(
                        context, "Photo logging needs a Gemini key. Setup tab > AI food detection has steps to get one.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@FoodSearchDialog
                }
                galleryLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onManual = { showSearch = false; showAddDialog = true },
            onDismiss = { showSearch = false; vm.resetSearch() }
        )
    }

    pendingPortion?.let { food ->
        QuantityDialog(
            food = food,
            portionOptions = portionOptions,
            onDismiss = { vm.choosePortion(null) },
            onConfirm = { scaled ->
                vm.choosePortion(null)
                showSearch = false
                vm.assess(scaled, activeMeal)
            }
        )
    }

    when (val s = scanState) {
        is TodayViewModel.ScanState.Working ->
            ScanStatusDialog(s.message) { vm.clearScan() }
        is TodayViewModel.ScanState.Message ->
            ScanStatusDialog(s.text) { vm.clearScan() }
        is TodayViewModel.ScanState.Choose ->
            ScanResultPicker(
                foods = s.foods,
                onPick = { vm.clearScan(); vm.choosePortion(it) },
                onDismiss = { vm.clearScan() }
            )
        is TodayViewModel.ScanState.Assess ->
            VerdictDialog(
                food = s.food,
                verdict = s.verdict,
                mealSlot = activeMeal,
                isSafeFood = com.evyr.rads.data.local.SafeFood.keyFor(s.food.name) in safeKeys,
                onToggleSafe = { vm.toggleSafe(s.food) },
                onDismiss = { vm.clearScan() },
                onConfirm = { vm.commitScanned(it, activeMeal) },
                simple = (profile ?: com.evyr.rads.data.local.UserProfile()).homeLayout().simple,
                onSuggestSwaps = { vm.suggestSwapsFor(s.food, s.verdict) }
            )
        else -> Unit
    }
}

private fun readBytes(context: Context, uri: android.net.Uri): ByteArray? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()


/**
 * Full-size photos can be several MB. Menu text reads fine at about 2000 px on the
 * long side, so larger pictures are scaled down before they are sent.
 */
private fun shrinkForUpload(bytes: ByteArray, maxSide: Int = 2000): ByteArray = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    if (longSide <= maxSide) return@runCatching bytes
    var sample = 1
    while (longSide / (sample * 2) >= maxSide) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@runCatching bytes
    // Re-encoding drops the EXIF rotation tag, so apply it to the pixels first or the menu arrives sideways.
    val degrees = when (
        android.media.ExifInterface(java.io.ByteArrayInputStream(bytes)).getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL
        )
    ) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val bmp = if (degrees == 0f) decoded else android.graphics.Bitmap.createBitmap(
        decoded, 0, 0, decoded.width, decoded.height,
        android.graphics.Matrix().apply { postRotate(degrees) }, true
    )
    val out = java.io.ByteArrayOutputStream()
    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
    out.toByteArray()
}.getOrDefault(bytes)
