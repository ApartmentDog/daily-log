package com.evyr.rads.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.evyr.rads.data.ScannedFood
import com.evyr.rads.data.Verdict
import com.evyr.rads.data.VerdictResult

@Composable
fun VerdictDialog(
    food: ScannedFood,
    verdict: VerdictResult,
    mealSlot: String,
    isSafeFood: Boolean = false,
    onToggleSafe: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (ScannedFood) -> Unit
) {
    var name by remember { mutableStateOf(food.name) }
    var calories by remember { mutableStateOf(food.calories.toString()) }
    var fat by remember { mutableStateOf(num(food.fatGrams)) }
    var protein by remember { mutableStateOf(num(food.proteinGrams)) }
    var carbs by remember { mutableStateOf(num(food.carbGrams)) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val dim = onSurface.copy(alpha = 0.72f)
    val faint = onSurface.copy(alpha = 0.68f)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val error = MaterialTheme.colorScheme.error

    val state = lightState(
        verdict.verdict,
        incomplete = verdict.skipped.isNotEmpty(),
        allergy = verdict.allergyHits.isNotEmpty(),
        estimate = food.source == "vision"
    )
    val stampColor = state.light.color
    val stampText = state.word

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(20.dp)
                .heightIn(max = 600.dp)
        ) {
          // Details scroll; the Log / Discard footer below stays pinned.
          Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
          ) {
            Text(
                "Assessment — ${mealSlot.replaceFirstChar { it.uppercase() }}",
                fontSize = 12.sp,
                color = dim
            )
            Spacer(Modifier.height(12.dp))

            // The traffic light and its word: the first thing read in the dialog.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrafficLight(state.light, dot = 26.dp)
                Spacer(Modifier.width(16.dp))
                Text(
                    stampText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (food.source == "vision") {
                Spacer(Modifier.height(8.dp))
                Text(
                    "AI estimate. The numbers come from a photo or description and may be off. " +
                        "Check them in the fields below before you log.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF9C6A12)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                verdict.headline,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = stampColor
            )

            verdict.compoundSummary?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = error)
            }
            if (isSafeFood) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "On your safe foods list. Every check above still ran.",
                    fontSize = 12.sp,
                    color = secondary
                )
            }

            Spacer(Modifier.height(8.dp))
            verdict.reasons.forEach { r ->
                Text(
                    "• $r",
                    fontSize = 12.sp,
                    color = dim,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }

            // ---- Allergy hits: always shown first, always the worst case ----
            if (verdict.allergyHits.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Hairline()
                Text(
                    "Allergy match",
                    fontSize = 12.sp,
                    color = dim,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    verdict.allergyHits.joinToString(", ") { it.label },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = error
                )
            }

            // ---- Condition checks ----
            if (verdict.warnings.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Hairline()
                Text(
                    "Condition checks",
                    fontSize = 12.sp,
                    color = dim,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                verdict.warnings.forEach { w ->
                    val c = when (w.severity) {
                        Verdict.OVER_LIMIT -> error
                        Verdict.CAUTION -> primary
                        Verdict.PASS -> faint
                    }
                    Text(
                        w.condition.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = c
                    )
                    Text(
                        w.details.joinToString(", "),
                        fontSize = 12.sp,
                        color = if (w.severity == Verdict.PASS) faint else dim,
                        modifier = Modifier.padding(start = 10.dp, bottom = 6.dp)
                    )
                }
            }

            if (verdict.skipped.isNotEmpty()) {
                // Missing data means the check is incomplete, not that the food
                // is fine, so this is shown at reading size in the caution tone.
                Text(
                    "Incomplete: couldn't check " + verdict.skipped.joinToString(", ") +
                        " (this source has no data), so treat the result as incomplete.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // ---- Extra nutrients, when the source has them ----
            val extras = listOfNotNull(
                food.saturatedFatGrams?.let { "Sat fat" to "${num(it)} g" },
                food.sugarGrams?.let { "Sugar" to "${num(it)} g" },
                food.fiberGrams?.let { "Fiber" to "${num(it)} g" },
                food.sodiumMg?.let { "Sodium" to "${it.toInt()} mg" }
            )
            if (extras.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                extras.forEach { (label, value) -> StatRow(label, value) }
            }

            Spacer(Modifier.height(12.dp))
            Hairline()
            Text(
                "Adjust before logging",
                fontSize = 12.sp,
                color = dim,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            EditRow("Name", name) { name = it }
            EditRow("Calories", calories, numeric = true) { calories = it }
            EditRow("Fat g", fat, numeric = true) { fat = it }
            EditRow("Protein g", protein, numeric = true) { protein = it }
            EditRow("Carbs g", carbs, numeric = true) { carbs = it }

            Spacer(Modifier.height(6.dp))
            Text(
                if (isSafeFood) "Remove from safe foods" else "Add to safe foods",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSafeFood) dim else secondary,
                modifier = Modifier.accessibleClick(if (isSafeFood) "Remove from safe foods" else "Add to safe foods") { onToggleSafe() }
            )

            food.barcode?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Barcode $it",
                    fontSize = 11.sp,
                    color = faint
                )
            }

          }

            Spacer(Modifier.height(12.dp))
            DialogActions(
                secondaryText = "Discard",
                onSecondary = onDismiss,
                primaryText = if (verdict.verdict == Verdict.OVER_LIMIT) "Log anyway" else "Log it",
                primaryColor = if (verdict.verdict == Verdict.OVER_LIMIT) error else primary,
                onPrimary = {
                    onConfirm(
                        food.copy(
                            name = name.trim().ifBlank { food.name },
                            calories = calories.toIntOrNull() ?: 0,
                            fatGrams = fat.toDoubleOrNull() ?: 0.0,
                            proteinGrams = protein.toDoubleOrNull() ?: 0.0,
                            carbGrams = carbs.toDoubleOrNull() ?: 0.0
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun EditRow(
    label: String,
    value: String,
    numeric: Boolean = false,
    onChange: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.width(88.dp).padding(top = 7.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = if (numeric)
                KeyboardOptions(keyboardType = KeyboardType.Number)
            else KeyboardOptions.Default,
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

private fun num(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)

