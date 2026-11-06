package com.evyr.rads.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rads.data.AllergyChecker
import com.evyr.rads.data.Condition
import com.evyr.rads.data.ConditionFlags
import com.evyr.rads.data.CustomAllergen
import com.evyr.rads.data.Units
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.evyr.rads.data.local.FoodLogEntry
import com.evyr.rads.data.local.HealthSnapshot
import com.evyr.rads.data.local.DEFAULT_FAT_WARN_GRAMS
import com.evyr.rads.data.local.DEFAULT_CALORIE_TARGET
import com.evyr.rads.data.local.UserProfile

@Composable
fun TabStatsView(
    scrollState: ScrollState,
    isToday: Boolean,
    entries: List<FoodLogEntry>,
    profile: UserProfile?,
    health: HealthSnapshot?,
    mealSlots: List<String>,
    history: List<FoodLogEntry> = emptyList()
) {
    Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {

        val target = profile?.calorieTarget() ?: DEFAULT_CALORIE_TARGET
        val totalCals = entries.sumOf { it.calories }
        val totalProtein = entries.sumOf { it.proteinGrams }
        val totalCarbs = entries.sumOf { it.carbGrams }

        SectionLabel(if (isToday) "Today's intake" else "Selected day")
        BarMeter("Calories", totalCals.toDouble(), target.toDouble(), "")
        StatRow("Protein", "${trim(totalProtein)} g")
        StatRow("Carbs", "${trim(totalCarbs)} g")
        StatRow("Entries", "${entries.size}")

        Spacer(Modifier.height(6.dp))
        Hairline()
        SectionLabel("Fat by meal")
        Text(
            "Fat is assessed per meal, not per day.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        val limit = profile?.fatWarnGramsPerMeal ?: DEFAULT_FAT_WARN_GRAMS
        mealSlots.forEach { slot ->
            val slotFat = entries.filter { it.mealSlot == slot }.sumOf { it.fatGrams }
            BarMeter(
                label = slot.replaceFirstChar { it.uppercase() },
                current = slotFat,
                target = limit,
                unit = "g",
                warn = slotFat >= limit
            )
        }

        Spacer(Modifier.height(6.dp))
        Hairline()
        TrendSection(history, profile)

        Spacer(Modifier.height(6.dp))
        Hairline()
        SectionLabel("Body & activity")
        StatRow("Steps", health?.steps?.toString() ?: "--", emphasize = true)
        StatRow("Exercise", health?.exerciseMinutes?.let { "$it min" } ?: "-- min")
        val imp = profile?.useImperial ?: true
        val wUnit = if (imp) "lb" else "kg"
        val shownKg = health?.weightKg ?: profile?.weightKg?.takeIf { it > 0 }
        StatRow(
            "Weight",
            shownKg?.let { "${Units.displayWeight(it, imp)} $wUnit" } ?: "-- $wUnit"
        )
        profile?.goalWeightKg?.takeIf { it > 0 }?.let { g ->
            StatRow("Goal weight", "${Units.displayWeight(g, imp)} $wUnit")
        }
        profile?.poundsToGoal()?.let { lbs ->
            StatRow("To goal", "${trim(kotlin.math.abs(lbs))} lb")
        }
        profile?.weeksToGoal()?.let { wk ->
            StatRow("ETA", if (wk == 0) "At goal" else "$wk weeks")
        }

        Spacer(Modifier.height(6.dp))
        Hairline()
        SectionLabel("Targets")
        StatRow("BMR", profile?.bmr()?.toInt()?.toString() ?: "--")
        StatRow("TDEE", profile?.tdee()?.toInt()?.toString() ?: "--")
        StatRow("Goal", profile?.goal?.replaceFirstChar { it.uppercase() } ?: "--")
        StatRow("Rate", profile?.let { "${trim(it.rateLbsPerWeek)} lb/wk" } ?: "--")
        StatRow("Target", "$target kcal", emphasize = true)

        Spacer(Modifier.height(10.dp))
    }
}


// ---------------------------------------------------------------------------
// Last 30 days
// ---------------------------------------------------------------------------

private data class TrendSummary(
    /** Calories per day, oldest first, 30 values (0 on days with nothing logged). */
    val dailyKcal: List<Int>,
    val daysLogged: Int,
    val avgKcal: Int,
    val daysOverTarget: Int,
    val mealsTotal: Int,
    val mealsOverFat: Int,
    /** Meals where each condition got flagged, most-flagged first. */
    val conditionMeals: List<Pair<Condition, Int>>,
    val allergyMeals: Int
)

/**
 * Counts per meal rather than per item, since "how many meals went over" is
 * the question that matters -- and the fat limit is judged per meal.
 */
private fun summarize(history: List<FoodLogEntry>, profile: UserProfile?): TrendSummary {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    fun dateOf(e: FoodLogEntry) = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate()

    val target = profile?.calorieTarget() ?: DEFAULT_CALORIE_TARGET
    val fatLimit = profile?.fatWarnGramsPerMeal ?: DEFAULT_FAT_WARN_GRAMS
    val conditions = profile?.conditionSet() ?: emptySet()
    val allergens = profile?.allergenSet() ?: emptySet()
    val customs = CustomAllergen.parseCsv(profile?.customAllergens)

    val byDay = history.groupBy { dateOf(it) }
    val daily = (29 downTo 0).map { back -> byDay[today.minusDays(back.toLong())].orEmpty().sumOf { it.calories } }
    val loggedDays = daily.filter { it > 0 }

    val meals = history.groupBy { dateOf(it) to it.mealSlot }.values
    val conditionCounts = mutableMapOf<Condition, Int>()
    var overFat = 0
    var allergyMeals = 0
    meals.forEach { meal ->
        if (meal.sumOf { it.fatGrams } >= fatLimit) overFat++
        val flagged = meal.flatMap { e -> ConditionFlags.forEntry(e, conditions).map { it.condition } }.toSet() +
            ConditionFlags.forMeal(meal, conditions, profile?.limits() ?: com.evyr.rads.data.Limits()).map { it.condition }.toSet()
        flagged.forEach { c -> conditionCounts[c] = (conditionCounts[c] ?: 0) + 1 }
        if (meal.any { AllergyChecker.check(it, allergens, customs).isNotEmpty() }) allergyMeals++
    }

    return TrendSummary(
        dailyKcal = daily,
        daysLogged = loggedDays.size,
        avgKcal = if (loggedDays.isEmpty()) 0 else loggedDays.average().toInt(),
        daysOverTarget = loggedDays.count { it > target },
        mealsTotal = meals.size,
        mealsOverFat = overFat,
        conditionMeals = conditionCounts.entries.sortedByDescending { it.value }.map { it.key to it.value },
        allergyMeals = allergyMeals
    )
}

@Composable
private fun TrendSection(history: List<FoodLogEntry>, profile: UserProfile?) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    Spacer(Modifier.height(6.dp))
    Hairline()
    SectionLabel("Last 30 days")

    if (history.isEmpty()) {
        Text(
            "Log a few days of meals and your trends show up here.",
            fontSize = 12.sp,
            color = dim,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        return
    }

    val t = summarize(history, profile)
    val target = profile?.calorieTarget() ?: DEFAULT_CALORIE_TARGET
    CalorieBars(t.dailyKcal, target)
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text("30 days ago", fontSize = 10.sp, color = dim, modifier = Modifier.weight(1f))
        Text("Today", fontSize = 10.sp, color = dim)
    }

    StatRow("Days logged", "${t.daysLogged} of 30")
    StatRow("Avg / day", "${t.avgKcal} kcal (target $target)")
    StatRow("Over target", "${t.daysOverTarget} of ${t.daysLogged} days")
    StatRow("Fat over limit", "${t.mealsOverFat} of ${t.mealsTotal} meals", warn = t.mealsOverFat > 0)
    if ((profile?.allergenSet()?.isNotEmpty() == true) || !profile?.customAllergens.isNullOrBlank()) {
        StatRow("Allergy hits", "${t.allergyMeals} of ${t.mealsTotal} meals", warn = t.allergyMeals > 0)
    }
    t.conditionMeals.forEach { (c, n) ->
        StatRow(c.short.lowercase().replaceFirstChar { it.uppercase() }, "flagged in $n of ${t.mealsTotal} meals")
    }
    Text(
        "Worth bringing to a doctor: how often meals go over your fat limit, and which conditions get flagged most.",
        fontSize = 11.sp,
        color = dim,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** One bar per day; days over the calorie target in the error color, dashed target line. */
@Composable
private fun CalorieBars(daily: List<Int>, target: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val empty = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Canvas(Modifier.fillMaxWidth().height(90.dp).padding(vertical = 4.dp)) {
        val maxV = maxOf(target * 1.25f, (daily.maxOrNull() ?: 0).toFloat(), 1f)
        val slot = size.width / daily.size
        val barW = slot * 0.7f
        daily.forEachIndexed { i, v ->
            val h = if (v <= 0) 2f else (v / maxV) * size.height
            drawRect(
                color = when {
                    v <= 0 -> empty
                    v > target -> error
                    else -> primary
                },
                topLeft = Offset(i * slot + (slot - barW) / 2f, size.height - h),
                size = Size(barW, h)
            )
        }
        val ty = size.height - (target / maxV) * size.height
        drawLine(
            color = lineColor,
            start = Offset(0f, ty),
            end = Offset(size.width, ty),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )
    }
}
