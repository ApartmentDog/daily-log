package com.evyr.rads.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rads.data.AllergyChecker
import com.evyr.rads.data.Condition
import com.evyr.rads.data.ConditionFlags
import com.evyr.rads.data.Verdict
import com.evyr.rads.data.local.HomeLayout
import com.evyr.rads.data.local.FoodLogEntry
import com.evyr.rads.ui.theme.Terracotta
import com.evyr.rads.ui.theme.TerracottaText
import com.evyr.rads.ui.theme.CautionText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val HeroLight = Color(0xFF7585D0)

@Composable
fun TabLogView(
    viewDate: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToToday: () -> Unit,
    mealSlots: List<String>,
    activeMeal: String,
    dayEntries: List<FoodLogEntry>,
    calorieTarget: Int,
    entries: List<FoodLogEntry>,
    selectedEntry: FoodLogEntry?,
    fatWarnGrams: Double,
    conditions: Set<Condition>,
    limits: com.evyr.rads.data.Limits = com.evyr.rads.data.Limits(),
    allergens: Set<com.evyr.rads.data.Allergen>,
    customAllergens: List<com.evyr.rads.data.CustomAllergen>,
    safeKeys: Set<String>,
    onToggleSafe: (FoodLogEntry) -> Unit,
    listState: LazyListState,
    onSelectMeal: (String) -> Unit,
    onSelectEntry: (FoodLogEntry) -> Unit,
    onDeleteEntry: (FoodLogEntry) -> Unit,
    onSuggestSwaps: (FoodLogEntry) -> Unit = {},
    onAddToMeal: (String) -> Unit,
    onOpenProfile: () -> Unit,
    layout: HomeLayout,
    onCheckFood: () -> Unit,
    onCheckMenu: () -> Unit = {}
) {
    // activeMeal, entries and listState are still accepted so the call site
    // stays the same shape; the accordion shows every meal at once and
    // scrolls itself, so none of them drive layout here.

    val dayCals = dayEntries.sumOf { it.calories }
    val dayProtein = dayEntries.sumOf { it.proteinGrams }
    val dayCarbs = dayEntries.sumOf { it.carbGrams }

    // Protein/carbs have no stored goals, so the bars scale off the calorie
    // target with a typical split. Visual fill only; fat is the tracked limit.
    val proteinTarget = (calorieTarget * layout.proteinShare / 4).coerceAtLeast(1.0)
    val carbTarget = (calorieTarget * layout.carbShare / 4).coerceAtLeast(1.0)

    // Everything tracked per meal gets a bar: fat always, plus whatever the ticked
    // conditions watch (sodium, sugar, sat fat, meal size, carbs). Each shows the
    // biggest meal so far, because these limits are per meal, never daily.
    // Allergies are yes/no, so they have no bar.
    val perMeal = mealSlots.map { s -> dayEntries.filter { it.mealSlot == s } }
    val limitBars: List<LimitBar> = if (layout.simple) emptyList() else buildList {
        add(LimitBar("Fat / meal", perMeal.maxOfOrNull { es -> es.sumOf { it.fatGrams } } ?: 0.0, fatWarnGrams, fatWarnGrams, "g", false))
        ConditionFlags.mealPeaks(perMeal, conditions, limits).forEach { pk ->
            add(LimitBar(limitTitle(pk.label), pk.peak, pk.caution, pk.over, pk.unit, pk.partial))
        }
    }

    // Meals that already have items start open, like the PWA's expandedMeals.
    // Keyed on the date so switching days recomputes it.
    val expanded = remember(viewDate) {
        mutableStateOf(mealSlots.filter { slot -> dayEntries.any { it.mealSlot == slot } }.toSet())
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        LogHero(
            viewDate = viewDate,
            isToday = isToday,
            onPreviousDay = onPreviousDay,
            onNextDay = onNextDay,
            onJumpToToday = onJumpToToday,
            onOpenProfile = onOpenProfile,
            dayCals = dayCals,
            calorieTarget = calorieTarget,
            dayProtein = dayProtein,
            proteinTarget = proteinTarget,
            dayCarbs = dayCarbs,
            carbTarget = carbTarget,
            showStats = layout.showCalorieRing,
            limitBars = limitBars,
            onCheckFood = if (layout.showCheckHero) onCheckFood else null,
            onCheckMenu = if (layout.showCheckHero) onCheckMenu else null
        )

        if (dayEntries.isEmpty()) {
            if (isToday) EmptyDay(showCheck = layout.showCheckHero, simple = layout.simple, onCheckFood = onCheckFood)
        } else if (layout.showCheckHero) {
            val flagged = flaggedMeals(
                dayEntries, mealSlots, conditions, limits, allergens, customAllergens, fatWarnGrams
            )
            DaySummary(
                logged = mealSlots.count { s -> dayEntries.any { it.mealSlot == s } },
                flagged = flagged,
                simple = layout.simple,
                onShow = { f ->
                    expanded.value = expanded.value + f.slot
                    onSelectMeal(f.slot)
                    onSelectEntry(f.entry)
                }
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            mealSlots.forEach { slot ->
                MealAccordionCard(
                    slot = slot,
                    label = slot.replaceFirstChar { it.uppercase() },
                    entries = dayEntries.filter { it.mealSlot == slot },
                    expanded = slot in expanded.value,
                    selectedEntryId = selectedEntry?.id,
                    fatWarnGrams = fatWarnGrams,
                    conditions = conditions,
                    limits = limits,
                    allergens = allergens,
                    customAllergens = customAllergens,
                    safeKeys = safeKeys,
                    simple = layout.simple,
                    onToggleSafe = onToggleSafe,
                    onToggle = {
                        expanded.value =
                            if (slot in expanded.value) expanded.value - slot else expanded.value + slot
                        onSelectMeal(slot)
                    },
                    onSelectEntry = onSelectEntry,
                    onDeleteEntry = onDeleteEntry,
                    onSuggestSwaps = onSuggestSwaps,
                    onAdd = { onAddToMeal(slot) }
                )
            }
        }
    }
}

/**
 * Full-bleed gradient hero: date pill, profile avatar, calorie ring and
 * protein/carb bars, with two soft decorative circles behind the content.
 */
@Composable
private fun LogHero(
    viewDate: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToToday: () -> Unit,
    onOpenProfile: () -> Unit,
    dayCals: Int,
    calorieTarget: Int,
    dayProtein: Double,
    proteinTarget: Double,
    dayCarbs: Double,
    carbTarget: Double,
    showStats: Boolean,
    limitBars: List<LimitBar> = emptyList(),
    onCheckFood: (() -> Unit)?,
    onCheckMenu: (() -> Unit)? = null
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(Brush.linearGradient(listOf(HeroLight, Terracotta)))
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-50).dp)
                .size(160.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(x = (-10).dp, y = 50.dp)
                .size(110.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )

        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.28f))
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "\u2039",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .accessibleClick("Previous day") { onPreviousDay() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Text(
                        viewDate.format(DateTimeFormatter.ofPattern("EEE d MMM")),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        "\u203A",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = if (isToday) 0.4f else 1f),
                        modifier = Modifier
                            .accessibleClick("Next day", enabled = !isToday) { onNextDay() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                if (!isToday) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Today",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                            .accessibleClick("Jump to today") { onJumpToToday() }
                            .padding(horizontal = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClickLabel = "Open setup", role = androidx.compose.ui.semantics.Role.Button) { onOpenProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    AppIconView(AppIcon.PERSON, Terracotta, iconSize = 20.dp, strokeWidth = 2.dp)
                }
            }

            if (onCheckFood != null) {
                Spacer(Modifier.height(14.dp))
                CheckFoodButton(onCheckFood, onCheckMenu)
            }

            if (showStats) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CalorieRing(eaten = dayCals, target = calorieTarget)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MacroBarRow(emoji = "\uD83E\uDD69", label = "Protein", value = dayProtein, target = proteinTarget)
                        MacroBarRow(emoji = "\uD83C\uDF5E", label = "Carbs", value = dayCarbs, target = carbTarget)
                    }
                }
                if (limitBars.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    LimitBars(limitBars)
                }
            }
        }
    }
}

