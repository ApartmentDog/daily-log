package com.evyr.rads.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.evyr.rads.data.FoodPortion
import com.evyr.rads.data.ScannedFood
import com.evyr.rads.data.local.SafeFood
import com.evyr.rads.ui.theme.InfoBlue
import com.evyr.rads.ui.theme.SageAccent
import com.evyr.rads.ui.theme.WarnAmber

/**
 * The single "add food" sheet. One box takes a food name or a barcode;
 * results from USDA, Open Food Facts and (when needed) an AI estimate all
 * land in the same list, each labelled with where it came from. Scanning a
 * barcode or taking a photo feeds the same list instead of opening screens
 * of their own.
 */
@Composable
fun FoodSearchDialog(
    mealSlot: String,
    query: String,
    results: List<ScannedFood>,
    searching: Boolean,
    message: String?,
    aiEnabled: Boolean,
    safeFoods: List<ScannedFood> = emptyList(),
    quickFoods: List<ScannedFood> = emptyList(),
    onRemoveSafe: (String) -> Unit = {},
    onQuickLog: ((ScannedFood) -> Unit)? = null,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPick: (ScannedFood) -> Unit,
    onScanBarcode: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dim = onSurface.copy(alpha = 0.6f)
    val primary = MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .heightIn(max = 620.dp)
        ) {
            Text(
                "Add to ${mealSlot.replaceFirstChar { it.uppercase() }}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Spacer(Modifier.height(14.dp))

            // Search box: name or barcode digits, search key or Find button.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconView(AppIcon.SEARCH, dim, iconSize = 18.dp, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Food name or barcode", fontSize = 15.sp, color = onSurface.copy(alpha = 0.4f))
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                            textStyle = TextStyle(fontSize = 15.sp, color = onSurface),
                            cursorBrush = SolidColor(primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Find",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(primary)
                        .clickable { onSearch() }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Quick actions, all feeding the same results list below.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(AppIcon.BARCODE, "Scan", Modifier.weight(1f), onScanBarcode)
                // Always shown. Without a Gemini key the tap explains what's missing
                // instead of the buttons silently not existing.
                QuickAction(AppIcon.CAMERA, "Photo", Modifier.weight(1f), onTakePhoto)
                QuickAction(AppIcon.PHOTO, "Gallery", Modifier.weight(1f), onPickPhoto)
                QuickAction(AppIcon.PENCIL, "Manual", Modifier.weight(1f), onManual)
            }
            if (!aiEnabled) {
                Text(
                    "Add a Gemini key in Setup to log from a photo.",
                    fontSize = 12.sp,
                    color = onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            Hairline()

            when {
                searching -> Text(
                    message ?: "Searching...",
                    fontSize = 14.sp,
                    color = dim,
                    modifier = Modifier.padding(vertical = 18.dp)
                )
                message != null -> Text(
                    message,
                    fontSize = 14.sp,
                    color = dim,
                    modifier = Modifier.padding(vertical = 18.dp)
                )
                // Nothing typed yet: offer safe foods, then the usual rotation.
                results.isEmpty() && query.isBlank() &&
                    (safeFoods.isNotEmpty() || quickFoods.isNotEmpty()) -> {
                    val safeKeySet = safeFoods.map { SafeFood.keyFor(it.name) }.toSet()
                    val recents = quickFoods.filter { SafeFood.keyFor(it.name) !in safeKeySet }
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        if (safeFoods.isNotEmpty()) {
                            item { QuickHeader("Your safe foods") }
                            items(safeFoods) { food ->
                                ResultRow(
                                    food,
                                    badge = "Safe",
                                    onRemove = { onRemoveSafe(SafeFood.keyFor(food.name)) },
                                    onQuickAdd = onQuickLog?.let { q -> { q(food) } }
                                ) { onPick(food) }
                                Hairline()
                            }
                        }
                        if (recents.isNotEmpty()) {
                            item { QuickHeader("Recent & frequent") }
                            items(recents) { food ->
                                ResultRow(
                                    food,
                                    badge = "Recent",
                                    onQuickAdd = onQuickLog?.let { q -> { q(food) } }
                                ) { onPick(food) }
                                Hairline()
                            }
                        }
                    }
                }
                results.isEmpty() -> Text(
                    "Search USDA and Open Food Facts together. Try \"grilled chicken\", or type or scan a package barcode.",
                    fontSize = 13.sp,
                    color = onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 18.dp)
                )
                else -> LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(results) { food ->
                        ResultRow(food) { onPick(food) }
                        Hairline()
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Cancel",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = dim,
                modifier = Modifier.clickable { onDismiss() }.padding(vertical = 6.dp, horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun QuickAction(icon: AppIcon, label: String, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(primary.copy(alpha = 0.09f))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconView(icon, primary, iconSize = 22.dp)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = primary)
    }
}

/** Where a result came from, so the merged list stays readable. */
private fun sourceLabel(source: String): Pair<String, Color> = when (source) {
    "usda" -> "USDA" to SageAccent
    "vision" -> "AI estimate" to WarnAmber
    else -> "Open Food Facts" to InfoBlue
}

@Composable
private fun QuickHeader(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun ResultRow(
    food: ScannedFood,
    badge: String? = null,
    onRemove: (() -> Unit)? = null,
    onQuickAdd: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val (label, tone) = if (badge != null) badge to SageAccent else sourceLabel(food.source)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                food.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = tone,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(tone.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            if (onRemove != null) {
                Text(
                    "\u2715",
                    fontSize = 14.sp,
                    color = onSurface.copy(alpha = 0.45f),
                    modifier = Modifier
                        .clickable { onRemove() }
                        .padding(start = 10.dp, end = 2.dp)
                )
            }
            if (onQuickAdd != null) {
                // One tap: log at the last amount. Anything with a warning still opens the verdict.
                Text(
                    "+ Log",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable { onQuickAdd() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Text(
            "${food.calories} kcal \u00B7 F ${food.fatGrams}g \u00B7 P ${food.proteinGrams}g \u00B7 C ${food.carbGrams}g",
            fontSize = 12.sp,
            color = onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 3.dp)
        )
        food.servingNote?.let {
            Text(
                it,
                fontSize = 11.sp,
                color = onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Portion multiplier shown before the verdict, since servings rarely match. */
@Composable
fun QuantityDialog(
    food: ScannedFood,
    portionOptions: List<FoodPortion> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (ScannedFood) -> Unit
) {
    // When the source gave no serving size, the numbers are per 100 g and the
    // honest question is "how many grams", not "how many servings".
    val forcedAmount = food.basisGrams != null
    val canUseAmount = forcedAmount || food.servingGrams != null
    var byAmount by remember(food.name) { mutableStateOf(forcedAmount) }
    val gramsMode = forcedAmount || byAmount
    // What the nutrition numbers are relative to, in g/ml.
    val basis = food.basisGrams ?: food.servingGrams ?: 1.0

    // A chosen published portion overrides the raw amount box.
    var chosenPortion by remember(food.name) { mutableStateOf<FoodPortion?>(null) }
    var unit by remember(food.name) { mutableStateOf(MeasureUnit.G) }
    var amount by remember(food.name) {
        mutableStateOf(if (forcedAmount) MeasureUnit.G.default else "1")
    }
    val entered = amount.toDoubleOrNull() ?: if (gramsMode) basis else 1.0

    val multiplier = when {
        chosenPortion != null -> (chosenPortion!!.gramWeight / basis) * entered
        gramsMode -> (entered * unit.perUnit) / basis
        else -> entered
    }

    val presets = when {
        chosenPortion != null -> listOf("0.5", "1", "1.5", "2", "3")
        gramsMode -> unit.presets
        else -> listOf("0.5", "1", "1.5", "2", "3")
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val dim = onSurface.copy(alpha = 0.6f)
    val faint = onSurface.copy(alpha = 0.45f)
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val selectedBg = primary.copy(alpha = 0.14f)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text(
                "Portion",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                food.name,
                fontSize = 14.sp,
                color = onSurface
            )
            food.servingNote?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = faint,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (forcedAmount && portionOptions.isEmpty()) {
                Text(
                    "No serving size published — enter an amount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Published portions first — nobody weighs a fast-food biscuit.
            if (portionOptions.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Portion",
                    fontSize = 12.sp,
                    color = dim
                )
                portionOptions.forEach { option ->
                    val isSel = chosenPortion?.label == option.label
                    Text(
                        text = "${option.label}  (${option.gramWeight.toInt()} g)",
                        fontSize = 14.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) primary else onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chosenPortion = if (isSel) null else option
                                amount = "1"
                            }
                            .background(if (isSel) selectedBg else Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (!forcedAmount && canUseAmount && chosenPortion == null) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    listOf(false to "Servings", true to "Amount").forEach { (mode, label) ->
                        val isSel = byAmount == mode
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) primary else faint,
                            modifier = Modifier
                                .clickable {
                                    byAmount = mode
                                    amount = if (mode) unit.default else "1"
                                }
                                .background(if (isSel) selectedBg else surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
                food.servingGrams?.let { sg ->
                    Text(
                        "1 serving = ${sg.toInt()} g/ml",
                        fontSize = 11.sp,
                        color = faint,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            if (gramsMode && chosenPortion == null) {
                Text(
                    "Unit",
                    fontSize = 12.sp,
                    color = dim
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)) {
                    MeasureUnit.values().forEach { u ->
                        val isSel = u == unit
                        Text(
                            text = u.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) primary else faint,
                            modifier = Modifier
                                .clickable {
                                    if (u != unit) {
                                        unit = u
                                        amount = u.default
                                    }
                                }
                                .background(if (isSel) selectedBg else surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 11.dp, vertical = 8.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                if (unit.isVolume) {
                    Text(
                        "Volume assumes roughly water density — close for most drinks.",
                        fontSize = 11.sp,
                        color = faint,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            Text(
                when {
                    chosenPortion != null -> "How many"
                    gramsMode -> "Amount (${unit.label})"
                    else -> "Servings"
                },
                fontSize = 12.sp,
                color = dim
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)) {
                presets.forEach { preset ->
                    val isSel = amount.trim() == preset ||
                        (preset == "1" && amount.trim() == "1.0")
                    Text(
                        text = preset,
                        fontSize = 14.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) primary else faint,
                        modifier = Modifier
                            .clickable { amount = preset }
                            .background(if (isSel) selectedBg else surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }

            Row(Modifier.fillMaxWidth()) {
                Text(
                    "Or type",
                    fontSize = 12.sp,
                    color = dim,
                    modifier = Modifier.width(88.dp).padding(top = 8.dp)
                )
                BasicTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = onSurface
                    ),
                    cursorBrush = SolidColor(primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(10.dp))
            StatRow("Calories", "${(food.calories * multiplier).toInt()}")
            StatRow("Fat", "${round1(food.fatGrams * multiplier)} g")
            StatRow("Protein", "${round1(food.proteinGrams * multiplier)} g")
            StatRow("Carbs", "${round1(food.carbGrams * multiplier)} g")
            food.sugarGrams?.let { StatRow("Sugar", "${round1(it * multiplier)} g") }
            food.sodiumMg?.let { StatRow("Sodium", "${kotlin.math.round(it * multiplier).toInt()} mg") }

            Spacer(Modifier.height(16.dp))
            DialogActions(
                secondaryText = "Back",
                onSecondary = onDismiss,
                primaryText = "Check",
                onPrimary = {
                    onConfirm(
                        food.copy(
                            calories = (food.calories * multiplier).toInt(),
                            fatGrams = round1(food.fatGrams * multiplier),
                            proteinGrams = round1(food.proteinGrams * multiplier),
                            carbGrams = round1(food.carbGrams * multiplier),
                            saturatedFatGrams = food.saturatedFatGrams
                                ?.let { round1(it * multiplier) },
                            sugarGrams = food.sugarGrams?.let { round1(it * multiplier) },
                            fiberGrams = food.fiberGrams?.let { round1(it * multiplier) },
                            sodiumMg = food.sodiumMg?.let { kotlin.math.round(it * multiplier) },
                            servingNote = when {
                                chosenPortion != null ->
                                    "${trimQty(entered)} x ${chosenPortion!!.label}"
                                gramsMode -> "${trimQty(entered)} ${unit.label}"
                                else -> food.servingNote
                            },
                            basisGrams = null
                        )
                    )
                }
            )
        }
    }
}

private fun round1(v: Double): Double = kotlin.math.round(v * 10) / 10.0

/**
 * Units for entering an amount when a food only publishes per-100 values.
 * perUnit converts one unit into grams (or ml, treated as grams for liquids).
 */
private enum class MeasureUnit(
    val label: String,
    val perUnit: Double,
    val default: String,
    val presets: List<String>,
    val isVolume: Boolean
) {
    G("g", 1.0, "100", listOf("50", "100", "150", "200", "250"), false),
    OZ("oz", 28.3495, "4", listOf("1", "2", "4", "6", "8"), false),
    ML("ml", 1.0, "250", listOf("100", "250", "355", "500"), true),
    FL_OZ("fl oz", 29.5735, "12", listOf("8", "12", "16", "20"), true),
    CUP("cup", 236.588, "1", listOf("0.5", "1", "1.5", "2"), true)
}

private fun trimQty(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)
