package com.evyr.rads.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rads.data.Allergen
import com.evyr.rads.data.AllergyChecker
import com.evyr.rads.data.Condition
import com.evyr.rads.data.ConditionFlags
import com.evyr.rads.data.CustomAllergen
import com.evyr.rads.data.Verdict
import com.evyr.rads.data.local.FoodLogEntry
import com.evyr.rads.data.local.SafeFood
import com.evyr.rads.ui.theme.*

private data class MealTint(
    val bg: Color,
    val border: Color,
    val chip: Color,
    val text: Color
)

/**
 * One neutral palette for every meal. Meals are told apart by their icon and
 * label; red, amber and green belong to the traffic light alone, so a tinted
 * card can never be mistaken for a verdict. The old per-meal colour families
 * (MealBreakfast*, MealLunch*, ...) are still in the theme if this is reverted.
 */
@Suppress("UNUSED_PARAMETER")
private fun mealTint(slot: String): MealTint =
    MealTint(CreamCard, CreamCardBorder, CreamCard2, ClayTextSoft)

private fun mealIcon(slot: String): String = when (slot) {
    "breakfast" -> "\uD83C\uDF73" // cooking (pan + egg)
    "lunch" -> "\uD83E\uDD57" // green salad
    "dinner" -> "\uD83C\uDF7D\uFE0F" // fork and knife with plate
    else -> "\uD83C\uDF7F" // popcorn
}

/**
 * Content-matched emoji per food name, ported from the original Gut Check
 * PWA's getFoodEmoji(). Falls back to a plate for anything unmatched.
 */
private fun foodEmoji(name: String): String {
    val n = name.lowercase()
    return when {
        n.contains("chicken") -> "\uD83C\uDF57"
        n.contains("beef") || n.contains("burger") -> "\uD83C\uDF54"
        n.contains("pizza") -> "\uD83C\uDF55"
        n.contains("salad") -> "\uD83E\uDD57"
        n.contains("fish") || n.contains("tuna") || n.contains("salmon") -> "\uD83D\uDC1F"
        n.contains("egg") -> "\uD83C\uDF73"
        n.contains("milk") || n.contains("yogurt") -> "\uD83E\uDD5B"
        n.contains("bread") || n.contains("toast") -> "\uD83C\uDF5E"
        n.contains("rice") -> "\uD83C\uDF5A"
        n.contains("pasta") || n.contains("noodle") -> "\uD83C\uDF5D"
        n.contains("coffee") || n.contains("latte") -> "\u2615"
        else -> "\uD83C\uDF7D\uFE0F"
    }
}

/**
 * One meal as a tinted accordion card: header (icon, name, item count,
 * alert dot, total kcal, chevron) that expands to show that meal's fat and
 * condition warnings, each logged item, and a dashed "+ Add to" button.
 * Fat is judged per meal here, never against a daily budget.
 */