/** The safety-first home's two entry points, side by side as equals. */
@Composable
private fun CheckFoodButton(onClick: () -> Unit, onCheckMenu: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ActionTile(
            "Can I eat this?", "Scan, snap a plate, or search",
            "Check a food", onClick, Modifier.weight(1f).fillMaxHeight()
        )
        if (onCheckMenu != null) {
            ActionTile(
                "Eating out?", "Photograph a menu",
                "Open menu mode: photograph a menu", onCheckMenu, Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ActionTile(title: String, subtitle: String, label: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .accessibleClick(label, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TerracottaText)
        Text(subtitle, fontSize = 12.sp, color = TerracottaText, modifier = Modifier.padding(top = 2.dp))
    }
}

/** One per-meal limit: the biggest meal so far against the limit it is judged by. */
internal data class LimitBar(
    val label: String,
    val value: Double,
    val caution: Double,
    val over: Double,
    val unit: String,
    val partial: Boolean
)

private fun limitTitle(label: String): String = when (label) {
    "meal size" -> "Meal size"
    "sat fat" -> "Sat fat / meal"
    else -> label.replaceFirstChar { it.uppercase() } + " / meal"
}

@Composable
private fun LimitBars(bars: List<LimitBar>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Per-meal limits \u00B7 biggest meal today",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f)
        )
        bars.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { LimitBarCell(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LimitBarCell(bar: LimitBar, modifier: Modifier) {
    // No warning colours up here: the cue is a symbol, so it reads without colour too.
    val state = when {
        bar.value >= bar.over -> "over" to " \u2715"
        bar.value >= bar.caution && bar.caution < bar.over -> "nearing" to " !"
        else -> "ok" to ""
    }
    val u = if (bar.unit == "kcal") " kcal" else bar.unit
    val text = "${trim(bar.value)}/${trim(bar.over)}$u${state.second}"
    val pct = if (bar.over > 0) (bar.value / bar.over).coerceIn(0.0, 1.0) else 0.0
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "${bar.label}: ${trim(bar.value)} of ${trim(bar.over)}$u, ${state.first}"
        }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(bar.label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.95f))
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.28f))
        ) {
            Box(
                Modifier.fillMaxWidth(pct.toFloat()).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun CalorieRing(eaten: Int, target: Int) {
    val pct = if (target > 0) (eaten.toFloat() / target).coerceIn(0f, 1f) else 0f
    Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(100.dp)) {
            val stroke = 11.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(
                color = Color.White.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke)
            )
            if (pct > 0f) {
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * pct,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$eaten",
                fontSize = 28.sp,
                fontFamily = FontFamily.Serif,
                color = Color.White
            )
            Text(
                if (target > 0) "of $target cal" else "cal",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.95f)
            )
        }
    }
}

