package com.evyr.rads.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rads.data.Condition
import com.evyr.rads.data.Verdict
import com.evyr.rads.data.local.HomeLayout
import com.evyr.rads.data.local.FoodLogEntry
import com.evyr.rads.ui.theme.Terracotta
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val HeroLight = Color(0xFFF0A868)

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
            onCheckFood = if (layout.showCheckHero) onCheckFood else null,
            onCheckMenu = if (layout.showCheckHero) onCheckMenu else null
        )

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

        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 26.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "\u2039",
                        fontSize = 18.sp,
                        color = Color.White,
                        modifier = Modifier.accessibleClick("Previous day") { onPreviousDay() }
                    )
                    Text(
                        viewDate.format(DateTimeFormatter.ofPattern("EEE d MMM")),
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        "\u203A",
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = if (isToday) 0.35f else 1f),
                        modifier = Modifier
                            .accessibleClick("Next day", enabled = !isToday) { onNextDay() }
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
                Spacer(Modifier.height(18.dp))
                CheckFoodButton(onCheckFood, onCheckMenu)
            }

            if (showStats) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CalorieRing(eaten = dayCals, target = calorieTarget)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MacroBarRow(emoji = "\uD83E\uDD69", label = "Protein", value = dayProtein, target = proteinTarget)
                        MacroBarRow(emoji = "\uD83C\uDF5E", label = "Carbs", value = dayCarbs, target = carbTarget)
                    }
                }
            }
        }
    }
}

/** The safety-first home's main action: check a food before eating or logging it. */
@Composable
private fun CheckFoodButton(onClick: () -> Unit, onCheckMenu: (() -> Unit)?) {
  Column {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            "Can I eat this?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Terracotta
        )
        Text(
            "Scan a barcode, snap a menu or plate, or search",
            fontSize = 13.sp,
            color = Terracotta.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
    if (onCheckMenu != null) {
        Text(
            "Eating out? Check a whole menu",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .accessibleClick("Check a whole menu from a photo", onClick = onCheckMenu)
                .padding(top = 6.dp)
        )
    }
  }
}

@Composable
private fun CalorieRing(eaten: Int, target: Int) {
    val pct = if (target > 0) (eaten.toFloat() / target).coerceIn(0f, 1f) else 0f
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(112.dp)) {
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
                fontSize = 30.sp,
                fontFamily = FontFamily.Serif,
                color = Color.White
            )
            Text(
                "EATEN",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = Color.White.copy(alpha = 0.9f)
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
    Verdict.CAUTION -> Color(0xFF9C6A12)
    Verdict.PASS -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
}

internal fun trim(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)
