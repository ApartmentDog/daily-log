package com.evyr.rads.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.evyr.rads.BuildConfig
import com.evyr.rads.data.Allergen
import com.evyr.rads.data.Condition
import com.evyr.rads.data.Limits
import com.evyr.rads.data.CustomAllergen
import com.evyr.rads.data.SecureStore
import com.evyr.rads.data.Units
import com.evyr.rads.data.local.DetailLevel
import com.evyr.rads.data.local.Focus
import com.evyr.rads.data.local.UserProfile
import com.evyr.rads.ui.theme.*

/**
 * Everything the user types lives in a local draft. Nothing touches the
 * database until SAVE, so typing can't be eaten by an async round trip and a
 * field can be cleared and retyped without snapping back.
 */
private data class SetupDraft(
    val imperial: Boolean,
    val name: String,
    val age: String,
    val sex: String,
    val feet: String,
    val inches: String,
    val heightCm: String,
    val weight: String,
    val goal: String,
    val goalWeight: String,
    val rate: String,
    val activity: String,
    val fatLimit: String,
    /** Typed per-meal limits by label; blank means "use the default". */
    val limits: Map<String, String>,
    val conditions: Set<Condition>,
    val allergens: Set<Allergen>,
    val customAllergensText: String,
    val focus: Set<Focus>,
    val detailLevel: String
) {
    companion object {
        fun from(p: UserProfile): SetupDraft {
            val (ft, inch) = Units.cmToFeetInches(p.heightCm)
            return SetupDraft(
                imperial = p.useImperial,
                name = p.name,
                age = if (p.age > 0) p.age.toString() else "",
                sex = p.sex,
                feet = if (p.heightCm > 0) ft.toString() else "",
                inches = if (p.heightCm > 0) inch.toString() else "",
                heightCm = if (p.heightCm > 0) trim(p.heightCm) else "",
                weight = Units.displayWeight(p.weightKg, p.useImperial),
                goal = p.goal,
                goalWeight = Units.displayWeight(p.goalWeightKg, p.useImperial),
                rate = trim(p.rateLbsPerWeek),
                activity = p.activityLevel,
                fatLimit = trim(p.fatWarnGramsPerMeal),
                limits = p.limits().overrides.mapValues { trim(it.value) },
                conditions = p.conditionSet(),
                allergens = p.allergenSet(),
                customAllergensText = CustomAllergen.parseCsv(p.customAllergens).joinToString(", ") { it.label },
                focus = p.focusSet(),
                detailLevel = DetailLevel.fromKey(p.detailLevel).key
            )
        }
    }

    private fun heightCmValue(): Double? {
        if (!imperial) {
            return if (heightCm.isBlank()) 0.0 else heightCm.trim().toDoubleOrNull()
        }
        val f = if (feet.isBlank()) 0 else feet.trim().toIntOrNull() ?: return null
        val i = if (inches.isBlank()) 0 else inches.trim().toIntOrNull() ?: return null
        if (i !in 0..11) return null
        return Units.feetInchesToCm(f, i)
    }

    private fun weightKgValue(text: String): Double? =
        if (text.isBlank()) 0.0 else Units.parseWeightToKg(text.trim(), imperial)

    /**
     * Builds the profile to save, or an error explaining which field is wrong.
     * Fields left as they were keep their stored value exactly, so unit
     * rounding (180 cm -> 5'11" -> 180.34 cm) never drifts untouched data.
     */
    fun toProfile(base: UserProfile, baseline: SetupDraft): Pair<UserProfile?, String?> {
        val ageV = if (age.isBlank()) 0 else age.trim().toIntOrNull()
            ?: return null to "Age must be a whole number."
        val heightV = heightCmValue()
            ?: return null to if (imperial) "Height: whole feet, inches 0-11." else "Height must be a number."
        val weightV = weightKgValue(weight) ?: return null to "Weight must be a number."
        val goalV = weightKgValue(goalWeight) ?: return null to "Target weight must be a number."
        val rateV = rate.trim().toDoubleOrNull() ?: return null to "Pick a rate."
        val fatV = fatLimit.trim().toDoubleOrNull()
            ?: return null to "Fat limit must be a number."
        if (fatV <= 0) return null to "Fat limit must be above zero."
        val limitMap = mutableMapOf<String, Double>()
        for (item in Limits.ITEMS) {
            val text = limits[item.label].orEmpty().trim()
            if (text.isBlank()) continue
            val v = text.toDoubleOrNull()
            if (v == null || v <= 0) return null to "${item.title} limit must be a number above zero, or blank for the default."
            if (v != item.default) limitMap[item.label] = v
        }

        val sameUnits = imperial == baseline.imperial
        val heightUntouched = sameUnits && feet == baseline.feet &&
            inches == baseline.inches && heightCm == baseline.heightCm
        val weightUntouched = sameUnits && weight == baseline.weight
        val goalUntouched = sameUnits && goalWeight == baseline.goalWeight

        return base.copy(
            useImperial = imperial,
            name = name.trim(),
            age = ageV,
            sex = sex,
            heightCm = if (heightUntouched) base.heightCm else heightV,
            weightKg = if (weightUntouched) base.weightKg else weightV,
            goal = goal,
            goalWeightKg = if (goalUntouched) base.goalWeightKg else goalV,
            rateLbsPerWeek = rateV,
            activityLevel = activity,
            fatWarnGramsPerMeal = fatV,
            // Typing a different number means the user chose it, not the default.
            fatLimitFromDoctor = base.fatLimitFromDoctor || fatV != base.fatWarnGramsPerMeal,
            limitOverrides = Limits(limitMap).toCsv(),
            focus = Focus.toCsv(focus),
            detailLevel = detailLevel,
            conditions = Condition.toCsv(conditions),
            allergens = Allergen.toCsv(allergens),
            customAllergens = CustomAllergen.toCsv(
                customAllergensText.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { CustomAllergen(it) }
            )
        ) to null
    }

    /** Switching units converts what's typed, rather than reinterpreting it. */
    fun withUnits(toImperial: Boolean): SetupDraft {
        if (toImperial == imperial) return this
        val hCm = heightCmValue()
        val wKg = weightKgValue(weight)
        val gKg = weightKgValue(goalWeight)
        val (ft, inch) = Units.cmToFeetInches(hCm ?: 0.0)
        return copy(
            imperial = toImperial,
            feet = if ((hCm ?: 0.0) > 0) ft.toString() else feet,
            inches = if ((hCm ?: 0.0) > 0) inch.toString() else inches,
            heightCm = if ((hCm ?: 0.0) > 0) trim(hCm!!) else heightCm,
            weight = wKg?.let { Units.displayWeight(it, toImperial) } ?: weight,
            goalWeight = gKg?.let { Units.displayWeight(it, toImperial) } ?: goalWeight
        )
    }
}