@Composable
private fun MacroBarRow(emoji: String, label: String, value: Double, target: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 15.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.95f))
                Text(
                    "${value.toInt()}/${target.toInt()}g",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.95f)
                )
            }
            Spacer(Modifier.height(4.dp))
            val pct = if (target > 0) (value / target).coerceIn(0.0, 1.0) else 0.0
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.28f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(pct.toFloat())
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
            }
        }
    }
}

/** Caution reads in the warning tone, over-limit in the error tone. */
@Composable
internal fun severityColor(v: Verdict) = when (v) {
    Verdict.OVER_LIMIT -> MaterialTheme.colorScheme.error
    // Darker amber than the lamp itself: the lamp colour is too pale to read as text on cream.
    Verdict.CAUTION -> CautionText
    Verdict.PASS -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
}

internal fun trim(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)

private data class FlaggedMeal(val slot: String, val entry: FoodLogEntry, val cause: String)

/** Meals today that trip an allergy, a condition, a per-meal limit or the fat limit. */
private fun flaggedMeals(
    dayEntries: List<FoodLogEntry>,
    mealSlots: List<String>,
    conditions: Set<Condition>,
    limits: com.evyr.rads.data.Limits,
    allergens: Set<com.evyr.rads.data.Allergen>,
    customAllergens: List<com.evyr.rads.data.CustomAllergen>,
    fatWarnGrams: Double
): List<FlaggedMeal> = mealSlots.mapNotNull { slot ->
    val es = dayEntries.filter { it.mealSlot == slot }
    if (es.isEmpty()) return@mapNotNull null
    val allergic = es.firstOrNull { AllergyChecker.check(it, allergens, customAllergens).isNotEmpty() }
    if (allergic != null) return@mapNotNull FlaggedMeal(slot, allergic, "an allergy match")
    val hit = es.firstOrNull { e ->
        ConditionFlags.forEntry(e, conditions).any { it.severity != Verdict.PASS }
    }
    if (hit != null) {
        val c = ConditionFlags.forEntry(hit, conditions).first { it.severity != Verdict.PASS }.condition.short
        return@mapNotNull FlaggedMeal(slot, hit, c)
    }
    val issue = ConditionFlags.mealIssues(es, conditions, limits).firstOrNull()
    if (issue != null) return@mapNotNull FlaggedMeal(slot, es.first(), issue.label)
    if (es.sumOf { it.fatGrams } >= fatWarnGrams) return@mapNotNull FlaggedMeal(slot, es.first(), "fat")
    null
}