@Composable
fun MealAccordionCard(
    slot: String,
    label: String,
    entries: List<FoodLogEntry>,
    expanded: Boolean,
    selectedEntryId: Long?,
    fatWarnGrams: Double,
    conditions: Set<Condition>,
    limits: com.evyr.rads.data.Limits = com.evyr.rads.data.Limits(),
    allergens: Set<Allergen>,
    customAllergens: List<CustomAllergen>,
    safeKeys: Set<String>,
    simple: Boolean = false,
    onToggleSafe: (FoodLogEntry) -> Unit,
    onToggle: () -> Unit,
    onSelectEntry: (FoodLogEntry) -> Unit,
    onDeleteEntry: (FoodLogEntry) -> Unit,
    onSuggestSwaps: (FoodLogEntry) -> Unit = {},
    onAdd: () -> Unit
) {
    val tint = mealTint(slot)
    val error = MaterialTheme.colorScheme.error
    val onSurface = MaterialTheme.colorScheme.onSurface
    val cardShape = RoundedCornerShape(16.dp)

    val total = entries.sumOf { it.calories }
    val mealFat = entries.sumOf { it.fatGrams }
    val overFat = entries.isNotEmpty() && mealFat >= fatWarnGrams
    val mealIssues = ConditionFlags.mealIssues(entries, conditions, limits)
    val mealAllergyHits = entries.flatMap { AllergyChecker.check(it, allergens, customAllergens) }.distinct()
    // A light only means something if the person told us what to watch for.
    val showLight = entries.isNotEmpty() &&
        (conditions.isNotEmpty() || allergens.isNotEmpty() || customAllergens.isNotEmpty())
    val worst = maxOf(
        entries.flatMap { ConditionFlags.forEntry(it, conditions) }.maxOfOrNull { it.severity.ordinal } ?: 0,
        mealIssues.maxOfOrNull { it.severity.ordinal } ?: 0,
        if (overFat || mealAllergyHits.isNotEmpty()) Verdict.OVER_LIMIT.ordinal else 0
    )
    val mealLight = when (worst) {
        Verdict.OVER_LIMIT.ordinal -> Light.STOP
        Verdict.CAUTION.ordinal -> Light.CAUTION
        else -> Light.GO
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(tint.bg)
            .border(1.dp, tint.border, cardShape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = if (expanded) "Collapse $label" else "Expand $label",
                    role = androidx.compose.ui.semantics.Role.Button
                ) { onToggle() }
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.chip),
                contentAlignment = Alignment.Center
            ) {
                Text(mealIcon(slot), fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = onSurface)
            if (entries.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.chip)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("${entries.size}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tint.text)
                }
            }
            if (showLight) {
                Spacer(Modifier.width(8.dp))
                TrafficLight(
                    mealLight, dot = 10.dp, horizontal = true, showSymbol = false,
                    description = "$label check: ${mealLight.spoken}"
                )
            }
            Spacer(Modifier.weight(1f))
            if (total > 0) {
                Text("$total", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tint.text)
                Spacer(Modifier.width(8.dp))
            }
            // Empty, collapsed meal: add straight from the header (one tap, not two).
            if (entries.isEmpty() && !expanded) {
                Text(
                    "+ Add",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint.text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.chip)
                        .accessibleClick("Add food to $label") { onAdd() }
                        .padding(horizontal = 8.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            AppIconView(
                icon = if (expanded) AppIcon.CHEVRON_DOWN else AppIcon.CHEVRON_RIGHT,
                color = tint.text,
                iconSize = 18.dp,
                strokeWidth = 2.dp
            )
        }

        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 15.dp, end = 15.dp, bottom = 13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (overFat) {
                    Text(
                        "Over your fat limit for this meal: ${trim(mealFat)}g of ${trim(fatWarnGrams)}g",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = error
                    )
                }
                mealIssues.forEach { issue ->
                    Text(
                        issue.describe(simple),
                        fontSize = 12.sp,
                        fontWeight = if (issue.severity == Verdict.OVER_LIMIT) FontWeight.Bold else FontWeight.Normal,
                        color = severityColor(issue.severity)
                    )
                }
                if (mealAllergyHits.isNotEmpty()) {
                    Text(
                        "ALLERGY: " + mealAllergyHits.joinToString(", ") { it.label },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = error
                    )
                }

                entries.forEach { entry ->
                  SwipeRow(onDelete = { onDeleteEntry(entry) }) {
                    FoodRow(
                        entry = entry,
                        tint = tint,
                        selected = entry.id == selectedEntryId,
                        conditions = conditions,
                        allergens = allergens,
                        customAllergens = customAllergens,
                        isSafe = SafeFood.keyFor(entry.name) in safeKeys,
                        simple = simple,
                        onToggleSafe = { onToggleSafe(entry) },
                        onClick = { onSelectEntry(entry) },
                        onDelete = { onDeleteEntry(entry) },
                        onSuggestSwaps = { onSuggestSwaps(entry) }
                    )
                  }
                }

                val dash = tint.text.copy(alpha = 0.5f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .drawBehind {
                            val sw = 1.5.dp.toPx()
                            drawRoundRect(
                                color = dash,
                                topLeft = Offset(sw / 2f, sw / 2f),
                                size = Size(size.width - sw, size.height - sw),
                                cornerRadius = CornerRadius(12.dp.toPx()),
                                style = Stroke(
                                    width = sw,
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f
                                    )
                                )
                            )
                        }
                        .clickable { onAdd() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+ Add to ${label.lowercase()}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = tint.text
                    )
                }
            }
        }
    }
}