@Composable
fun TabSetupView(
    scrollState: ScrollState,
    profile: UserProfile?,
    onUpdate: (UserProfile) -> Unit
) {
    // Seed the draft once when the profile first arrives; never overwrite
    // it from the database while the user is editing.
    var draft by remember { mutableStateOf<SetupDraft?>(null) }
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(profile) {
        if (draft == null && profile != null) draft = SetupDraft.from(profile)
    }

    val d = draft
    if (profile == null || d == null) {
        Text(
            "Loading your profile...",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
        return
    }

    fun edit(change: (SetupDraft) -> SetupDraft) {
        draft = change(draft ?: d)
        justSaved = false
    }

    // Compare on-screen text, not converted numbers, so opening the tab
    // doesn't show phantom unsaved changes from rounding.
    val baseline = SetupDraft.from(profile)
    val (candidate, error) = d.toProfile(profile, baseline)
    val dirty = d != baseline && candidate != null
    val preview = candidate ?: profile
    val imp = d.imperial
    val wLabel = if (imp) "lb" else "kg"

    val save = {
        candidate?.let {
            onUpdate(it)
            draft = SetupDraft.from(it)
            justSaved = true
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {

        SectionLabel("Units")
        ChoiceRow("Measurement", listOf("imperial", "metric"),
            if (imp) "imperial" else "metric") { choice ->
            edit { it.withUnits(choice == "imperial") }
        }

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("About you")
        EditField("Name", d.name) { v -> edit { it.copy(name = v) } }
        EditNumber("Age", d.age) { v -> edit { it.copy(age = v) } }
        ChoiceRow("Sex", listOf("male", "female", "unspecified"), d.sex) { v ->
            edit { it.copy(sex = v) }
        }

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("Body")
        if (imp) {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    EditNumber("Height ft", d.feet) { v -> edit { it.copy(feet = v) } }
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    EditNumber("in", d.inches) { v -> edit { it.copy(inches = v) } }
                }
            }
        } else {
            EditNumber("Height cm", d.heightCm) { v -> edit { it.copy(heightCm = v) } }
        }
        EditNumber("Weight $wLabel", d.weight) { v -> edit { it.copy(weight = v) } }

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("Goal")
        ChoiceRow("Goal", listOf("lose", "maintain", "gain"), d.goal) { v ->
            edit { it.copy(goal = v) }
        }
        if (d.goal != "maintain") {
            EditNumber("Target $wLabel", d.goalWeight) { v -> edit { it.copy(goalWeight = v) } }
            ChoiceRow("Rate lb/wk", listOf("0.5", "1", "1.5", "2"), d.rate) { v ->
                edit { it.copy(rate = v) }
            }
            preview.poundsToGoal()?.let { lbs ->
                StatRow("To goal", "${trim(kotlin.math.abs(lbs))} lb")
            }
            preview.weeksToGoal()?.let { wk ->
                StatRow("ETA", if (wk == 0) "At goal" else "$wk weeks")
            }
        }
        ChoiceRow(
            "Activity",
            listOf("sedentary", "light", "moderate", "active", "very_active"),
            d.activity
        ) { v -> edit { it.copy(activity = v) } }

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("Home screen")
        Text(
            "What you want help with decides what the Log screen shows.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Focus.values().forEach { f ->
            val on = f in d.focus
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { edit { it.copy(focus = if (on) it.focus - f else it.focus + f) } }
                    .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    f.title,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    f.blurb,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        ChoiceRow(
            "Detail",
            listOf(DetailLevel.SIMPLE.key, DetailLevel.DETAILED.key),
            d.detailLevel
        ) { v -> edit { it.copy(detailLevel = v) } }

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("Fat limit (per meal)")
        Text(
            "Applied to each meal separately, not a daily budget.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        EditNumber("Grams", d.fatLimit) { v -> edit { it.copy(fatLimit = v) } }

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("Other limits (per meal)")
        Text(
            "Only used for the conditions you ticked below. Leave blank for the general starting point; " +
                "if your doctor or dietitian gave you a number, enter it. Amber appears a bit before the limit.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Limits.ITEMS.forEach { item ->
            EditNumber("${item.title} (${item.unit}), default ${trim(item.default)}", d.limits[item.label].orEmpty()) { v ->
                edit { it.copy(limits = it.limits + (item.label to v)) }
            }
        }

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("Health conditions")
        Text(
            "Tick any that apply. Foods get flagged for them before you log. " +
                "General guidance only -- your doctor's instructions override these.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Condition.values().forEach { c ->
            val on = c in d.conditions
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        edit {
                            it.copy(conditions = if (on) it.conditions - c else it.conditions + c)
                        }
                    }
                    .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    c.label,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Watches: " + c.watches,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("Allergies")
        Text(
            "Tick any that apply. A match in a food's name or ingredients gets flagged " +
                "before you log it -- these are always treated as a hard stop, not a caution. " +
                "This is not a substitute for reading labels or checking with a restaurant -- " +
                "it catches what's written in the food's name, not what touched it in the kitchen.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Allergen.values().forEach { a ->
            val on = a in d.allergens
            Text(
                a.label,
                fontSize = 14.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        edit {
                            it.copy(allergens = if (on) it.allergens - a else it.allergens + a)
                        }
                    }
                    .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Other allergies (comma-separated)",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
        EditField("e.g. mango, MSG", d.customAllergensText) { v ->
            edit { it.copy(customAllergensText = v) }
        }

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel(if (dirty) "Computed (unsaved)" else "Computed")
        StatRow("BMR", preview.bmr().toInt().toString())
        StatRow("TDEE", preview.tdee().toInt().toString())
        StatRow(
            "Adjust",
            preview.dailyAdjustment().let { if (it >= 0) "+$it" else "$it" } + " kcal"
        )
        StatRow("Target", "${preview.calorieTarget()} kcal", emphasize = true)

        // ---- Device settings: these save immediately, they aren't profile data ----
        Spacer(Modifier.height(4.dp))
        Hairline()
        val bootCtx = LocalContext.current
        var keyError by remember { mutableStateOf<String?>(null) }

        SectionLabel("Food database")
        Text(
            "USDA key. Blank uses a shared demo key that rate limits quickly. Saves as you type.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        var usdaKey by remember { mutableStateOf(SecureStore.usdaKey(bootCtx)) }
        EditField("USDA key", usdaKey) { typed ->
            usdaKey = typed
            SecureStore.setUsdaKey(bootCtx, typed)
            // Read back rather than trust the local var, so a save that
            // silently failed shows up here instead of only on next visit.
            val confirmed = SecureStore.usdaKey(bootCtx)
            keyError = if (confirmed != typed.trim()) "Couldn't save the USDA key. Try again." else null
        }
        StatRow("DB key", if (usdaKey.isNotBlank()) "Set" else "Demo (limited)")

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("AI food detection")
        Text(
            "Photo logging and fallback estimates use Google's Gemini. You need your own key, " +
                "and it saves as you type.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        Text(
            "How to get one:\n" +
                "1. Tap here to open Google AI Studio and sign in with a Google account.\n" +
                "2. Tap \"Create API key\".\n" +
                "3. Copy the key and paste it below.\n" +
                "Usage may be billed to your Google account, depending on your plan.",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .clickable { runCatching { uriHandler.openUri("https://aistudio.google.com/apikey") } }
        )
        var apiKey by remember { mutableStateOf(SecureStore.geminiKey(bootCtx)) }
        var model by remember { mutableStateOf(SecureStore.geminiModel(bootCtx)) }
        EditField("API key", apiKey) { typed ->
            apiKey = typed
            SecureStore.setGeminiKey(bootCtx, typed)
            val confirmed = SecureStore.geminiKey(bootCtx)
            keyError = if (confirmed != typed.trim()) "Couldn't save the API key. Try again." else null
        }
        EditField("Model", model) {
            model = it
            SecureStore.setGeminiModel(bootCtx, it)
        }
        StatRow("AI status", if (apiKey.isNotBlank()) "Enabled" else "Off")
        keyError?.let {
            Text(
                it,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("Safety and privacy")
        val onSurfaceSoft = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        listOf(
            "R.A.D.S. gives general food guidance, not medical advice. It can be wrong. Your doctor's or dietitian's instructions always come first.",
            "Each food is checked against the conditions and limits you set. Default limits are general starting points, not personalized advice.",
            "Food data comes from USDA FoodData Central and Open Food Facts (community-edited, so it can contain errors), plus AI estimates from Google Gemini for photos. AI estimates are labelled and are never shown as a green light.",
            "Allergy checks only catch what is written in a food's name or ingredients. They cannot detect cross-contact, so keep reading labels and asking restaurants.",
            "Your profile and log stay on this phone. Searches go to USDA and Open Food Facts. Photos and some food text go to Google Gemini with your own key."
        ).forEach {
            Text(it, fontSize = 12.sp, color = onSurfaceSoft, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            "Read the full privacy policy",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.accessibleClick("Open the privacy policy") {
                runCatching {
                    uriHandler.openUri("https://github.com/ApartmentDog/daily-log/blob/main/PRIVACY.md")
                }
            }
        )

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("About")
        StatRow("Version", BuildConfig.VERSION_NAME, emphasize = true)
        StatRow("Build", BuildConfig.VERSION_CODE.toString())
        StatRow("Package", BuildConfig.APPLICATION_ID)
        StatRow("AI model", SecureStore.geminiModel(bootCtx))

        Spacer(Modifier.height(16.dp))
        Hairline()
        SaveBar(dirty = dirty, justSaved = justSaved, error = error, onSave = { save() })

        Spacer(Modifier.height(12.dp))
    }
}

/** Save button with dirty / saved / error feedback. */
@Composable
private fun SaveBar(
    dirty: Boolean,
    justSaved: Boolean,
    error: String?,
    onSave: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Save profile",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (dirty) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                .accessibleClick("Save profile", enabled = dirty) { onSave() }
                .padding(horizontal = 6.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            when {
                error != null -> error
                dirty -> "Unsaved changes"
                justSaved -> "Saved"
                else -> "Up to date"
            },
            fontSize = 12.sp,
            color = when {
                error != null -> MaterialTheme.colorScheme.error
                dirty -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            }
        )
    }
}

@Composable
private fun EditField(label: String, value: String, onChange: (String) -> Unit) {
    FieldShell(label) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun EditNumber(label: String, value: String, onChange: (String) -> Unit) {
    FieldShell(label) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun FieldShell(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.width(96.dp).padding(top = 7.dp)
        )
        Column(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            options.forEach { opt ->
                val isSel = opt == selected
                Text(
                    text = opt.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(opt) }
                        .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}