/** One line for the whole day, so nobody has to open every card to find the problem. */
@Composable
private fun DaySummary(
    logged: Int,
    flagged: List<FlaggedMeal>,
    simple: Boolean,
    onShow: (FlaggedMeal) -> Unit
) {
    val meals = if (logged == 1) "1 meal logged" else "$logged meals logged"
    val text = when {
        flagged.isEmpty() -> "$meals, none flagged"
        flagged.size == 1 -> "$meals, 1 flagged for ${flagged.first().cause}"
        else -> "$meals, ${flagged.size} flagged"
    }
    val first = flagged.firstOrNull()
    val tone = if (first != null) severityColor(Verdict.CAUTION) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tone.copy(alpha = 0.10f))
            .then(
                if (first != null) Modifier.accessibleClick("$text. Show the first flagged meal") { onShow(first) }
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            fontSize = if (simple) 17.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = tone,
            modifier = Modifier.weight(1f)
        )
        if (first != null) {
            Text("Show \u203A", fontSize = if (simple) 15.sp else 13.sp, fontWeight = FontWeight.SemiBold, color = tone)
        }
    }
}

/** First day, or a day with nothing logged: one clear next step instead of blank cards. */
@Composable
private fun EmptyDay(showCheck: Boolean, simple: Boolean, onCheckFood: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(com.evyr.rads.ui.theme.CreamBg)
            .padding(18.dp)
    ) {
        Text(
            if (showCheck) "Check your first meal" else "Nothing logged yet",
            fontSize = if (simple) 20.sp else 17.sp,
            fontWeight = FontWeight.Bold,
            color = TerracottaText
        )
        Text(
            if (showCheck) "Scan a barcode, snap a plate, or search for a food. You will see a traffic light before you eat or log it."
            else "Add what you eat in a meal card below.",
            fontSize = if (simple) 16.sp else 13.sp,
            color = TerracottaText,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (showCheck) {
            Text(
                "Check a food",
                fontSize = if (simple) 17.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = TerracottaText,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .accessibleClick("Check a food", onClick = onCheckFood)
            )
        }
    }
}