@Composable
private fun FoodRow(
    entry: FoodLogEntry,
    tint: MealTint,
    selected: Boolean,
    conditions: Set<Condition>,
    allergens: Set<Allergen>,
    customAllergens: List<CustomAllergen>,
    isSafe: Boolean,
    simple: Boolean = false,
    onToggleSafe: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onSuggestSwaps: () -> Unit = {}
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val error = MaterialTheme.colorScheme.error
    val rowShape = RoundedCornerShape(12.dp)
    val flags = ConditionFlags.forEntry(entry, conditions)
    val allergyHits = AllergyChecker.check(entry, allergens, customAllergens)
    // An allergy is always the worst case, ahead of any condition tier.
    val worst = if (allergyHits.isNotEmpty()) Verdict.OVER_LIMIT
        else flags.maxByOrNull { it.severity.ordinal }?.severity

    val sub = buildList {
        add("${trim(entry.fatGrams)}g fat")
        if (entry.proteinGrams > 0) add("${trim(entry.proteinGrams)}g prot")
    }.joinToString(" \u00B7 ")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(Color.White)
            .border(1.dp, tint.border, rowShape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint.bg),
                contentAlignment = Alignment.Center
            ) {
                Text(foodEmoji(entry.name), fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    fontSize = if (simple) 17.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (worst == Verdict.OVER_LIMIT) error else onSurface
                )
                if (simple) {
                    // Plain verdict instead of grams: okay / be careful / skip, with the first reason.
                    val reason = flags.maxByOrNull { it.severity.ordinal }?.details?.firstOrNull()
                    val (word, tone) = when {
                        allergyHits.isNotEmpty() -> "Skip: allergy match" to error
                        worst == Verdict.OVER_LIMIT -> "Better to skip" to error
                        worst == Verdict.CAUTION ->
                            (if (reason != null) "Be careful: $reason" else "Be careful") to
                                severityColor(Verdict.CAUTION)
                        else -> "Okay" to onSurface.copy(alpha = 0.72f)
                    }
                    Text(word, fontSize = if (simple) 15.sp else 12.sp, fontWeight = FontWeight.Medium, color = tone)
                } else {
                    Text(
                        if (isSafe) "$sub \u00B7 Safe" else sub,
                        fontSize = 12.sp,
                        color = onSurface.copy(alpha = 0.72f)
                    )
                }
                if (allergyHits.isNotEmpty()) {
                    Text(
                        "ALLERGY: " + allergyHits.joinToString(", ") { it.label },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = error,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (!simple && flags.isNotEmpty()) {
                    Row(Modifier.padding(top = 2.dp)) {
                        flags.forEachIndexed { i, w ->
                            if (i > 0) {
                                Text(" \u00B7 ", fontSize = 11.sp, color = onSurface.copy(alpha = 0.68f))
                            }
                            Text(
                                w.condition.short,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = severityColor(w.severity)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("${entry.calories}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = onSurface)
        }

        if (selected) {
            Spacer(Modifier.height(8.dp))
            Hairline()
            Spacer(Modifier.height(4.dp))
            StatRow("Carbs", "${trim(entry.carbGrams)} g")
            entry.saturatedFatGrams?.let { StatRow("Sat fat", "${trim(it)} g") }
            entry.sugarGrams?.let { StatRow("Sugar", "${trim(it)} g") }
            entry.fiberGrams?.let { StatRow("Fiber", "${trim(it)} g") }
            entry.sodiumMg?.let { StatRow("Sodium", "${it.toInt()} mg") }
            StatRow("Source", entry.source.replaceFirstChar { it.uppercase() })
            if (allergyHits.isNotEmpty()) {
                Text(
                    "Allergy match: " + allergyHits.joinToString(", ") { it.label },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = error,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            flags.forEach { w ->
                Text(
                    "${w.condition.label}: ${w.details.joinToString(", ")}",
                    fontSize = 12.sp,
                    color = severityColor(w.severity),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (worst == Verdict.CAUTION || worst == Verdict.OVER_LIMIT) {
                Row(
                    Modifier
                        .accessibleClick("Get a lighter idea for next time", onClick = onSuggestSwaps)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IdeaIcon(26.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Lighter idea for next time",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                }
            }
            AppAction(if (isSafe) "Remove from safe foods" else "Mark as safe food", onToggleSafe)
            AppAction("Delete entry", onDelete, warn = true)
        }
    }
}

/** Swipe a logged food left to delete it; Undo appears at the bottom of the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    "Delete",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        },
        content = { content() }
    )
}
