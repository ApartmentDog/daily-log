package com.evyr.rads.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rads.data.Allergen
import com.evyr.rads.data.Condition
import com.evyr.rads.data.CustomAllergen
import com.evyr.rads.data.ScannedFood
import com.evyr.rads.data.Units
import com.evyr.rads.data.Verdict
import com.evyr.rads.data.VerdictRules
import com.evyr.rads.data.local.DEFAULT_FAT_WARN_GRAMS
import com.evyr.rads.data.local.DetailLevel
import com.evyr.rads.data.local.Focus
import com.evyr.rads.data.local.UserProfile
import com.evyr.rads.ui.components.DialogActions
import com.evyr.rads.ui.components.TrafficLight
import com.evyr.rads.ui.components.lightState
import java.util.Locale

/**
 * Onboarding decides what the home screen looks like. It opens with what the
 * person wants help with, then asks only the follow-ups that answer needs:
 * no conditions step unless they picked conditions, no body measurements
 * unless they want a weight or fitness goal. Every question changes
 * something they will see.
 */
private enum class Step {
    WELCOME, INTENT, CONDITIONS, FAT, ALLERGIES, BODY, GOAL, DETAIL, SAMPLE, HOME
}

private val FAT_RELEVANT = setOf(Condition.GALLBLADDER, Condition.FATTY_LIVER, Condition.CHOLESTEROL)

private fun friendlyName(c: Condition): String = when (c) {
    Condition.REFLUX -> "Heartburn / acid reflux (GERD)"
    Condition.HIATAL_HERNIA -> "Hiatal hernia"
    Condition.IBS -> "IBS"
    Condition.LACTOSE -> "Lactose intolerance"
    Condition.CELIAC -> "Celiac disease / gluten"
    Condition.GALLBLADDER -> "Gallbladder issues / gallstones"
    Condition.FATTY_LIVER -> "Fatty liver"
    Condition.HYPERTENSION -> "High blood pressure"
    Condition.CHOLESTEROL -> "High cholesterol"
    Condition.DIABETES -> "Diabetes / blood sugar"
    Condition.GOUT -> "Gout"
    else -> c.label
}

private val CONDITION_GROUPS: List<Pair<String, List<Condition>>> = listOf(
    "Stomach and gut" to listOf(
        Condition.REFLUX, Condition.HIATAL_HERNIA, Condition.IBS, Condition.LACTOSE, Condition.CELIAC
    ),
    "Gallbladder and liver" to listOf(Condition.GALLBLADDER, Condition.FATTY_LIVER),
    "Heart and blood sugar" to listOf(Condition.HYPERTENSION, Condition.CHOLESTEROL, Condition.DIABETES),
    "Joints" to listOf(Condition.GOUT)
)

