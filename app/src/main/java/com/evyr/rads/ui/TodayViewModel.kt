package com.evyr.rads.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evyr.rads.data.FoodPortion
import com.evyr.rads.data.ScannedFood
import com.evyr.rads.data.SecureStore
import com.evyr.rads.data.Verdict
import com.evyr.rads.data.VerdictResult
import com.evyr.rads.data.TriggerDetector
import com.evyr.rads.data.VerdictRules
import com.evyr.rads.data.local.SafeFood
import com.evyr.rads.data.local.DatabaseProvider
import com.evyr.rads.data.remote.GeminiVision
import com.evyr.rads.data.remote.OpenFoodFacts
import com.evyr.rads.data.remote.UsdaFoodSearch
import com.evyr.rads.data.local.FoodLogEntry
import com.evyr.rads.data.local.HealthSnapshot
import com.evyr.rads.data.local.DEFAULT_FAT_WARN_GRAMS
import com.evyr.rads.data.local.UserProfile
import com.evyr.rads.health.HealthConnectManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val db = DatabaseProvider.get(app)
    private val foodDao = db.foodLogDao()
    private val healthDao = db.healthSnapshotDao()
    private val profileDao = db.userProfileDao()
    private val health = HealthConnectManager(app)

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate get() = LocalDate.now(zone)
    private val dayKey: Long get() = today.toEpochDay()

    /** The day being viewed. Follows the real date unless the user paged back. */
    private val _viewDate = MutableStateFlow(LocalDate.now(zone))
    val viewDate: StateFlow<LocalDate> = _viewDate.asStateFlow()

    /** True while the view should roll over with the clock at midnight. */
    private var followToday = true

    val isViewingToday: StateFlow<Boolean> =
        _viewDate.map { it == LocalDate.now(zone) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private fun startOf(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun endOf(d: LocalDate) =
        d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<FoodLogEntry>> =
        _viewDate.flatMapLatest { d -> foodDao.getEntriesForDay(startOf(d), endOf(d)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- History: trends on Stats, recents in the add sheet ----

    /** The real date, for history windows. Rolls over in refreshDate(). */
    private val _today = MutableStateFlow(LocalDate.now(zone))

    /** Last 90 days of entries, newest first. One query feeds trends and recents. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val recentHistory: StateFlow<List<FoodLogEntry>> =
        _today.flatMapLatest { d -> foodDao.getEntriesSince(startOf(d.minusDays(89))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Last 30 days, for the Stats trend view. */
    val last30Days: StateFlow<List<FoodLogEntry>> =
        recentHistory.map { list ->
            val cutoff = startOf(_today.value.minusDays(29))
            list.filter { it.timestamp >= cutoff }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The foods logged most often in the last 90 days (ties broken by most
     * recent), each as it was last logged. Shown in the add sheet before you
     * type anything, since most people eat the same handful of things.
     */
    val frequentFoods: StateFlow<List<ScannedFood>> =
        recentHistory.map { list ->
            list.groupBy { it.name.trim().lowercase() }
                .values
                .sortedWith(
                    compareByDescending<List<FoodLogEntry>> { it.size }
                        .thenByDescending { it.first().timestamp }
                )
                .take(8)
                .map { group ->
                    val latest = group.first() // list is newest-first
                    latest.toScannedFood(
                        if (group.size > 1) "Logged ${group.size} times. Amounts as you logged it last."
                        else "Amounts as you logged it last time."
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Safe foods ----

    private val safeDao = db.safeFoodDao()

    val safeFoods: StateFlow<List<SafeFood>> =
        safeDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val safeKeys: StateFlow<Set<String>> =
        safeFoods.map { list -> list.map { it.foodKey }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun isSafe(name: String): Boolean = SafeFood.keyFor(name) in safeKeys.value

    /** Add or remove a logged entry from the safe list. */
    fun toggleSafe(entry: FoodLogEntry) {
        viewModelScope.launch {
            val key = SafeFood.keyFor(entry.name)
            if (key in safeKeys.value) safeDao.delete(key)
            else safeDao.upsert(
                SafeFood(
                    foodKey = key, name = entry.name.trim(), calories = entry.calories,
                    fatGrams = entry.fatGrams, proteinGrams = entry.proteinGrams, carbGrams = entry.carbGrams,
                    saturatedFatGrams = entry.saturatedFatGrams, sugarGrams = entry.sugarGrams,
                    fiberGrams = entry.fiberGrams, sodiumMg = entry.sodiumMg,
                    triggers = entry.triggers, source = entry.source,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Add or remove a candidate (from search or scan) from the safe list. */
    fun toggleSafe(food: ScannedFood) {
        viewModelScope.launch {
            val key = SafeFood.keyFor(food.name)
            if (key in safeKeys.value) safeDao.delete(key)
            else safeDao.upsert(
                SafeFood(
                    foodKey = key, name = food.name.trim(), calories = food.calories,
                    fatGrams = food.fatGrams, proteinGrams = food.proteinGrams, carbGrams = food.carbGrams,
                    saturatedFatGrams = food.saturatedFatGrams, sugarGrams = food.sugarGrams,
                    fiberGrams = food.fiberGrams, sodiumMg = food.sodiumMg,
                    triggers = TriggerDetector.detect(food.name, food.ingredients, food.tags)
                        .joinToString(",") { it.key }.ifBlank { null },
                    source = food.source,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeSafe(foodKey: String) {
        viewModelScope.launch { safeDao.delete(foodKey) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val healthToday: StateFlow<HealthSnapshot?> =
        _viewDate.flatMapLatest { d -> healthDao.observeDay(d.toEpochDay()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Called when the app comes back to the foreground. If the clock has
     * crossed midnight, roll the view onto the new day; yesterday stays in
     * the database and is reachable with the day arrows.
     */
    fun refreshDate() {
        val now = LocalDate.now(zone)
        if (_today.value != now) _today.value = now
        if (followToday && _viewDate.value != now) {
            _viewDate.value = now
            selectEntry(null)
        }
        // Steps and exercise keep climbing all day, so re-read on every
        // return to the app rather than trusting the value from launch.
        if (followToday) {
            viewModelScope.launch { syncQuietly() }
        }
    }

    /** The meal slot that matches the current time of day. */
    fun mealSlotForNow(): String {
        val h = java.time.LocalTime.now(zone).hour
        return when {
            h < 10 -> "breakfast"
            h < 15 -> "lunch"
            h < 21 -> "dinner"
            else -> "snack"
        }
    }

    fun previousDay() {
        followToday = false
        _viewDate.value = _viewDate.value.minusDays(1)
        selectEntry(null)
    }

    fun nextDay() {
        val next = _viewDate.value.plusDays(1)
        if (next.isAfter(LocalDate.now(zone))) return
        _viewDate.value = next
        followToday = next == LocalDate.now(zone)
        selectEntry(null)
    }

    fun jumpToToday() {
        followToday = true
        _viewDate.value = LocalDate.now(zone)
        selectEntry(null)
    }

    val profile: StateFlow<UserProfile?> =
        profileDao.observe()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _profileLoaded = MutableStateFlow(false)
    val profileLoaded: StateFlow<Boolean> = _profileLoaded.asStateFlow()

    private val _selectedEntryId = MutableStateFlow<Long?>(null)
    val selectedEntryId: StateFlow<Long?> = _selectedEntryId.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    enum class SyncStatus { IDLE, SYNCING, OK, UNAVAILABLE, NO_PERMISSION, ERROR }

    // ---- Scanning ----

    sealed class ScanState {
        object Idle : ScanState()
        data class Working(val message: String) : ScanState()
        data class Message(val text: String) : ScanState()
        data class Choose(val foods: List<ScannedFood>) : ScanState()
        data class Assess(val food: ScannedFood, val verdict: VerdictResult) : ScanState()
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    fun clearScan() { _scanState.value = ScanState.Idle }

    // ---- Food search ----

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ScannedFood>>(emptyList())
    val searchResults: StateFlow<List<ScannedFood>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _searchMessage = MutableStateFlow<String?>(null)
    val searchMessage: StateFlow<String?> = _searchMessage.asStateFlow()

    private val _pendingPortion = MutableStateFlow<ScannedFood?>(null)
    val pendingPortion: StateFlow<ScannedFood?> = _pendingPortion.asStateFlow()

    /** Published portion options for the pending item, when the source has them. */
    private val _portionOptions = MutableStateFlow<List<FoodPortion>>(emptyList())
    val portionOptions: StateFlow<List<FoodPortion>> = _portionOptions.asStateFlow()

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun choosePortion(food: ScannedFood?) {
        _pendingPortion.value = food
        _portionOptions.value = emptyList()
        // Only worth a lookup when we don't already have a per-serving figure.
        val id = food?.sourceId
        if (food != null && food.basisGrams != null && id != null) {
            viewModelScope.launch {
                val ctx = getApplication<Application>()
                val found = UsdaFoodSearch.portions(id, SecureStore.usdaKey(ctx))
                if (_pendingPortion.value?.sourceId == id) {
                    _portionOptions.value = found
                }
            }
        }
    }

    fun resetSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _searchMessage.value = null
        _searching.value = false
        _pendingPortion.value = null
        _portionOptions.value = emptyList()
    }

    /**
     * One search box for everything. Digits only (6-14 of them) are treated
     * as a barcode and checked against Open Food Facts and USDA's branded
     * foods at once; anything else is a name search across both databases
     * in parallel, USDA first, with an AI estimate as the last resort when
     * neither has it.
     */
    fun runSearch() {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _searching.value = true
            _searchResults.value = emptyList()
            val ctx = getApplication<Application>()

            val isBarcode = q.length in 6..14 && q.all { it.isDigit() }
            if (isBarcode) {
                _searchMessage.value = "Looking up barcode $q..."
                val offJob = async { runCatching { OpenFoodFacts.lookup(q) }.getOrNull() }
                val usdaJob = async { UsdaFoodSearch.search(q, SecureStore.usdaKey(ctx)) }
                val off = offJob.await()
                val usda = usdaJob.await()

                val wanted = q.trimStart('0')
                val offFoods = (off as? OpenFoodFacts.Result.Found)?.let { listOf(it.food) }
                    ?: emptyList()
                val usdaFoods = (usda as? UsdaFoodSearch.Result.Found)?.foods
                    ?.filter { it.barcode?.trimStart('0') == wanted }
                    ?: emptyList()
                val merged = offFoods + usdaFoods

                _searching.value = false
                if (merged.isEmpty()) {
                    _searchMessage.value = (off as? OpenFoodFacts.Result.Failed)?.message
                        ?: "Barcode $q isn't in USDA or Open Food Facts.\n\nTry searching the product name, or enter it manually."
                } else {
                    _searchMessage.value = null
                    _searchResults.value = merged
                    // A single exact hit goes straight to the portion step.
                    if (merged.size == 1) choosePortion(merged.first())
                }
                return@launch
            }

            _searchMessage.value = "Searching USDA and Open Food Facts..."
            val usdaJob = async { UsdaFoodSearch.search(q, SecureStore.usdaKey(ctx)) }
            val offJob = async { runCatching { OpenFoodFacts.search(q) }.getOrDefault(emptyList()) }
            val usda = usdaJob.await()
            val off = offJob.await()

            var usdaError: String? = null
            val merged = when (usda) {
                is UsdaFoodSearch.Result.Found -> usda.foods + off
                is UsdaFoodSearch.Result.Empty -> off
                is UsdaFoodSearch.Result.Failed -> {
                    usdaError = usda.message
                    off
                }
            }

            if (merged.isNotEmpty()) {
                _searchResults.value = merged
                _searchMessage.value = null
                _searching.value = false
                return@launch
            }

            // Neither database has it. Fall back to an AI estimate, which is
            // where chain-restaurant items usually land.
            val key = SecureStore.geminiKey(ctx)
            if (key.isBlank()) {
                _searchMessage.value = usdaError
                    ?: "No matches for \"$q\".\n\nAdd a Gemini key in Setup to estimate foods the databases don't carry, or enter it manually."
                _searching.value = false
                return@launch
            }

            _searchMessage.value = "Not in either database. Estimating with AI..."
            when (val ai = GeminiVision.estimateFromText(key, SecureStore.geminiModel(ctx), q)) {
                is GeminiVision.Result.Found -> {
                    _searchResults.value = ai.foods
                    _searchMessage.value = null
                }
                is GeminiVision.Result.ModelRetired -> {
                    SecureStore.setGeminiModel(ctx, ai.suggested)
                    when (val retry =
                        GeminiVision.estimateFromText(key, ai.suggested, q)) {
                        is GeminiVision.Result.Found -> {
                            _searchResults.value = retry.foods
                            _searchMessage.value = null
                        }
                        else -> _searchMessage.value =
                            "No matches for \"$q\". Try entering it manually."
                    }
                }
                is GeminiVision.Result.Failed ->
                    _searchMessage.value = usdaError
                        ?: "No matches for \"$q\".\n\n${ai.message}"
            }
            _searching.value = false
        }
    }

    fun aiEnabled(): Boolean = SecureStore.hasGeminiKey(getApplication<Application>())

    /** A scanned barcode runs through the same search as a typed one. */
    fun onBarcodeScanned(barcode: String) {
        _searchQuery.value = barcode
        runSearch()
    }

    /** Photo results land in the same results list as a search. */
    fun onPhotoCaptured(bytes: ByteArray) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            _searchQuery.value = ""
            _searchResults.value = emptyList()

            val key = SecureStore.geminiKey(ctx)
            if (key.isBlank()) {
                _searchMessage.value = "Add a Gemini key in Setup to log from a photo."
                return@launch
            }

            _searching.value = true
            _searchMessage.value = "Reading your photo..."

            var result = GeminiVision.analyze(key, SecureStore.geminiModel(ctx), bytes)

            // Google retired the saved model: adopt the suggested one and retry once.
            if (result is GeminiVision.Result.ModelRetired) {
                val suggested = result.suggested
                SecureStore.setGeminiModel(ctx, suggested)
                result = GeminiVision.analyze(key, suggested, bytes)
            }

            when (val r = result) {
                is GeminiVision.Result.Found -> {
                    _searchResults.value = r.foods
                    _searchMessage.value = null
                    if (r.foods.size == 1) choosePortion(r.foods.first())
                }
                is GeminiVision.Result.Failed ->
                    _searchMessage.value = r.message
                is GeminiVision.Result.ModelRetired ->
                    _searchMessage.value =
                        "The AI model name is out of date. Set it to \"${r.suggested}\" in Setup."
            }
            _searching.value = false
        }
    }

    /** Judge a candidate against what's already in the target meal. */
    fun assess(food: ScannedFood, mealSlot: String? = null) {
        viewModelScope.launch {
            val slot = mealSlot ?: activeMealSlot
            _scanState.value = ScanState.Assess(food, evaluateFor(food, slot))
        }
    }

    /** Fat and condition checks against everything already in this meal. */
    private suspend fun evaluateFor(food: ScannedFood, mealSlot: String): VerdictResult {
        val profile = profileDao.get()
        return VerdictRules.evaluate(
            food = food,
            mealEntries = entries.value.filter { it.mealSlot == mealSlot },
            fatLimitPerMeal = profile?.fatWarnGramsPerMeal ?: DEFAULT_FAT_WARN_GRAMS,
            conditions = profile?.conditionSet() ?: emptySet(),
            allergens = profile?.allergenSet() ?: emptySet(),
            customAllergens = com.evyr.rads.data.CustomAllergen.parseCsv(profile?.customAllergens),
            limits = profile?.limits() ?: com.evyr.rads.data.Limits()
        )
    }

    /** The meal slot the UI is currently showing, so scans land in the right place. */
    var activeMealSlot: String = "breakfast"

    fun commitScanned(food: ScannedFood, mealSlot: String) {
        viewModelScope.launch {
            insertEvaluated(food, mealSlot)
            _scanState.value = ScanState.Idle
        }
    }

    private suspend fun insertEvaluated(food: ScannedFood, mealSlot: String): FoodLogEntry {
        val v = evaluateFor(food, mealSlot)
        val entry =
            FoodLogEntry(
                timestamp = timestampForViewedDay(),
                mealSlot = mealSlot,
                name = food.name,
                calories = food.calories,
                fatGrams = food.fatGrams,
                proteinGrams = food.proteinGrams,
                carbGrams = food.carbGrams,
                source = food.source,
                flagged = v.verdict == Verdict.OVER_LIMIT,
                flagReason = v.headline.takeIf { v.verdict != Verdict.PASS },
                saturatedFatGrams = food.saturatedFatGrams,
                sugarGrams = food.sugarGrams,
                fiberGrams = food.fiberGrams,
                sodiumMg = food.sodiumMg,
                triggers = v.triggers.joinToString(",") { it.key }.ifBlank { null }
            )
        return entry.copy(id = foodDao.insert(entry))
    }

    private val _quickLogged = MutableSharedFlow<FoodLogEntry>(extraBufferCapacity = 8)

    /** Fires after a one-tap log so the screen can offer Undo. */
    val quickLogged: SharedFlow<FoodLogEntry> = _quickLogged.asSharedFlow()

    /**
     * One-tap log of a remembered food at the amount it was last logged.
     * Only a clean pass is logged straight away. Anything that needs a second
     * look (caution, over a limit, an allergy match) opens the full verdict
     * so a one-tap shortcut can never skip a warning.
     */
    fun quickLog(food: ScannedFood, mealSlot: String) {
        viewModelScope.launch {
            val v = evaluateFor(food, mealSlot)
            if (v.verdict != Verdict.PASS || v.allergyHits.isNotEmpty()) {
                _scanState.value = ScanState.Assess(food, v)
                return@launch
            }
            _quickLogged.emit(insertEvaluated(food, mealSlot))
        }
    }

    init {
        // Mark profile state as resolved once we've checked the DB at least once,
        // so onboarding doesn't flash before the real profile loads.
        viewModelScope.launch {
            profileDao.get()
            _profileLoaded.value = true
        }
        // Opportunistic sync on launch; silently no-ops without permission.
        viewModelScope.launch { syncQuietly() }
    }

    fun selectEntry(id: Long?) {
        _selectedEntryId.value = if (_selectedEntryId.value == id) null else id
    }

    fun saveProfile(updated: UserProfile) {
        viewModelScope.launch { profileDao.upsert(updated.copy(id = 1)) }
    }

    fun completeOnboarding(p: UserProfile) {
        viewModelScope.launch {
            profileDao.upsert(p.copy(id = 1, onboarded = true))
        }
    }

    fun addEntry(
        name: String,
        mealSlot: String,
        calories: Int,
        fatGrams: Double,
        proteinGrams: Double,
        carbGrams: Double
    ) {
        viewModelScope.launch {
            insertEvaluated(
                ScannedFood(
                    name = name,
                    calories = calories,
                    fatGrams = fatGrams,
                    proteinGrams = proteinGrams,
                    carbGrams = carbGrams,
                    source = "manual"
                ),
                mealSlot
            )
        }
    }

    /** Now if viewing today, otherwise midday of the day being viewed. */
    private fun timestampForViewedDay(): Long {
        val d = _viewDate.value
        return if (d == LocalDate.now(zone)) System.currentTimeMillis()
        else d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    }

    fun deleteEntry(entry: FoodLogEntry) {
        viewModelScope.launch {
            if (_selectedEntryId.value == entry.id) _selectedEntryId.value = null
            foodDao.delete(entry)
        }
    }

    fun sync() {
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.SYNCING
            _syncStatus.value = runSync()
        }
    }

    private suspend fun syncQuietly() {
        val result = runSync()
        if (result == SyncStatus.OK) _syncStatus.value = SyncStatus.OK
    }

    private suspend fun runSync(): SyncStatus {
        return try {
            if (!health.isAvailable()) return SyncStatus.UNAVAILABLE
            if (!health.hasAllPermissions()) return SyncStatus.NO_PERMISSION
            val daily = health.readToday()
            healthDao.upsert(
                HealthSnapshot(
                    dayKey = dayKey,
                    steps = daily.steps,
                    weightKg = daily.weightKg,
                    exerciseMinutes = daily.exerciseMinutes,
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
            SyncStatus.OK
        } catch (e: Exception) {
            SyncStatus.ERROR
        }
    }

    private fun parseTags(csv: String?): Set<String> =
        csv.orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()

    private fun FoodLogEntry.toScannedFood(note: String?) = ScannedFood(
        name = name, calories = calories, fatGrams = fatGrams,
        proteinGrams = proteinGrams, carbGrams = carbGrams,
        saturatedFatGrams = saturatedFatGrams, sugarGrams = sugarGrams,
        fiberGrams = fiberGrams, sodiumMg = sodiumMg,
        tags = parseTags(triggers), servingNote = note, source = source
    )

    /** A safe-list item as a pickable candidate for the add sheet. */
    fun safeAsScanned(food: SafeFood) = ScannedFood(
        name = food.name, calories = food.calories, fatGrams = food.fatGrams,
        proteinGrams = food.proteinGrams, carbGrams = food.carbGrams,
        saturatedFatGrams = food.saturatedFatGrams, sugarGrams = food.sugarGrams,
        fiberGrams = food.fiberGrams, sodiumMg = food.sodiumMg,
        tags = parseTags(food.triggers), servingNote = "On your safe list.", source = food.source
    )
}