@Composable
fun OnboardingScreen(onComplete: (UserProfile) -> Unit) {
    var stepIndex by remember { mutableStateOf(0) }

    var name by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf(emptySet<Focus>()) }
    var conditions by remember { mutableStateOf(emptySet<Condition>()) }
    var allergens by remember { mutableStateOf(emptySet<Allergen>()) }
    var customAllergensText by remember { mutableStateOf("") }

    var fatFromDoctor by remember { mutableStateOf(false) }
    var fatLimit by remember { mutableStateOf("15") }

    // Locale picks the starting units; the person can flip them on the body step.
    var imperial by remember { mutableStateOf(Locale.getDefault().country == "US") }
    var age by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("unspecified") }
    var feet by remember { mutableStateOf("") }
    var inches by remember { mutableStateOf("") }
    var heightCmText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }

    // No goal is preselected for weight users. Muscle-only starts on "gain".
    var goalChoice by remember { mutableStateOf<String?>(null) }
    var goalWeightText by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("1.0") }
    var activity by remember { mutableStateOf("moderate") }

    var detailChoice by remember { mutableStateOf<String?>(null) }

    val wantsBody = Focus.WEIGHT in focus || Focus.MUSCLE in focus
    val wantsSafety = Focus.CONDITION in focus || Focus.ALLERGY in focus
    val goal = goalChoice ?: if (Focus.MUSCLE in focus && Focus.WEIGHT !in focus) "gain" else null
    // Safety-first people get plain verdicts by default; number people get detail.
    val suggestedDetail = if (wantsBody) DetailLevel.DETAILED.key else DetailLevel.SIMPLE.key
    val detail = detailChoice ?: suggestedDetail

    val steps = buildList {
        add(Step.WELCOME)
        add(Step.INTENT)
        if (Focus.CONDITION in focus) add(Step.CONDITIONS)
        if (Focus.CONDITION in focus && conditions.any { it in FAT_RELEVANT }) add(Step.FAT)
        if (Focus.ALLERGY in focus) add(Step.ALLERGIES)
        if (wantsBody) {
            add(Step.BODY)
            add(Step.GOAL)
        }
        add(Step.DETAIL)
        if (wantsSafety) add(Step.SAMPLE)
        add(Step.HOME)
    }
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]
    val isLast = stepIndex >= steps.lastIndex

    val onSurface = MaterialTheme.colorScheme.onSurface
    val dim = onSurface.copy(alpha = 0.72f)
    val primary = MaterialTheme.colorScheme.primary

    fun heightCm(): Double = if (imperial) {
        Units.feetInchesToCm(feet.toIntOrNull() ?: 0, inches.toIntOrNull() ?: 0)
    } else heightCmText.toDoubleOrNull() ?: 0.0

    fun draftProfile(): UserProfile {
        val effectiveFocus = focus
        val enteredFat = if (fatFromDoctor) fatLimit.toDoubleOrNull()?.takeIf { it > 0 } else null
        return UserProfile(
            name = name.trim(),
            age = age.toIntOrNull() ?: 0,
            sex = sex,
            heightCm = if (wantsBody) heightCm() else 0.0,
            weightKg = if (wantsBody) Units.parseWeightToKg(weightText, imperial) ?: 0.0 else 0.0,
            goal = if (wantsBody) (goal ?: "maintain") else "maintain",
            goalWeightKg = if (wantsBody) Units.parseWeightToKg(goalWeightText, imperial) ?: 0.0 else 0.0,
            rateLbsPerWeek = rate.toDoubleOrNull() ?: 1.0,
            activityLevel = activity,
            useImperial = imperial,
            fatWarnGramsPerMeal = enteredFat ?: DEFAULT_FAT_WARN_GRAMS,
            fatLimitFromDoctor = enteredFat != null,
            conditions = if (Focus.CONDITION in effectiveFocus) Condition.toCsv(conditions) else "",
            allergens = if (Focus.ALLERGY in effectiveFocus) Allergen.toCsv(allergens) else "",
            customAllergens = if (Focus.ALLERGY in effectiveFocus) CustomAllergen.toCsv(
                customAllergensText.split(",").map { it.trim() }
                    .filter { it.isNotBlank() }.map { CustomAllergen(it) }
            ) else "",
            focus = Focus.toCsv(effectiveFocus),
            detailLevel = detail,
            onboarded = true
        )
    }

    val canContinue = when (step) {
        Step.WELCOME -> agreed
        Step.BODY -> (age.toIntOrNull() ?: 0) > 0 && heightCm() > 0 &&
            (Units.parseWeightToKg(weightText, imperial) ?: 0.0) > 0
        Step.GOAL -> goal != null &&
            (goal == "maintain" || (Units.parseWeightToKg(goalWeightText, imperial) ?: 0.0) > 0)
        Step.FAT -> !fatFromDoctor || (fatLimit.toDoubleOrNull() ?: 0.0) > 0
        else -> true
    }

    BackHandler(enabled = stepIndex > 0) { stepIndex-- }

    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth()) {
                steps.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                if (i <= stepIndex) primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(2.dp)
                            )
                    )
                    if (i != steps.lastIndex) Spacer(Modifier.width(4.dp))
                }
            }
            Spacer(Modifier.height(20.dp))

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
            ) {
                when (step) {
                    Step.WELCOME -> {
                        Text("R.A.D.S.", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = onSurface)
                        Spacer(Modifier.height(6.dp))
                        Prompt("Eat with confidence.")
                        Note(
                            "Tell us what you need help with and we'll build your home screen around it. " +
                                "It takes about a minute, and you can change everything later."
                        )
                        Spacer(Modifier.height(12.dp))
                        Field("Your name", name) { name = it }
                        Note("Optional.")
                        Spacer(Modifier.height(14.dp))
                        SelectCard(
                            title = "I understand",
                            blurb = "R.A.D.S. gives general food guidance, not medical advice. It can be wrong, " +
                                "especially for photo estimates and restaurant food. Your doctor's " +
                                "instructions always come first.",
                            selected = agreed
                        ) { agreed = !agreed }
                    }

                    Step.INTENT -> {
                        Prompt("What do you want help with?")
                        Note("Pick all that apply. You can skip this and we'll set up the basics.")
                        Spacer(Modifier.height(8.dp))
                        Focus.values().forEach { f ->
                            SelectCard(f.title, f.blurb, f in focus) {
                                focus = if (f in focus) focus - f else focus + f
                            }
                        }
                    }

                    Step.CONDITIONS -> {
                        Prompt("Which conditions do you manage?")
                        Note(
                            "Foods get flagged for these before you log them. Not sure, or none of these? " +
                                "Just tap Next. This is general guidance only."
                        )
                        Spacer(Modifier.height(8.dp))
                        val grouped = CONDITION_GROUPS.flatMap { it.second }.toSet()
                        val groups = CONDITION_GROUPS +
                            listOf("Other" to Condition.values().filter { it !in grouped })
                        groups.filter { it.second.isNotEmpty() }.forEach { (title, list) ->
                            Text(
                                title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = dim,
                                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                            )
                            list.forEach { c ->
                                SelectCard(friendlyName(c), null, c in conditions) {
                                    conditions = if (c in conditions) conditions - c else conditions + c
                                }
                            }
                        }
                    }

                    Step.FAT -> {
                        Prompt("Fat in a single meal")
                        Note("Did your doctor or dietitian give you a limit for fat in one meal?")
                        Spacer(Modifier.height(8.dp))
                        SelectCard(
                            "Not sure, use a general starting point",
                            "We start at ${DEFAULT_FAT_WARN_GRAMS.toInt()} g per meal. It isn't a " +
                                "prescription, and you can change it any time in Setup.",
                            !fatFromDoctor
                        ) { fatFromDoctor = false }
                        SelectCard(
                            "Yes, I have a number",
                            null,
                            fatFromDoctor
                        ) { fatFromDoctor = true }
                        if (fatFromDoctor) {
                            Field("Grams / meal", fatLimit, numeric = true) { fatLimit = it }
                        }
                    }

                    Step.ALLERGIES -> {
                        Prompt("Which allergies do you have?")
                        Note(
                            "A match in a food's name or ingredients gets flagged before you log it. " +
                                "This catches what's written on the label, not what touched the food in a " +
                                "kitchen, so keep reading labels and asking restaurants."
                        )
                        Spacer(Modifier.height(8.dp))
                        SelectCard("None of these", null, allergens.isEmpty() && customAllergensText.isBlank()) {
                            allergens = emptySet()
                            customAllergensText = ""
                        }
                        Allergen.values().forEach { a ->
                            SelectCard(a.label, null, a in allergens) {
                                allergens = if (a in allergens) allergens - a else allergens + a
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Note("Anything else? Separate with commas.")
                        Field("e.g. mango, MSG", customAllergensText) { customAllergensText = it }
                    }

                    Step.BODY -> {
                        Prompt("About you")
                        Note("Used only to work out a calorie goal that fits you. It stays on your phone.")
                        Spacer(Modifier.height(8.dp))
                        Choices("Units", listOf("imperial", "metric"), if (imperial) "imperial" else "metric") {
                            imperial = it == "imperial"
                        }
                        Field("Age", age, numeric = true) { age = it }
                        Choices("Sex", listOf("male", "female", "unspecified"), sex) { sex = it }
                        if (imperial) {
                            Row(Modifier.fillMaxWidth()) {
                                Box(Modifier.weight(1f)) {
                                    Field("Height ft", feet, numeric = true) { feet = it }
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.weight(1f)) {
                                    Field("in", inches, numeric = true) { inches = it }
                                }
                            }
                            Field("Weight lb", weightText, numeric = true) { weightText = it }
                        } else {
                            Field("Height cm", heightCmText, numeric = true) { heightCmText = it }
                            Field("Weight kg", weightText, numeric = true) { weightText = it }
                        }
                    }

                    Step.GOAL -> {
                        Prompt("What's your goal?")
                        Note("Slower tends to stick, but you know you best.")
                        Spacer(Modifier.height(8.dp))
                        listOf("lose" to "Lose weight", "maintain" to "Maintain", "gain" to "Gain weight / muscle")
                            .forEach { (key, label) ->
                                SelectCard(label, null, goal == key) { goalChoice = key }
                            }
                        if (goal != null && goal != "maintain") {
                            Field(
                                "Target ${if (imperial) "lb" else "kg"}",
                                goalWeightText,
                                numeric = true
                            ) { goalWeightText = it }
                            Choices("Rate (lb/week)", listOf("0.5", "1.0", "1.5", "2.0"), rate) { rate = it }
                            val r = rate.toDoubleOrNull() ?: 1.0
                            Note(
                                "${if (goal == "lose") "Losing" else "Gaining"} $r lb/week " +
                                    "≈ ${((r * 3500) / 7).toInt()} kcal/day " +
                                    (if (goal == "lose") "deficit." else "surplus.")
                            )
                        }
                        Choices(
                            "Activity",
                            listOf("sedentary", "light", "moderate", "active", "very_active"),
                            activity
                        ) { activity = it }
                    }

                    Step.DETAIL -> {
                        Prompt("How much detail do you want?")
                        Note("You can switch any time in Setup.")
                        Spacer(Modifier.height(8.dp))
                        SelectCard(
                            "Keep it simple",
                            "Each food gets a plain verdict: okay, be careful, or skip, with one reason.",
                            detail == DetailLevel.SIMPLE.key
                        ) { detailChoice = DetailLevel.SIMPLE.key }
                        SelectCard(
                            "Show me the numbers",
                            "Verdicts plus grams, limits, and which conditions are flagged.",
                            detail == DetailLevel.DETAILED.key
                        ) { detailChoice = DetailLevel.DETAILED.key }
                    }

                    Step.SAMPLE -> SampleStep(
                        conditions = if (Focus.CONDITION in focus) conditions else emptySet(),
                        allergens = if (Focus.ALLERGY in focus) allergens else emptySet(),
                        fatLimit = draftProfile().fatWarnGramsPerMeal,
                        simple = detail == DetailLevel.SIMPLE.key
                    )

                    Step.HOME -> {
                        val p = draftProfile()
                        val layout = p.homeLayout()
                        Prompt(if (p.name.isNotBlank()) "Here's your home, ${p.name}" else "Here's your home screen")
                        Note("Built from your answers:")
                        Spacer(Modifier.height(8.dp))
                        if (layout.showCheckHero) {
                            Bullet("A big \"Can I eat this?\" button. Scan a barcode, snap a menu or plate, or search.")
                        }
                        Bullet(
                            if (layout.simple) "Your meals, with a plain verdict on every food."
                            else "Your meals, with verdicts, numbers, and condition tags on every food."
                        )
                        if (layout.showCalorieRing) {
                            Bullet(
                                "A calorie ring and protein/carb bars" +
                                    if (wantsBody) ", aiming for about ${p.calorieTarget()} kcal a day." else "."
                            )
                        }
                        Bullet("Change any of this later in Setup.")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            DialogActions(
                secondaryText = if (stepIndex > 0) "Back" else null,
                onSecondary = { if (stepIndex > 0) stepIndex-- },
                primaryText = if (isLast) "Start" else "Next",
                primaryEnabled = canContinue,
                onPrimary = {
                    if (!isLast) stepIndex++ else onComplete(draftProfile())
                }
            )
        }
    }
}

@Composable
private fun SampleStep(
    conditions: Set<Condition>,
    allergens: Set<Allergen>,
    fatLimit: Double,
    simple: Boolean
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val sampleName = buildString {
        append("Fried chicken sandwich with tomato")
        allergens.firstOrNull()?.matchWords?.firstOrNull()?.let { append(", topped with $it") }
    }
    val food = remember(sampleName) {
        ScannedFood(
            name = sampleName,
            calories = 650, fatGrams = 28.0, proteinGrams = 30.0, carbGrams = 50.0,
            saturatedFatGrams = 7.0, sugarGrams = 6.0, sodiumMg = 1400.0,
            source = "sample"
        )
    }
    val verdict = remember(food, conditions, allergens, fatLimit) {
        VerdictRules.evaluate(food, emptyList(), fatLimit, conditions, allergens)
    }
    val state = lightState(verdict.verdict, allergy = verdict.allergyHits.isNotEmpty())
    val stampText = state.word
    val stampColor = state.light.color

    Prompt("Here's how a check looks")
    Note("An example food, checked against what you just told us. Real checks work the same way.")
    Spacer(Modifier.height(10.dp))
    Text(food.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = onSurface)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        TrafficLight(state.light, dot = 26.dp)
        Spacer(Modifier.width(16.dp))
        Text(stampText, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = onSurface)
    }
    Spacer(Modifier.height(8.dp))
    if (verdict.allergyHits.isNotEmpty()) {
        Text(
            "Allergy match: " + verdict.allergyHits.joinToString(", ") { it.label },
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error
        )
    }
    if (simple) {
        val reasons = buildList {
            verdict.warnings.take(2).forEach { w ->
                add("${w.condition.short}: ${w.details.firstOrNull().orEmpty()}")
            }
            if (isEmpty()) add(verdict.headline.lowercase().replaceFirstChar { it.uppercase() })
        }
        reasons.forEach { Bullet(it) }
    } else {
        verdict.compoundSummary?.let {
            Text(it, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        }
        verdict.warnings.forEach { w ->
            Text(
                w.condition.label,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = onSurface, modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                w.details.joinToString(", "),
                fontSize = 12.sp, color = onSurface.copy(alpha = 0.72f),
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        verdict.reasons.take(1).forEach { Bullet(it) }
    }
}

@Composable
private fun SelectCard(
    title: String,
    blurb: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (selected) primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface, shape)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) primary else MaterialTheme.colorScheme.outline,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) primary else onSurface
        )
        if (blurb != null) {
            Text(
                blurb,
                fontSize = 12.sp,
                color = onSurface.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("•", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Prompt(text: String) {
    Text(
        text,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    numeric: Boolean = false,
    onChange: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.width(96.dp).padding(top = 8.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = if (numeric) {
                KeyboardOptions(keyboardType = KeyboardType.Number)
            } else KeyboardOptions.Default,
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun Choices(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 12.sp, color = onSurface.copy(alpha = 0.72f))
        Spacer(Modifier.height(5.dp))
        options.forEach { opt ->
            val isSel = opt == selected
            Text(
                text = opt.replace('_', ' ').replaceFirstChar { it.uppercase() },
                fontSize = 14.sp,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                color = if (isSel) primary else onSurface.copy(alpha = 0.68f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(opt) }
                    .background(
                        if (isSel) primary.copy(alpha = 0.12f) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}
